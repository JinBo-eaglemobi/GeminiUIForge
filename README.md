# Gemini UI Forge

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)
![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg)
![Gemini AI](https://img.shields.io/badge/AI-Gemini_Pro_Vision-purple.svg)

**Gemini UI Forge** 是一款基于 Google Gemini AI 与 Compose Multiplatform 打造的次世代游戏 UI 辅助开发工具。它旨在通过 AI 的视觉理解与内容生成能力，帮助开发者快速完成从视觉设计到可交互 UI 资源的全流程转化。

## 🌟 核心特性

### 1. 🤖 AI 智能赋能
- **视觉引导重塑 (Visual Refine)**：通过框选参考图中的特定区域，AI 可自动识别组件结构、光影并生成对应的描述词进行重塑。
- **智能提示词优化**：内置专业级 Prompt 引擎，自动为您的 UI 组件补全风格、材质及光效描述。
- **自动化背景移除**：集成云端 (Vertex AI) 与本地 (Python rembg) 双重抠图方案，生图后自动物理切除透明边缘，确保资产完美贴合 UI 布局。

### 2. 📐 专业级布局编辑
- **层级隔离模式 (Isolated Editing)**：双击组模块进入隔离编辑状态，专注于局部微调，防止误触深层干扰图层。双击空白处即可退出。
- **跨平台 Skia 图形引擎**：所有的图像裁剪、缩放及透明度检测均基于 Skia 实现，确保在 Windows, macOS, Android, iOS 上的表现完全一致。
- **精确数值控制**：支持通过属性面板直接修改模块的 X、Y、W、H 及模块类型。

### 3. 🛠 稳健的系统架构
- **环境自动化修复**：内置 Python 运行环境检查系统，一键检测并自动安装 `rembg`、`pillow` 等关键库。
- **影子接力更新 (Shadow Update)**：基于 GitHub Releases 的全自动更新系统，支持下载后自动接力替换文件并重启应用。
- **多语言支持**：原生支持中英文动态切换，符合国际化开发规范。

## 🚀 快速开始

本项目支持 Android, iOS, Desktop (JVM), Web (Wasm/JS)。

### 桌面端 (Windows / macOS)
```shell
./gradlew :composeApp:run
```

### Android 移动端
```shell
./gradlew :composeApp:assembleDebug
```

### iOS 移动端
1. 在 Xcode 中打开 `iosApp/iosApp.xcworkspace`。
2. 运行项目。

### Web 浏览器端 (Wasm)
```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

## 📖 帮助手册
更详细的操作指南、快捷键说明及常见问题排查，请参阅 [**HELP.md**](./composeApp/src/commonMain/resources/HELP.md)。

## 🏗 技术栈
- **核心框架**: Kotlin Multiplatform (KMP), Compose Multiplatform
- **图形处理**: JetBrains Skia
- **网络层**: Ktor
- **序列化**: Kotlinx Serialization
- **AI 引擎**: Google Gemini API / Vertex AI
- **本地工具**: Python 3.9+ (rembg)

---
© 2026 Gemini UI Forge Team. 驱动创意，重塑开发。
