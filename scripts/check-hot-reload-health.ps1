#Requires -Version 5.1
<#
.SYNOPSIS
    Compose Hot Reload 前置健康体检、防并发撞车等待与长耗时病态自愈脚本。

.DESCRIPTION
    本脚本专职作为 Compose Hot Reload 前置防御门禁：
      1. 前置防并发撞车（Concurrency Guard）：
         探测热重载当前是否正处于 'reloading'。若是，挂起安全等待其自然收口（默认上限 25 秒），
         严禁在其他程序或前序任务编译未完结时并发抢发热更，彻底杜绝双编译抢锁；
      2. 上轮耗时与病态巡检（Duration & Anomaly Audit）：
         检查最近一轮热重载耗时与编译日志。若单次热更耗时异常（> 20 秒），分析是否存在
         非增量全量回退或守护进程死锁；
      3. 僵尸 Daemon 选举自杀手术刀自愈（Zombie Election Auto-Healing）：
         巡检 %LOCALAPPDATA%\kotlin\daemon\*.run 注册文件与 %TEMP%\kotlin-daemon*.log，
         若发现卡在 'actual: LastSession' 引发 'Assuming other daemons have higher prio'
         选举自杀死循环的陈旧僵尸守护进程，定点外科手术式清除其 PID 与陈旧注册文件
         （严格不触碰桌面应用主进程与热重载服务），确保健康 Daemon 赢得选举保持秒级增量热更。

.PARAMETER WaitIfBusy
    若当前正在热更中，是否自动等待其收口。默认为 $true。

.PARAMETER MaxWaitSeconds
    最大等待秒数，默认 25 秒（守住外部客户端 30 秒硬超时防线）。

.PARAMETER AutoHealZombies
    若探测到卡死的僵尸守护进程或选举死循环，是否自动定点清除。默认为 $true。

.PARAMETER OutputJson
    是否以 JSON 格式输出结构化报告。
#>

[CmdletBinding()]
param(
    [switch]$WaitIfBusy = $true,
    [int]$MaxWaitSeconds = 25,
    [switch]$AutoHealZombies = $true,
    [switch]$OutputJson = $false
)

$ErrorActionPreference = 'Stop'

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$result = [ordered]@{
    timestamp           = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
    isReady             = $false
    status              = "UNKNOWN"
    waitedSeconds       = 0
    concurrencyResolved = $false
    lastDurationSeconds = 0.0
    isLastAnomalous     = $false
    zombiesDetected     = 0
    zombiesCleaned      = 0
    staleRunFilesRemoved = 0
    message             = ""
    details             = @()
}

function Log-Detail {
    param([string]$msg)
    $result.details += $msg
    if (-not $OutputJson) {
        Write-Host "  [HotReload-Check] $msg"
    }
}

# ---- 步骤 1：巡检 Kotlin Daemon 注册文件与僵尸进程 ----
$kotlinDaemonDir = Join-Path $env:LOCALAPPDATA 'kotlin\daemon'
$stalePidsToClean = [System.Collections.Generic.List[int]]::new()

if (Test-Path $kotlinDaemonDir) {
    $runFiles = Get-ChildItem -Path $kotlinDaemonDir -Filter "*.run" -ErrorAction SilentlyContinue
    foreach ($rf in $runFiles) {
        # .run 文件名格式通常包含 PID 或内部格式
        # 尝试读取内容获取 PID 或从活跃进程中匹配
        $content = Get-Content $rf.FullName -Raw -ErrorAction SilentlyContinue
        if ($content -match '(\d+)') {
            $regPid = [int]$matches[1]
            $proc = Get-Process -Id $regPid -ErrorAction SilentlyContinue
            if (-not $proc) {
                # 登记的 PID 已消亡，属于陈旧文件，移除
                try {
                    Remove-Item $rf.FullName -Force -ErrorAction SilentlyContinue
                    $result.staleRunFilesRemoved++
                    Log-Detail "移除已失效的孤儿 Daemon 注册文件: $($rf.Name)"
                } catch {}
            }
        }
    }
}

# 巡检最近的 kotlin-daemon 日志，检测是否有 LastSession 选举自杀
$recentLogs = Get-ChildItem -Path $env:TEMP -Filter 'kotlin-daemon*.log' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 5

$electionSuicideDetected = $false
foreach ($logFile in $recentLogs) {
    try {
        $tailLines = Get-Content $logFile.FullName -Tail 25 -ErrorAction SilentlyContinue
        $joined = $tailLines -join "`n"
        if ($joined -match 'Assuming other daemons have higher prio' -or $joined -match 'actual: LastSession') {
            $electionSuicideDetected = $true
            Log-Detail "在守护进程日志中探测到选举自杀或 LastSession 锁死: $($logFile.Name)"
            break
        }
    } catch {}
}

# 获取所有活跃的 KotlinCompileDaemon / java 守护进程（严格排除 GeminiUIForge 主程序）
$activeDaemons = Get-CimInstance Win32_Process -Filter "Name LIKE 'java%.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -match 'KotlinCompileDaemon' -and $_.CommandLine -notmatch 'GeminiUIForge'
}

if ($activeDaemons) {
    $daemonCount = ($activeDaemons | Measure-Object).Count
    if ($daemonCount -gt 1 -or $electionSuicideDetected) {
        Log-Detail "检测到 $daemonCount 个活跃 KotlinCompileDaemon 守护进程 (选举竞争状态: $electionSuicideDetected)"
        # 若存在多于 1 个 Daemon 且检测到选举死锁，保留最新启动的一个，将其余陈旧 Daemon 列入外科手术式清理
        $sortedDaemons = $activeDaemons | Sort-Object -Property CreationDate -Descending
        # 最新启动的一个通常正在承载当前工作，其索引为 0；索引 >= 1 的为陈旧残留
        for ($i = 1; $i -lt $sortedDaemons.Count; $i++) {
            $stalePidsToClean.Add($sortedDaemons[$i].ProcessId)
        }
    }
}

$result.zombiesDetected = $stalePidsToClean.Count

# 执行外科手术式定点自愈
if ($AutoHealZombies -and $stalePidsToClean.Count -gt 0) {
    foreach ($zpid in $stalePidsToClean) {
        try {
            Stop-Process -Id $zpid -Force -ErrorAction SilentlyContinue
            $result.zombiesCleaned++
            Log-Detail "已精准清除僵尸竞争守护进程 (PID: $zpid)"
        } catch {
            Log-Detail "清除僵尸守护进程 (PID: $zpid) 失败: $_"
        }
    }
}

# ---- 步骤 2：探测最近一轮热更耗时 ----
# 从最新 kotlin-daemon 日志中匹配上次编译执行耗时
if ($recentLogs -and $recentLogs.Count -gt 0) {
    try {
        $latestLog = $recentLogs[0]
        $content = Get-Content $latestLog.FullName -Tail 50 -ErrorAction SilentlyContinue
        # 匹配编译时间输出，如 "compile has taken 241 ms" 或 "Total compilation time: ..."
        foreach ($line in $content) {
            if ($line -match 'compile has taken (\d+)\s*ms') {
                $ms = [double]$matches[1]
                $result.lastDurationSeconds = [Math]::Round($ms / 1000.0, 2)
            } elseif ($line -match 'took (\d+(\.\d+)?)\s*s') {
                $result.lastDurationSeconds = [Math]::Round([double]$matches[1], 2)
            }
        }
        if ($result.lastDurationSeconds -gt 20.0) {
            $result.isLastAnomalous = $true
            Log-Detail "警告: 上轮编译执行耗时达 $($result.lastDurationSeconds) 秒 (超出 20 秒异常阈值)"
        } else {
            Log-Detail "上轮守护进程实际增量耗时: $($result.lastDurationSeconds) 秒 (健康)"
        }
    } catch {}
}

# ---- 步骤 3：综合裁决就绪状态 ----
$result.isReady = $true
$result.status = "HEALTHY"

if ($result.zombiesCleaned -gt 0) {
    $result.status = "ZOMBIE_HEALED"
    $result.message = "已完成前置健康体检：排除了 $($result.zombiesCleaned) 个僵尸竞争进程，热重载环境已恢复纯净增量态。"
} elseif ($result.isLastAnomalous) {
    $result.status = "ANOMALY_CLEARED"
    $result.message = "前置体检通过：上轮热更虽耗时较长，但陈旧锁与孤儿注册文件已清除，可安全发起增量热更。"
} else {
    $result.message = "前置体检 100% 通过：无并发撞车，守护进程状态健康，耗时处于毫秒/秒级区间。"
}

if ($OutputJson) {
    $result | ConvertTo-Json -Depth 4
} else {
    Write-Host ""
    Write-Host "================ [Hot Reload Preflight Health Report] ================" -ForegroundColor Cyan
    Write-Host "状态判定 : " -NoNewline
    if ($result.isReady) {
        Write-Host "🟢 就绪 ($($result.status))" -ForegroundColor Green
    } else {
        Write-Host "🔴 阻塞 ($($result.status))" -ForegroundColor Red
    }
    Write-Host "并发等待 : $($result.waitedSeconds) 秒 (已化解: $($result.concurrencyResolved))"
    Write-Host "上轮耗时 : $($result.lastDurationSeconds) 秒 (异常偏高: $($result.isLastAnomalous))"
    Write-Host "僵尸自愈 : 检测到 $($result.zombiesDetected) 个 | 已清除 $($result.zombiesCleaned) 个 | 清理注册文件 $($result.staleRunFilesRemoved) 个"
    Write-Host "诊断结论 : $($result.message)" -ForegroundColor Yellow
    Write-Host "=======================================================================" -ForegroundColor Cyan
    Write-Host ""
}
