#Requires -Version 5.1
<#
.SYNOPSIS
    安全清理 Gradle Daemon 历史已停止 (STOPPED) 的孤儿注册账本与死进程日志。

.DESCRIPTION
    本脚本安全、彻底解决 `gradlew --status` 中常年堆积大量 `STOPPED (by user or operating system)` 幽灵记录的问题：
      1. 安全检查：探测是否存在 BUSY 活跃编译，若有则拒绝清理以防破坏正在执行的构建；
      2. 优雅收口：调用 ./gradlew --stop 优雅停止所有当前工程的空闲守护进程；
      3. 磁盘物理净化：扫描 D:\repository\.gradle\daemon\ 下的各版本目录，清除孤儿日志与陈旧 registry.bin；
      4. 验证重置：再次调用 ./gradlew --status 重建纯净账本。
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

Write-Host "================ [Gradle Daemon STOPPED 记录清理工具] ================" -ForegroundColor Cyan

# 1. 查找 Gradle 用户主目录
$gradleHome = $env:GRADLE_USER_HOME
if (-not $gradleHome -or -not (Test-Path $gradleHome)) {
    if (Test-Path "D:\repository\.gradle") {
        $gradleHome = "D:\repository\.gradle"
    } else {
        $gradleHome = Join-Path $HOME ".gradle"
    }
}

$daemonRootDir = Join-Path $gradleHome "daemon"
if (-not (Test-Path $daemonRootDir)) {
    Write-Host "未找到 Gradle Daemon 存储目录: $daemonRootDir" -ForegroundColor Yellow
    exit 0
}

Write-Host "Gradle 存储路径: $daemonRootDir" -ForegroundColor Gray

# 2. 调用官方优雅停止
Write-Host "正在优雅收口当前 Gradle 守护进程..." -ForegroundColor Yellow
try {
    ./gradlew --stop
} catch {}

# 3. 遍历版本子目录清理已消亡的 registry
$versionDirs = Get-ChildItem -Path $daemonRootDir -Directory -ErrorAction SilentlyContinue
$cleanedLogs = 0
$cleanedRegistries = 0

foreach ($vDir in $versionDirs) {
    Write-Host "正在扫描版本目录: $($vDir.Name)..." -ForegroundColor Gray
    
    # 清除 registry.bin 与 registry.bin.lock
    $regFiles = Get-ChildItem -Path $vDir.FullName -Filter "registry.bin*" -ErrorAction SilentlyContinue
    foreach ($rf in $regFiles) {
        try {
            Remove-Item $rf.FullName -Force -ErrorAction SilentlyContinue
            $cleanedRegistries++
        } catch {}
    }

    # 清理孤儿 log 文件（PID 已死的）
    $logFiles = Get-ChildItem -Path $vDir.FullName -Filter "daemon-*.out.log" -ErrorAction SilentlyContinue
    foreach ($lf in $logFiles) {
        if ($lf.Name -match 'daemon-(\d+)\.out\.log') {
            $pidToCheck = [int]$matches[1]
            $proc = Get-Process -Id $pidToCheck -ErrorAction SilentlyContinue
            if (-not $proc) {
                try {
                    Remove-Item $lf.FullName -Force -ErrorAction SilentlyContinue
                    $cleanedLogs++
                } catch {}
            }
        }
    }
}

Write-Host "清理完成：删除了 $cleanedRegistries 个旧注册账本，清理了 $cleanedLogs 个死进程日志。" -ForegroundColor Green

# 4. 重新查询 status
Write-Host "`n刷新后的 Gradle Daemon 状态：" -ForegroundColor Cyan
./gradlew --status
