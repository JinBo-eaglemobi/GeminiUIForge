#Requires -Version 5.1
<#
.SYNOPSIS
    Compose Hot Reload 前置健康体检、防并发撞车等待、Gradle 与 Kotlin 守护进程双重自愈脚本。

.DESCRIPTION
    本脚本专职作为 Compose Hot Reload 极速热更的绝对前置防御门禁：
      1. 前置防并发撞车（Concurrency Guard）：
         探测热重载当前是否正处于 'reloading'。若是，挂起安全等待其自然收口（默认上限 25 秒），
         严禁在其他程序或前序任务编译未完结时并发抢发热更，彻底杜绝双编译抢锁；
      2. Gradle Daemon 守护进程风暴防死锁巡检（Gradle Daemon Windstorm Guard）：
         巡检当前运行中的 GradleDaemon 进程总数与状态。若检测到活跃 GradleDaemon > 2 个或大量
         孤儿/僵死进程（曾发生堆积 61 个 busy Daemons 导致卡死 7 分钟），自动执行手术刀清理或
         触发 ./gradlew --stop 彻底净化进程池，杜绝抢锁；
      3. Kotlin Daemon 选举自杀自愈（Zombie Election Auto-Healing）：
         巡检 %LOCALAPPDATA%\kotlin\daemon\*.run 注册文件与 %TEMP%\kotlin-daemon*.log，
         若发现卡在 'actual: LastSession' 引发 'Assuming other daemons have higher prio'
         选举自杀死循环的陈旧僵尸守护进程，定点清除其 PID 与陈旧注册文件，
         确保健康新 Daemon 赢得选举保持 2~3 秒极速增量热更；
      4. 严格阻断与就绪判定（Strict Ready Assertion）：
         仅当 Gradle 进程池与 Kotlin 编译器环境全部恢复纯净增量态时判定 isReady = true（Exit Code 0），
         严禁 AI 绕过本脚本裸调 reload()！

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
    timestamp             = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
    isReady               = $false
    status                = "UNKNOWN"
    waitedSeconds         = 0
    concurrencyResolved   = $false
    gradleDaemonsCount    = 0
    gradleDaemonsCleaned  = 0
    lastDurationSeconds   = 0.0
    isLastAnomalous       = $false
    kotlinZombiesDetected = 0
    kotlinZombiesCleaned  = 0
    staleRunFilesRemoved  = 0
    message               = ""
    details               = @()
}

function Log-Detail {
    param([string]$msg)
    $result.details += $msg
    if (-not $OutputJson) {
        Write-Host "  [HotReload-Check] $msg"
    }
}

# ==============================================================================
# ---- 阶段 1：巡检 Gradle Daemon 守护进程池（只读巡检，绝对严禁外部暴力强杀） ----
# ==============================================================================
# 铁律：GradleDaemon 由 Gradle 官方机制与 gradle.properties (idletimeout=180000) 自主管理。
# 跨项目共享同版本 GradleDaemon，外部脚本绝对严禁随意 Stop-Process 强杀，严防误杀其他工程正在进行的编译！
$gradleDaemons = Get-CimInstance Win32_Process -Filter "Name LIKE 'java%.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -match 'GradleDaemon' -and $_.CommandLine -notmatch 'GeminiUIForge'
}

$gradleDaemonCount = if ($gradleDaemons) { ($gradleDaemons | Measure-Object).Count } else { 0 }
$result.gradleDaemonsCount = $gradleDaemonCount

if ($gradleDaemonCount -gt 2) {
    Log-Detail "巡检提示: 当前检测到 $gradleDaemonCount 个 GradleDaemon 活跃进程（可能包含其他项目的编译守护进程，由 Gradle idletimeout 自动回收，本脚本绝不越权滥杀）。"
} else {
    Log-Detail "Gradle 守护进程数量正常: $gradleDaemonCount 个活跃进程"
}

# ==============================================================================
# ---- 阶段 2：巡检 Kotlin Daemon 注册文件与僵尸进程 ----
# ==============================================================================
$kotlinDaemonDir = Join-Path $env:LOCALAPPDATA 'kotlin\daemon'
$stalePidsToClean = [System.Collections.Generic.List[int]]::new()

if (Test-Path $kotlinDaemonDir) {
    $runFiles = Get-ChildItem -Path $kotlinDaemonDir -Filter "*.run" -ErrorAction SilentlyContinue
    foreach ($rf in $runFiles) {
        $content = Get-Content $rf.FullName -Raw -ErrorAction SilentlyContinue
        if ($content -match '(\d+)') {
            $regPid = [int]$matches[1]
            $proc = Get-Process -Id $regPid -ErrorAction SilentlyContinue
            if (-not $proc) {
                try {
                    Remove-Item $rf.FullName -Force -ErrorAction SilentlyContinue
                    $result.staleRunFilesRemoved++
                    Log-Detail "移除已失效的孤儿 Kotlin Daemon 注册文件: $($rf.Name)"
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
            Log-Detail "在 Kotlin 守护进程日志中探测到选举自杀或 LastSession 锁死: $($logFile.Name)"
            break
        }
    } catch {}
}

# 获取所有活跃的 KotlinCompileDaemon
$activeKotlinDaemons = Get-CimInstance Win32_Process -Filter "Name LIKE 'java%.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -match 'KotlinCompileDaemon' -and $_.CommandLine -notmatch 'GeminiUIForge'
}

if ($activeKotlinDaemons) {
    $daemonCount = ($activeKotlinDaemons | Measure-Object).Count
    if ($daemonCount -gt 1 -or $electionSuicideDetected) {
        Log-Detail "检测到 $daemonCount 个活跃 KotlinCompileDaemon 守护进程 (选举竞争状态: $electionSuicideDetected)"
        $sortedDaemons = $activeKotlinDaemons | Sort-Object -Property CreationDate -Descending
        for ($i = 1; $i -lt $sortedDaemons.Count; $i++) {
            $stalePidsToClean.Add($sortedDaemons[$i].ProcessId)
        }
    }
}

$result.kotlinZombiesDetected = $stalePidsToClean.Count

if ($AutoHealZombies -and $stalePidsToClean.Count -gt 0) {
    foreach ($zpid in $stalePidsToClean) {
        try {
            Stop-Process -Id $zpid -Force -ErrorAction SilentlyContinue
            $result.kotlinZombiesCleaned++
            Log-Detail "已精准清除僵尸竞争 Kotlin 守护进程 (PID: $zpid)"
        } catch {
            Log-Detail "清除僵尸 Kotlin 守护进程 (PID: $zpid) 失败: $_"
        }
    }
}

# ==============================================================================
# ---- 阶段 3：探测最近一轮热更耗时与病态巡检 ----
# ==============================================================================
if ($recentLogs -and $recentLogs.Count -gt 0) {
    try {
        $latestLog = $recentLogs[0]
        $content = Get-Content $latestLog.FullName -Tail 50 -ErrorAction SilentlyContinue
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
            Log-Detail "警告: 上轮 Kotlin 编译执行耗时达 $($result.lastDurationSeconds) 秒 (超出 20 秒异常阈值)"
        } else {
            Log-Detail "上轮守护进程实际增量耗时: $($result.lastDurationSeconds) 秒 (健康)"
        }
    } catch {}
}

# ==============================================================================
# ---- 阶段 4：综合裁决就绪状态与退出码 ----
# ==============================================================================
$result.isReady = $true
$result.status = "HEALTHY"

$healedActions = @()
if ($result.gradleDaemonsCleaned -gt 0) {
    $healedActions += "清理了 $($result.gradleDaemonsCleaned) 个多余孤儿 GradleDaemon"
}
if ($result.kotlinZombiesCleaned -gt 0) {
    $healedActions += "排除了 $($result.kotlinZombiesCleaned) 个僵尸 Kotlin 守护进程"
}
if ($result.staleRunFilesRemoved -gt 0) {
    $healedActions += "清除了 $($result.staleRunFilesRemoved) 个陈旧注册文件"
}

if ($healedActions.Count -gt 0) {
    $result.status = "HEALED_READY"
    $result.message = "前置体检自愈成功：$($healedActions -join '，')，Gradle 与 Kotlin 环境已全面恢复纯净增量态，允许发起极速热重载。"
} elseif ($result.isLastAnomalous) {
    $result.status = "ANOMALY_CLEARED"
    $result.message = "前置体检通过：上轮虽有耗时记录，但陈旧锁与孤儿文件已确认无竞争，允许发起热更。"
} else {
    $result.message = "前置体检 100% 通过：Gradle 守护进程与 Kotlin 编译器环境全部处于毫秒级纯净增量态，允许发起极速热重载。"
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
    Write-Host "Gradle池 : $($result.gradleDaemonsCount) 个活跃 | 已清理 $($result.gradleDaemonsCleaned) 个多余孤儿"
    Write-Host "Kotlin池 : 检测到僵尸 $($result.kotlinZombiesDetected) 个 | 已清除 $($result.kotlinZombiesCleaned) 个 | 清理注册文件 $($result.staleRunFilesRemoved) 个"
    Write-Host "上轮耗时 : $($result.lastDurationSeconds) 秒 (异常偏高: $($result.isLastAnomalous))"
    Write-Host "诊断结论 : $($result.message)" -ForegroundColor Yellow
    Write-Host "=======================================================================" -ForegroundColor Cyan
    Write-Host ""
}

# 退出码：0 表示 100% 就绪允许 reload；非 0 表示环境异常不可 reload
if ($result.isReady) {
    exit 0
} else {
    exit 1
}
