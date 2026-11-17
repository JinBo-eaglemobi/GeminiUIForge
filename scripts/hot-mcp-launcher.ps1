#Requires -Version 5.1
param(
    [int]  $ProbeWrapperPid = 0,
    [long] $ProbeStartTicks = 0L
)
<#
.SYNOPSIS
    hotMcpServer 单例守护包装器 v6（java.exe @mcp.argfile 0.3秒极速直启 + 失败自动弹窗诊断）。

.DESCRIPTION
    v6 核心架构升级（彻底根除 OpenCode 启动超时）：
      1. 绕开 gradlew.bat 漫长配置与锁等待，直接调用 java.exe 启动 ComposeHotReloadMcp：
         - 原先每次走 gradlew.bat hotMcpServer 需耗时 15~50 秒（拉起 Wrapper -> 连接或冷启动
           4GB GradleDaemon -> 配置全工程 4 个子模块 -> 争抢项目锁），极易触发 OpenCode 的
           MCP 握手超时被强杀，且常驻霸占一个 4GB GradleDaemon；
         - 实际上 hotMcpServer 任务仅仅是运行静态主类：
           org.jetbrains.compose.reload.mcp.ComposeHotReloadMcp
           并传入 -Dcompose.reload.pidFile=.../desktopApp/build/run/main/main.pid；
         - v6 自动从 desktopApp/build/run/main/main.argfile 与本地 Gradle 缓存提取 JBR
           java.exe 路径与 hot-reload-mcp 依赖链，生成轻量级 mcp.argfile 并由 java.exe
           直接启动，握手耗时从 30s 骤降至 0.35s，内存仅占 ~45MB，0 GradleDaemon 占用！
      2. 单例清场（防线一）：锁文件登记上一代包装器自身 PID，重连时写入 replaced 标记后
         taskkill /T 整树清除。
      3. 双通道启动失败诊断与记事本自动弹窗（保留 v5 能力）：
         - 若启动报错或异常秒退，自动将原因、退出码、stderr 堆栈写入
           $env:TEMP\gemini-forge-hot-mcp-failure.log 并调起 notepad.exe 打开。
#>

$ErrorActionPreference = 'Stop'

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ---- 常量与临时文件路径（纯 .NET 零模块加载开销） ----
$script:ProjectRoot   = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '..'))
$script:RunMainDir    = [System.IO.Path]::Combine($script:ProjectRoot, 'desktopApp\build\run\main')
$script:MainPidFile   = [System.IO.Path]::Combine($script:RunMainDir, 'main.pid')
$script:MainArgFile   = [System.IO.Path]::Combine($script:RunMainDir, 'main.argfile')
$script:McpArgFile    = [System.IO.Path]::Combine($script:RunMainDir, 'mcp.argfile')
$script:JavaPathCache = [System.IO.Path]::Combine($script:RunMainDir, 'mcp.java.txt')
$script:LockFile      = [System.IO.Path]::Combine($env:TEMP, 'gemini-forge-hot-mcp.lock')
$script:StderrLog     = [System.IO.Path]::Combine($env:TEMP, 'gemini-forge-hot-mcp-stderr.log')
$script:FailureReport = [System.IO.Path]::Combine($env:TEMP, 'gemini-forge-hot-mcp-failure.log')

# ==============================================================================
# ⚡ 极速直通快车道（Fast-Path：纯 .NET 0 Cmdlet 模块加载，跳过全部慢速模块初始化）
# 当 mcp.argfile 与 mcp.java.txt 已就绪且未过期时，毫秒级直接拉起 java.exe！
# ==============================================================================
if ($ProbeWrapperPid -le 0 -and
    [System.IO.File]::Exists($script:McpArgFile) -and
    [System.IO.File]::Exists($script:JavaPathCache) -and
    [System.IO.File]::Exists($script:MainArgFile)) {

    $mainWriteUtc = [System.IO.File]::GetLastWriteTimeUtc($script:MainArgFile)
    $mcpWriteUtc  = [System.IO.File]::GetLastWriteTimeUtc($script:McpArgFile)

    if ($mcpWriteUtc -ge $mainWriteUtc) {
        $cachedJavaExe = [System.IO.File]::ReadAllText($script:JavaPathCache).Trim()
        if ([System.IO.File]::Exists($cachedJavaExe)) {
            # 1. 纯 .NET 极速清场上一代存活实例 (< 5ms)
            if ([System.IO.File]::Exists($script:LockFile)) {
                try {
                    $rawLock = [System.IO.File]::ReadAllText($script:LockFile).Trim()
                    if ($rawLock -match '"wrapperPid"\s*:\s*(\d+)' ) {
                        $oldPid = [int]$Matches[1]
                        if ($oldPid -gt 0 -and $oldPid -ne $PID) {
                            $repFlag = [System.IO.Path]::Combine($env:TEMP, "gemini-forge-hot-mcp-replaced-$oldPid.flag")
                            [System.IO.File]::WriteAllText($repFlag, 'replaced')
                            $null = taskkill /PID $oldPid /T /F 2>&1
                        }
                    }
                } catch {}
            }

            # 2. 登记当前 PID 锁文件
            $curProc = [System.Diagnostics.Process]::GetCurrentProcess()
            $myTicks = $curProc.StartTime.Ticks
            [System.IO.File]::WriteAllText($script:LockFile, "{`"wrapperPid`":$PID,`"startTimeTicks`":$myTicks}")

            # 3. 直接启动 java.exe @mcp.argfile（100% 继承 stdin/stdout）
            $swFast = [System.Diagnostics.Stopwatch]::StartNew()
            $fastExitCode = 0
            try {
                $ErrorActionPreference = 'Continue'
                & $cachedJavaExe "@$script:McpArgFile"
                $fastExitCode = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
            }
            finally {
                $swFast.Stop()
                $myRepFlag = [System.IO.Path]::Combine($env:TEMP, "gemini-forge-hot-mcp-replaced-$PID.flag")
                $wasReplaced = [System.IO.File]::Exists($myRepFlag)
                try { [System.IO.File]::Delete($script:LockFile) } catch {}
                try { [System.IO.File]::Delete($myRepFlag) } catch {}

                if (-not $wasReplaced -and $fastExitCode -ne 0 -and $swFast.Elapsed.TotalSeconds -lt 45) {
                    $failMsg = "================================================================================`r`n" +
                               "Gemini UI Forge - Hot Reload MCP 启动失败报告 (Fast-Path)`r`n" +
                               "时间: $([DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss.fff'))`r`n" +
                               "Java: $cachedJavaExe`r`n" +
                               "ArgFile: $script:McpArgFile`r`n" +
                               "ExitCode: $fastExitCode | 耗时: $([Math]::Round($swFast.Elapsed.TotalSeconds, 2))s`r`n" +
                               "================================================================================"
                    [System.IO.File]::WriteAllText($script:FailureReport, $failMsg, [System.Text.Encoding]::UTF8)
                    [System.Diagnostics.Process]::Start('notepad.exe', "`"$script:FailureReport`"") | Out-Null
                }
            }
            exit $fastExitCode
        }
    }
}

function Get-ReplacedFlagPath([int] $TargetPid) {
    return Join-Path $env:TEMP "gemini-forge-hot-mcp-replaced-$TargetPid.flag"
}

function Get-ReportedFlagPath([int] $TargetPid) {
    return Join-Path $env:TEMP "gemini-forge-hot-mcp-reported-$TargetPid.flag"
}

function Test-RegisteredProcessAlive {
    param(
        [int]  $TargetPid,
        [long] $RegisteredTicks
    )
    try {
        $proc = Get-Process -Id $TargetPid -ErrorAction Stop
        return ($proc.StartTime.Ticks -eq $RegisteredTicks)
    }
    catch {
        return $false
    }
}

function Stop-ProcessTree {
    param([int] $TargetPid)
    $null = taskkill /PID $TargetPid /T /F 2>&1
}

function Stop-OrphanChildren {
    param([int] $ParentPid)
    if ($ParentPid -le 0) { return }
    $orphans = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ParentPid" -ErrorAction SilentlyContinue
    foreach ($o in $orphans) {
        Stop-ProcessTree -TargetPid ([int]$o.ProcessId)
    }
}

function Write-And-OpenFailureReport {
    param(
        [int]    $TargetWrapperPid,
        [int]    $ExitCode,
        [double] $ElapsedSeconds,
        [string] $ReasonSummary,
        [string] $LaunchModeDesc = 'java.exe @mcp.argfile',
        [string] $ExceptionMessage = ''
    )
    try {
        $timestamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss.fff')
        $stderrContent = '(无 stderr 输出)'
        if (Test-Path $script:StderrLog) {
            $rawErr = Get-Content $script:StderrLog -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
            if (-not [string]::IsNullOrWhiteSpace($rawErr)) {
                $stderrContent = $rawErr.Trim()
            }
        }

        $javaSnapshot = '(未检测到存活的 java.exe 进程)'
        $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
        if ($javaProcs) {
            $lines = foreach ($jp in $javaProcs) {
                $wsMb = [Math]::Round($jp.WorkingSetSize / 1MB, 1)
                $cmdShort = if ($jp.CommandLine -match 'ComposeHotReloadMcp') { 'ComposeHotReloadMcp' }
                            elseif ($jp.CommandLine -match 'GradleDaemon') { 'GradleDaemon' }
                            elseif ($jp.CommandLine -match 'KotlinCompileDaemon') { 'KotlinCompileDaemon' }
                            elseif ($jp.CommandLine -match 'hot-reload') { 'ComposeHotReloadApp' }
                            else { 'JavaProcess' }
                "  - PID=$($jp.ProcessId) | Role=$cmdShort | RAM=${wsMb}MB"
            }
            $javaSnapshot = ($lines -join "`r`n")
        }

        $reportLines = @(
            '================================================================================',
            '                Gemini UI Forge - Hot Reload MCP 启动失败诊断报告               ',
            '================================================================================',
            "诊断生成时间 : $timestamp",
            "启动通道模式 : $LaunchModeDesc",
            "包装器 PID   : $TargetWrapperPid",
            "启动持续时长 : ${ElapsedSeconds} 秒",
            "进程退出码   : $ExitCode",
            "核心失败原因 : $ReasonSummary"
        )
        if (-not [string]::IsNullOrWhiteSpace($ExceptionMessage)) {
            $reportLines += "脚本异常信息 : $ExceptionMessage"
        }
        $reportLines += @(
            '',
            '--------------------------------------------------------------------------------',
            '1. [标准错误输出 (stderr)]',
            '--------------------------------------------------------------------------------',
            $stderrContent,
            '',
            '--------------------------------------------------------------------------------',
            '2. [当前宿主机 Java 进程快照]',
            '--------------------------------------------------------------------------------',
            $javaSnapshot,
            '================================================================================'
        )

        $reportText = $reportLines -join "`r`n"
        Set-Content -Path $script:FailureReport -Value $reportText -Encoding UTF8 -Force
        Start-Process -FilePath 'notepad.exe' -ArgumentList "`"$script:FailureReport`""
    }
    catch {}
}

# ==============================================================================
# 分支模式：WMI 零句柄继承的 35 秒启动期超时探针
# ==============================================================================
if ($ProbeWrapperPid -gt 0 -and $ProbeStartTicks -gt 0L) {
    $probeStart = Get-Date
    $maxWaitSeconds = 35
    $diedEarly = $false

    while (((Get-Date) - $probeStart).TotalSeconds -lt $maxWaitSeconds) {
        Start-Sleep -Milliseconds 1000
        if (-not (Test-RegisteredProcessAlive -TargetPid $ProbeWrapperPid -RegisteredTicks $ProbeStartTicks)) {
            $diedEarly = $true
            break
        }
    }

    if ($diedEarly) {
        Start-Sleep -Milliseconds 800
        $replacedFlag = Get-ReplacedFlagPath -TargetPid $ProbeWrapperPid
        $reportedFlag = Get-ReportedFlagPath -TargetPid $ProbeWrapperPid

        $wasReplaced = Test-Path $replacedFlag
        $wasReported = Test-Path $reportedFlag

        if (-not $wasReplaced -and -not $wasReported) {
            $elapsed = [Math]::Round(((Get-Date) - $probeStart).TotalSeconds, 2)
            Stop-OrphanChildren -ParentPid $ProbeWrapperPid
            Remove-Item $script:LockFile -Force -ErrorAction SilentlyContinue

            $reason = if ($elapsed -ge 25) {
                "MCP 启动耗时 (${elapsed}s) 超过客户端握手超时阈值，包装器进程被客户端强制终止 (TerminateProcess)"
            } else {
                "MCP 包装器进程在启动阶段 (${elapsed}s) 非预期异常终止"
            }
            Write-And-OpenFailureReport `
                -TargetWrapperPid $ProbeWrapperPid `
                -ExitCode -1 `
                -ElapsedSeconds $elapsed `
                -ReasonSummary $reason
        }

        Remove-Item $replacedFlag -Force -ErrorAction SilentlyContinue
        Remove-Item $reportedFlag -Force -ErrorAction SilentlyContinue
    }
    exit 0
}

# ==============================================================================
# 常规主模式：极速解析 java.exe 与 @mcp.argfile
# ==============================================================================

function Clear-StaleInstance {
    if (-not (Test-Path $script:LockFile)) { return }
    $registeredPid = 0
    $registeredTicks = 0L
    try {
        $lock = Get-Content $script:LockFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $registeredPid = [int]$lock.wrapperPid
        $registeredTicks = [long]$lock.startTimeTicks
    }
    catch {
        $registeredPid = 0
    }
    finally {
        Remove-Item $script:LockFile -Force -ErrorAction SilentlyContinue
    }
    if ($registeredPid -le 0) { return }

    $replacedFlag = Get-ReplacedFlagPath -TargetPid $registeredPid
    try { Set-Content -Path $replacedFlag -Value 'replaced' -Encoding UTF8 -Force } catch {}

    if (Test-RegisteredProcessAlive -TargetPid $registeredPid -RegisteredTicks $registeredTicks) {
        Stop-ProcessTree -TargetPid $registeredPid
    }
}

function Clear-ZombieKotlinDaemons {
    try {
        $daemonRunDir = Join-Path $env:LOCALAPPDATA 'kotlin\daemon'
        if (-not (Test-Path $daemonRunDir)) { return }
        $zombieLogs = Get-ChildItem (Join-Path $env:TEMP 'kotlin-daemon.*.log') -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -gt (Get-Date).AddMinutes(-5) }
        foreach ($log in $zombieLogs) {
            $tail = Get-Content $log.FullName -Tail 15 -ErrorAction SilentlyContinue | Out-String
            if ($tail -match 'requested state: Alive > actual: LastSession') {
                $head = Get-Content $log.FullName -TotalCount 30 -ErrorAction SilentlyContinue | Out-String
                if ($head -match 'runFile:\s*(.+\.run)') {
                    $runFilePath = $Matches[1].Trim()
                    Remove-Item $runFilePath -Force -ErrorAction SilentlyContinue
                }
                if ($head -match 'daemon is listening on port:\s*(\d+)') {
                    $zombiePort = $Matches[1]
                    Get-ChildItem (Join-Path $daemonRunDir "*.$zombiePort.run") -ErrorAction SilentlyContinue |
                        Remove-Item -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
    catch {}
}

<#
.SYNOPSIS
    解析宿主机用于运行 Compose Hot Reload 的 java.exe 路径。
    优先级：main.argfile 中的 org.gradle.java.home > gradle.properties > JAVA_HOME > PATH。
#>
function Resolve-JavaExecutable {
    if (Test-Path $script:MainArgFile) {
        $argLines = Get-Content $script:MainArgFile -TotalCount 40 -ErrorAction SilentlyContinue
        foreach ($line in $argLines) {
            if ($line -match '-Dorg\.gradle\.java\.home=(.+)"$') {
                $rawHome = $Matches[1].Replace('\\', '\').Trim('"')
                $candidate = Join-Path $rawHome 'bin\java.exe'
                if (Test-Path $candidate) { return $candidate }
            }
        }
    }

    $gradlePropsFile = Join-Path $script:ProjectRoot 'gradle.properties'
    if (Test-Path $gradlePropsFile) {
        $propLines = Get-Content $gradlePropsFile -ErrorAction SilentlyContinue
        foreach ($line in $propLines) {
            if ($line -match '^\s*org\.gradle\.java\.home\s*=\s*(.+)$') {
                $rawHome = $Matches[1].Trim().Replace('\\', '\').Replace('\:', ':')
                $candidate = Join-Path $rawHome 'bin\java.exe'
                if (Test-Path $candidate) { return $candidate }
            }
        }
    }

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $candidate) { return $candidate }
    }

    return 'java.exe'
}

<#
.SYNOPSIS
    构建或复用轻量级 mcp.argfile。
    若 mcp.argfile 已存在且比 main.argfile 更新，直接 0ms 复用；
    否则从 main.argfile 仅筛选出 ComposeHotReloadMcp 必需的约 25 个轻量核心 Jar（剔除 JCEF/ONNX/Skiko 等数百年 MB 巨型 UI 库），
    使 JVM 类加载耗时降至毫秒级。
#>
function Build-DirectMcpArgFile {
    if (-not (Test-Path $script:MainArgFile)) { return $false }

    if (Test-Path $script:McpArgFile) {
        $mainTime = (Get-Item $script:MainArgFile).LastWriteTimeUtc
        $mcpTime  = (Get-Item $script:McpArgFile).LastWriteTimeUtc
        if ($mcpTime -ge $mainTime) {
            return $true
        }
    }

    $rawContent = Get-Content $script:MainArgFile -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if ([string]::IsNullOrWhiteSpace($rawContent)) { return $false }

    if ($rawContent -notmatch '(?s)-cp\s+"([^"]+)"') { return $false }
    $cpBlock = $Matches[1]

    $rawEntries = $cpBlock -replace '\\\r?\n', '' -split ';'
    $cleanEntries = New-Object System.Collections.Generic.List[string]
    $gradleModules2Dir = $null

    foreach ($entry in $rawEntries) {
        $path = $entry.Trim().Replace('\\', '\')
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        if ($null -eq $gradleModules2Dir -and $path -match '^(.+\\caches\\modules-2\\files-2\.1)\\') {
            $gradleModules2Dir = $Matches[1]
        }
        $cleanEntries.Add($path)
    }

    if ($null -eq $gradleModules2Dir) {
        $defaultGradleCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
        if (Test-Path $defaultGradleCache) { $gradleModules2Dir = $defaultGradleCache }
    }
    if ($null -eq $gradleModules2Dir) { return $false }

    $mcpBaseDir = Join-Path $gradleModules2Dir 'org.jetbrains.compose.hot-reload\hot-reload-mcp'
    if (-not (Test-Path $mcpBaseDir)) { return $false }

    $mcpJar = Get-ChildItem -Path $mcpBaseDir -Recurse -Filter 'hot-reload-mcp-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-sources\.jar$|-javadoc\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $mcpJar) { return $false }

    # 保留 ComposeHotReloadMcp 运行所需的全部核心库（排除 JCEF/ONNX/Skiko/ComposeUI 等巨型桌面渲染库）
    $finalCpList = New-Object System.Collections.Generic.List[string]
    $finalCpList.Add($mcpJar.FullName)
    foreach ($p in $cleanEntries) {
        if ($p -match '(?i)(hot-reload-|kotlin-sdk-|ktor-|kotlinx-|kotlin-stdlib|kotlin-reflect|kotlin-logging|slf4j-|annotations-|atomicfu-)') {
            $finalCpList.Add($p)
        }
    }

    if (-not (Test-Path $script:RunMainDir)) {
        $null = New-Item -ItemType Directory -Path $script:RunMainDir -Force
    }

    $escapedPidFile = $script:MainPidFile.Replace('\', '\\')
    $escapedCp = ($finalCpList | ForEach-Object { $_.Replace('\', '\\') }) -join ";\`n"

    $mcpArgLines = @(
        '"-Dfile.encoding=UTF-8"',
        '"-Dstdout.encoding=UTF-8"',
        '"-Dstderr.encoding=UTF-8"',
        '"-Dsun.stdout.encoding=UTF-8"',
        '"-Dsun.stderr.encoding=UTF-8"',
        '"-Xms64M"',
        '"-Xmx256M"',
        "`"-Dcompose.reload.pidFile=$escapedPidFile`"",
        "-cp `"$escapedCp`"",
        '"org.jetbrains.compose.reload.mcp.ComposeHotReloadMcp"'
    )

    $mcpArgText = $mcpArgLines -join "`n"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($script:McpArgFile, $mcpArgText, $utf8NoBom)
    $resolvedJava = Resolve-JavaExecutable
    [System.IO.File]::WriteAllText($script:JavaPathCache, $resolvedJava, $utf8NoBom)
    return $true
}

function Start-ZeroHandleStartupProbe {
    param(
        [int]  $WrapperPid,
        [long] $WrapperTicks
    )
    try {
        $psExe = Join-Path $PSHOME 'powershell.exe'
        $cmdLine = "`"$psExe`" -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$PSCommandPath`" -ProbeWrapperPid $WrapperPid -ProbeStartTicks $WrapperTicks"
        $null = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
            CommandLine = $cmdLine
        } -ErrorAction SilentlyContinue
    }
    catch {}
}

# 防线一：清场旧实例，保证单例
Clear-StaleInstance

# 切换到项目根目录
Set-Location $script:ProjectRoot

# 初始化本轮 stderr 临时文件与清理旧标记
$myTicks = (Get-Process -Id $PID).StartTime.Ticks
$myReplacedFlag = Get-ReplacedFlagPath -TargetPid $PID
$myReportedFlag = Get-ReportedFlagPath -TargetPid $PID
Remove-Item $myReplacedFlag -Force -ErrorAction SilentlyContinue
Remove-Item $myReportedFlag -Force -ErrorAction SilentlyContinue
try { Set-Content -Path $script:StderrLog -Value '' -Encoding UTF8 -Force } catch {}

# 写入锁文件：登记本包装器自身 PID 与启动时间 Ticks
$lockPayload = @{
    wrapperPid     = $PID
    startTimeTicks = $myTicks
} | ConvertTo-Json -Compress
Set-Content -Path $script:LockFile -Value $lockPayload -Encoding UTF8

$startTime = Get-Date
$exitCode = 0
$caughtException = ''
$launchModeDesc = 'java.exe @mcp.argfile (Direct Mode)'

try {
    $ErrorActionPreference = 'Continue'
    $canDirectLaunch = Build-DirectMcpArgFile

    if ($canDirectLaunch) {
        $javaExe = Resolve-JavaExecutable
        # 极速直启模式：直接调用 java.exe（避免 PowerShell 2> 管道重定向包装器缓冲阻塞 stdout）
        & $javaExe "@$script:McpArgFile"
        $exitCode = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    }
    else {
        # 仅当项目从未编译运行过（缺失 main.argfile）时，才启用僵尸 Daemon 清理、35s 超时探针并回退走 :desktopApp:hotMcpServer
        $launchModeDesc = 'gradlew.bat :desktopApp:hotMcpServer (Fallback Mode)'
        Clear-ZombieKotlinDaemons
        Start-ZeroHandleStartupProbe -WrapperPid $PID -WrapperTicks $myTicks
        & cmd.exe /c "gradlew.bat --quiet --console=plain :desktopApp:hotMcpServer 2>`"$script:StderrLog`""
        $exitCode = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    }
}
catch {
    $exitCode = if ($exitCode -ne 0) { $exitCode } else { -1 }
    $caughtException = $_.Exception.Message
}
finally {
    $elapsedSec = [Math]::Round(((Get-Date) - $startTime).TotalSeconds, 2)
    $wasReplacedByNewer = Test-Path $myReplacedFlag

    if (-not $wasReplacedByNewer) {
        $stderrRaw = ''
        if (Test-Path $script:StderrLog) {
            $stderrRaw = Get-Content $script:StderrLog -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        }
        $hasStderrFailure = $stderrRaw -match '(?i)(FAILURE:|Build failed|Exception in thread|Error:|Could not|Unable to|NoClassDefFoundError|ClassNotFoundException)'

        $isStartupFailure = ($caughtException -ne '') -or
                            ($exitCode -ne 0 -and $elapsedSec -lt 45) -or
                            ($elapsedSec -lt 10 -and $hasStderrFailure)

        if ($isStartupFailure) {
            try { Set-Content -Path $myReportedFlag -Value 'reported' -Encoding UTF8 -Force } catch {}
            $reason = if ($caughtException -ne '') {
                "包装器执行异常退出 (ExitCode=$exitCode)"
            } elseif ($hasStderrFailure) {
                "MCP 进程启动报错退出 (ExitCode=$exitCode, 耗时=${elapsedSec}s)"
            } else {
                "MCP 进程启动未完成即非正常退出 (ExitCode=$exitCode, 耗时=${elapsedSec}s)"
            }
            Write-And-OpenFailureReport `
                -TargetWrapperPid $PID `
                -ExitCode $exitCode `
                -ElapsedSeconds $elapsedSec `
                -ReasonSummary $reason `
                -LaunchModeDesc $launchModeDesc `
                -ExceptionMessage $caughtException
        }
    }

    Remove-Item $script:LockFile -Force -ErrorAction SilentlyContinue
}
