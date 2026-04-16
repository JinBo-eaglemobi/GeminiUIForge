# Gemini UI Forge - 发布与部署指南

本文档详细说明了本项目如何进行本地构建、打包以及如何通过 GitHub Actions 实现自动化发布。

---

## 1. 本地构建与运行

在开发环境下，您可以使用以下命令快速验证程序：

### 1.1 桌面端 (JVM)
*   **直接运行**: `./gradlew :composeApp:run`
*   **打包免安装版 (绿色版文件夹)**: `./gradlew :composeApp:createDistributable`
    *   产物路径: `composeApp/build/compose/binaries/main/app/`
*   **打包本地安装程序 (MSI/EXE)**: `./gradlew :composeApp:packageDistributed`
    *   产物路径: `composeApp/build/compose/binaries/main/msi/` 或 `exe/`

### 1.2 安卓端 (Android)
*   **生成 Release APK**: `./gradlew :composeApp:assembleRelease`
    *   产物路径: `composeApp/build/outputs/apk/release/`

---

## 2. GitHub Actions 自动化发布 (推荐)

本项目已配置完善的 CI/CD 流水线，位于 `.github/workflows/release.yml`。

### 2.1 触发机制
流水线通过 **Git Tag (标签)** 触发。版本号会自动从标签名中提取（自动去掉前缀 `v` 和后缀）。

### 2.2 按需构建 (精准控制)
您可以根据修改内容，推送带有特定后缀的标签来节省服务器资源：

| 标签格式 (示例) | 触发行为 | 生成产物 |
| :--- | :--- | :--- |
| `v1.0.0` | **全平台构建** | Win (.exe, .msi, .jar, .zip), Mac (.dmg, .zip), Android (.apk) |
| `v1.0.0-win` | **仅构建 Windows** | .exe, .msi, .jar, GeminiUIForge-Windows-Portable.zip |
| `v1.0.0-mac` | **仅构建 macOS** | .dmg, GeminiUIForge-macOS-Portable.zip |
| `v1.0.0-android` | **仅构建 Android** | .apk |

### 2.3 操作步骤
1.  确认代码已提交并推送至远程仓库。
2.  在本地打标签: `git tag v1.0.1-win` (以 Windows 为例)。
3.  推送标签: `git push origin v1.0.1-win`。
4.  前往 GitHub 仓库的 **Actions** 页面观察进度。
5.  构建完成后，在 **Releases** 页面下载产物。

---

## 3. 版本号管理

项目版本号已实现**自动化同步**。
*   **原理**: `composeApp/build.gradle.kts` 会动态读取 GitHub 的 Tag 名。
*   **同步范围**: 
    *   Windows 安装包的属性信息。
    *   Android 的 `versionName`。
*   **本地默认值**: 若在本地打包，版本号默认为 `1.0.0`。

---

## 4. 注意事项

1.  **JDK 版本**: 本项目构建环境统一使用 **JDK 23**。
2.  **Android 签名**: 目前 GitHub 自动生成的 APK 为未签名版本，仅供测试。若需发布商店，需配置 Secret 密钥。
3.  **Windows 绿色版**: 发布的 `.zip` 包已内置精简版 JRE，用户无需安装 Java 即可运行。
4.  **失败排查**: 若构建失败，请在 GitHub Actions 的 Log 窗口查看 `FAILED` 标识上方的错误日志。

---
*更新日期：2026年4月16日*
