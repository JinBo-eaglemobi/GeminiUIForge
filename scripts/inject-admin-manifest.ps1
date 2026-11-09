<#
.SYNOPSIS
    将包含 requireAdministrator 的 Manifest 清单注入到生成的 GeminiUIForge.exe PE 资源中，
    使 Windows 资源管理器直接显示管理员小蓝黄盾牌图标 (UAC Shield)，并在启动时触发 UAC 提权。
#>
param(
    [string]$exePath = "$PSScriptRoot\..\desktopApp\build\compose\binaries\main\app\GeminiUIForge\GeminiUIForge.exe",
    [string]$manifestPath = "$PSScriptRoot\..\desktopApp\src\main\resources\app.manifest"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $exePath)) {
    Write-Warning "⚠️ 未找到目标可执行文件: $exePath"
    exit 0
}

if (-not (Test-Path -LiteralPath $manifestPath)) {
    Write-Error "❌ 未找到清单文件: $manifestPath"
    exit 1
}

$fullExePath = (Resolve-Path -LiteralPath $exePath).Path
$fullManifestPath = (Resolve-Path -LiteralPath $manifestPath).Path

Write-Host "🛡️ 正在为可执行文件注入管理员 Manifest: $fullExePath"

# 解除 jpackage 默认施加的 ReadOnly 只读文件锁
$fileItem = Get-Item -LiteralPath $fullExePath
$wasReadOnly = $fileItem.IsReadOnly
if ($wasReadOnly) {
    $fileItem.IsReadOnly = $false
}

$csharp = @'
using System;
using System.IO;
using System.Runtime.InteropServices;

public class ManifestInjector {
    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern IntPtr BeginUpdateResource(string pFileName, bool bDeleteExistingResources);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool UpdateResource(IntPtr hUpdate, IntPtr lpType, IntPtr lpName, ushort wLanguage, byte[] lpData, uint cbData);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool EndUpdateResource(IntPtr hUpdate, bool fDiscard);

    public static bool Inject(string exePath, string manifestPath) {
        if (!File.Exists(exePath) || !File.Exists(manifestPath)) return false;
        byte[] manifestBytes = File.ReadAllBytes(manifestPath);
        IntPtr hUpdate = BeginUpdateResource(exePath, false);
        if (hUpdate == IntPtr.Zero) return false;
        
        // RT_MANIFEST = 24, ID = 1
        IntPtr RT_MANIFEST = new IntPtr(24);
        IntPtr ID_1 = new IntPtr(1);

        // 覆盖全部常见语言环境 (1033 = en-US 即 jpackage 默认模板语言，0 = Neutral，2052 = zh-CN)
        ushort[] targetLanguages = new ushort[] { 1033, 0, 2052 };
        bool allOk = true;
        foreach (ushort lang in targetLanguages) {
            bool ok = UpdateResource(hUpdate, RT_MANIFEST, ID_1, lang, manifestBytes, (uint)manifestBytes.Length);
            if (!ok) allOk = false;
        }

        if (!allOk) {
            EndUpdateResource(hUpdate, true);
            return false;
        }
        return EndUpdateResource(hUpdate, false);
    }
}
'@

if (-not ([System.Management.Automation.PSTypeName]'ManifestInjector').Type) {
    Add-Type -TypeDefinition $csharp
}

$result = [ManifestInjector]::Inject($fullExePath, $fullManifestPath)
if (-not $result) {
    Write-Error "❌ 调用 Win32 UpdateResource 注入 Manifest 失败！Win32错误码: $([System.Runtime.InteropServices.Marshal]::GetLastWin32Error())"
    exit 1
}

# 物理校验注入结果
$bytes = [System.IO.File]::ReadAllBytes($fullExePath)
$ascii = [System.Text.Encoding]::ASCII.GetString($bytes)
$hasReqAdmin = $ascii.Contains("requireAdministrator")
$hasAsInvoker = $ascii.Contains("asInvoker")

if ($hasReqAdmin -and -not $hasAsInvoker) {
    Write-Host "✅ 管理员 Manifest 注入成功！"
    Write-Host "🛡️ 成功将 asInvoker 替换为 requireAdministrator，Windows 资源管理器已为其赋予系统管理员小蓝黄盾牌图标 (UAC Shield)"
    exit 0
} elseif ($hasReqAdmin) {
    Write-Host "✅ 管理员 Manifest 注入成功（已包含 requireAdministrator）"
    exit 0
} else {
    Write-Error "❌ 物理校验失败：未在可执行文件中检测到 requireAdministrator 字符串！"
    exit 1
}
