#Requires -Version 5.1
<#
.SYNOPSIS
    hotMcpServer 单例守护包装器 v4（stdio 直通 + 清场兜底，无看门狗进程）。

.DESCRIPTION
    v4 核心架构（v3 的 Start-Job 看门狗经两轮实测存在宿主进程失联风险，已移除；
    孤儿收尸职责移交"下一实例启动时的清场逻辑"，数据驱动更可靠）：
      1. stdio 直通：主线程用 PowerShell 原生 '&' 直接调用 cmd /c gradlew，
         子进程 100% 继承本包装器的 stdin/stdout 匿名管道，MCP JSON-RPC
         字节级直通 AI 客户端（实测热启动握手约 11-17 秒，30 秒红线内）。
      2. 单例清场（防线一）：锁文件登记上一代包装器自身 PID——
         a) PID 存活且 Ticks 匹配 → taskkill /T 整树清除；
         b) PID 已死（曾被强杀）→ 按 ParentProcessId 枚举孤儿树逐个补杀。
      3. 自愈机制（防线二，零进程开销）：
         a) AI 客户端正常退出/崩溃 → 管道写端关闭 → hotMcpServer JVM 读
            stdin 得到 EOF 自动退出（插件原生行为）→ gradle 退出 → 收尾；
         b) AI 客户端强杀本包装器 → cmd/java 孤儿短暂存活，由下一次
            启动实例的清场逻辑补杀（实测孤儿树可被完整清除）。
      4. 去掉 --no-daemon：复用常驻 Gradle Daemon 秒级热启动，避开 AI
         客户端约 30 秒的 MCP initialize 握手超时红线（冷启动约 50 秒，
         仅发生在 Daemon 不在场时，如机器重启后首连，重连一次即可）。
#>

$ErrorActionPreference = 'Stop'

# 尽力将控制台编码切换为 UTF-8（管道环境下可能无控制台句柄，失败则忽略；
# MCP JSON-RPC 内容几乎全 ASCII，OEM 代码页对其透明，此处仅防御性优化）
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ---- 常量与锁文件路径 ----
$script:LockFile = Join-Path $env:TEMP 'gemini-forge-hot-mcp.lock'

<#
.SYNOPSIS
    校验目标 PID 是否存活，且启动时间 Ticks 与登记值一致（防 Windows PID 复用误杀）。
    注意：登记值必须是 Int64 Ticks —— 若落盘日期字符串，PS5.1 的 ConvertFrom-Json
    会将其自动反序列化为 DateTime 对象导致格式比对永远失败。
#>
function Test-RegisteredProcessAlive {
    param(
        [int] $TargetPid,
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

<#
.SYNOPSIS
    级联终止目标进程及其整棵子进程树。
#>
function Stop-ProcessTree {
    param([int] $TargetPid)
    $null = taskkill /PID $TargetPid /T /F 2>&1
}

<#
.SYNOPSIS
    防线一：清场旧实例。
    锁登记的是上一代包装器自身 PID：
      a) PID 仍存活且 Ticks 匹配 → taskkill /T 级联清除整树（cmd→java 全灭）；
      b) PID 已死亡（AI 客户端曾强杀包装器，树内进程变孤儿）→
         按 ParentProcessId 枚举其残留子进程逐树补杀。
#>
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
        # 锁文件损坏视同失效，直接进入删除流程
        $registeredPid = 0
    }
    finally {
        Remove-Item $script:LockFile -Force -ErrorAction SilentlyContinue
    }
    if ($registeredPid -le 0) { return }
    if (Test-RegisteredProcessAlive -TargetPid $registeredPid -RegisteredTicks $registeredTicks) {
        # 旧包装器还活着：整树清除（含阻塞其中的 cmd/gradle/java）
        Stop-ProcessTree -TargetPid $registeredPid
    }
    else {
        # 旧包装器已被强杀：残留孤儿的父字段仍指向该 PID，逐树补杀
        # （PID 复用恰好命中且新进程恰好有子进程的概率极低，Ticks 已挡住活体误杀）
        $orphans = Get-CimInstance Win32_Process -Filter "ParentProcessId=$registeredPid" -ErrorAction SilentlyContinue
        foreach ($o in $orphans) {
            Stop-ProcessTree -TargetPid ([int]$o.ProcessId)
        }
    }
}

# ============================ 主流程 ============================

# 防线一：清场旧实例，保证单例
Clear-StaleInstance

# 定位项目根目录（脚本位于 scripts/ 下，gradlew.bat 在项目根）
Set-Location (Join-Path $PSScriptRoot '..')

# 写入锁文件：登记本包装器自身 PID 与启动时间 Ticks
# （cmd/gradle/java 均为本包装器子进程：存活场景 taskkill /T 一锅端；
#   强杀孤儿场景由 ParentProcessId 枚举补杀，见 Clear-StaleInstance）
$lockPayload = @{
    wrapperPid     = $PID
    startTimeTicks = (Get-Process -Id $PID).StartTime.Ticks
} | ConvertTo-Json -Compress
Set-Content -Path $script:LockFile -Value $lockPayload -Encoding UTF8

try {
    # 核心：PowerShell 原生 '&' 直接调用 —— 子进程继承本包装器的 stdin/stdout
    # 管道（与 Start-Process 的关键差异），MCP JSON-RPC 字节级直通 AI 客户端。
    # --quiet / --console=plain 防构建日志污染 JSON-RPC 协议帧；
    # 不加 --no-daemon，复用常驻 Gradle Daemon 实现秒级热启动。
    & cmd.exe /c gradlew.bat --quiet --console=plain hotMcpServer
}
finally {
    # gradle 退出（含 stdin EOF 链式自愈路径）后收尾：删除锁文件。
    # 若本包装器被 AI 客户端强杀（不走 finally），残留锁与孤儿由下一实例清场。
    Remove-Item $script:LockFile -Force -ErrorAction SilentlyContinue
}
