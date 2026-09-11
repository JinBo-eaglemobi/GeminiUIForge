# Gemini UI Forge

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue.svg)
![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform_1.11.1-orange.svg)
![JDK](https://img.shields.io/badge/JDK-23-success.svg)
![Gemini AI](https://img.shields.io/badge/AI-Google_Gemini-purple.svg)
![Ktor](https://img.shields.io/badge/Ktor-3.5.2-blueviolet.svg)
![Coil](https://img.shields.io/badge/Coil-3.5.0-brightgreen.svg)

**Gemini UI Forge** 是一款基于 **Google Gemini AI** 与 **Compose Multiplatform** 打造的跨平台游戏 UI 辅助开发工具。它旨在通过 AI 的多模态视觉理解与内容生成能力，帮助游戏开发者快速完成从视觉概念设计到工业级可交互 UI 资产的全流程转化。

---

## 🏛 架构拓扑与多模块边界

本项目遵循高度解耦的 Kotlin Multiplatform (KMP) 标准多模块体系构建：

| 模块 | 职责与平台定位 | 核心技术栈 |
|---|---|---|
| **`:shared`** | 核心业务共享库（包含全部 UI 模块树、设计系统、状态机、网络通信与本地服务） | Compose Multiplatform, Ktor, Skia, kotlinx-serialization |
| **`:desktopApp`** | 桌面端独立宿主壳（**第一优先级平台**，专职 JVM 启动调优、原生窗口控制与安装包分发） | JVM, Jetpack Desktop, Packager (MSI/EXE/DMG/DEB) |
| **`:webApp`** | 独立 Web 浏览器宿主壳（支持跨平台快速预览与轻量交互调试） | Kotlin/JS (Browser), Canvas / WebGL |
| **`:androidApp`** | Android 移动端宿主壳（面向触控设备与平板端 UI 预览） | Android SDK 35+, AGP 9.0+ |

---

## 🌟 核心工业级特性矩阵

### 1. 🎨 AI 视觉交互工作室 (Visual Chat Studio)
- **多模态自适应生图**：支持从零概念绘制与带参考图的以图生图（Img2Img）；参考底图 100% 以官方标准 `inlineData` 内联打包注入请求体，生图精准受控；
- **所见即所发（WYSIWYS）**：发送与入库严格基于当前输入框文本，彻底剔除旧版双语捆绑传递的幻觉干扰；
- **流式提示词优化**：内置系统级专家约束指令，支持中英文同态流式打字机式润色与扩充，彻底杜绝“翻译：”或“建议：”等冗余杂质；
- **内存切片原生灯箱**：支持对未落盘的内存切片图像进行全分辨率大图放大预览与多轮对比。

### 2. 🧩 全模块多资源槽位智能分配中枢 (Resource Slots)
- **多态槽位自动感知**：面向所有图元类型统一抽象 `assetStates` 槽位体系；普通单图组件静默直通，多态组件智能拦截；
  - **旋转大按钮 (`SPIN_BUTTON`)**：自动感知“默认状态 (Spin)”与“停止状态 (Stop)”；
  - **常规按钮 (`BUTTON`)**：自动感知“默认状态 (Normal)”、“点击状态 (Pressed)”与“禁用状态 (Disabled)”；
- **支持多选批量赋权**：弹出专职的 `ResourceSlotSelectionDialog`，用户可同时多选多个目标状态槽位，一次性将生成的物理资产批量赋予所有勾选属性，并沉淀原子化撤销快照，彻底杜绝重复克隆文件。

### 3. 📡 会话原始网络通信档案 (Raw Traffic Archive)
- **旁路物理持久化**：采用序列号分流技术（`traffic/{seq}_REQ.json` 与 `traffic/{seq}_RESP.json`），100% 完整留存未脱敏、未截断的真实 HTTP 通信明细；
- **JSON 语法树格式化与自适应展开**：展开内容高度完全随文案行数自适应撑开，由外层单一滚动条流畅主导，彻底杜绝双重嵌套滚动死锁；
- **一键折叠与实时刷新**：支持顶部操作栏一键将所有报文在紧凑单行与完整展示之间全局切换，并支持随时手动拉取最新日志；
- **Base64 查看器与原图解码**：展示层安全折叠长串数据，提供独立的 `TrafficPayloadViewer` 弹窗，支持 Base64 一键复制与直接解码大图预览。

### 4. 🔌 内置 MCP (Model Context Protocol) 扩展中枢
- **本地服务一键拉起**：顶部常驻系统 Hub 状态指示，内置基于 Ktor 的轻量级本地 MCP Server；
- **多客户端非侵入式配置向导**：提供面向 **OpenCode** (`opencode.jsonc`)、**Claude Code** (`.claude.json`)、**Gemini CLI**、**Cursor** 及 **Claude Desktop** 的图形化配置教程与一键复制命令，严格执行安全红线（仅操作 `gemini-ui-forge` 单一键，绝不破坏用户原有配置与注释）。

### 5. 📐 可视化游戏 UI 布局与九宫格烘焙
- **层级隔离编辑模式 (Isolated Editing)**：双击图层组进入局部隔离编辑，屏蔽外部干扰，双击画布空白处即时退出；
- **二元空间分离与坐标守恒**：持久化数据模型严格采用父级局部坐标系，视图渲染与碰撞检测统一运行于全局绝对坐标系，平移变换与尺寸绝对解耦；
- **九宫格物理烘焙固化 (Nine-patch Bake)**：集成拉伸、平铺与九宫格算法，编辑后一键将规则合成并烘焙为物理 PNG 文件，实现与原始依赖的完全解耦。

### 6. ⚡ 独立本地离线抠图引擎 (LocalMattingService)
- **关注点分离**：本地 Python 图像去背算法从云端 `AIGenerationService` 中彻底解耦，由专职的 `LocalMattingService` 统一调度；
- **离线稳定执行**：基于系统内置的 `remove_bg.py`（rembg / pillow / u2net），具备环境自检、命令自适应（`python`/`python3`）及临时文件安全清理机制，不消耗任何外部网络 Token。

---

## 🚀 快速开始

构建环境要求：**JDK 23**（Toolchain 缺失时由 Gradle Foojay Resolver 自动解析下载）。

### 桌面端运行 (JVM - 核心平台)
```shell
# 启动桌面端应用程序
./gradlew :desktopApp:run

# 执行共享库快速编译校验
./gradlew :shared:compileKotlinJvm

# 编译桌面端最小验证集
./gradlew :desktopApp:compileKotlin
```

### Web 浏览器端 (JS)
```shell
# 启动 Web 端热重载开发调试服务
./gradlew :webApp:jsBrowserDevelopmentRun
```

### Android 移动端
```shell
# 构建 Android 调试安装包
./gradlew :androidApp:assembleDebug

# 构建 Android 正式发布包
./gradlew :androidApp:assembleRelease
```

### 桌面端绿色版与安装包打包
```shell
# 生成当前操作系统适用的免安装绿色版
./gradlew :desktopApp:createDistributable

# 打包为当前系统的原生安装包 (Windows MSI/EXE, macOS DMG, Linux DEB)
./gradlew :desktopApp:packageDistributionForCurrentOS
```

---

## 📖 详细文档与开发规范

- **用户使用手册**：关于工作台基础操作、图层层级、快捷键及常见问题，请参阅 [**HELP.md**](./shared/src/commonMain/resources/HELP.md)。
- **智能体与开发规范**：关于多平台边界、剪贴板规范、坐标系与设计系统约束，请参阅 [**AGENTS.md**](./AGENTS.md)。
- **版本发布指南**：关于自动化 CI/CD 标签触发与打包发布流程，请参阅 [**RELEASE_GUIDE.md**](./RELEASE_GUIDE.md)。

---

## 🏗 技术栈总览

- **跨平台核心**: Kotlin Multiplatform 2.4.10, Compose Multiplatform 1.11.1
- **图形与排版渲染**: JetBrains Skia 0.144.6, Coil 3.5.0
- **网络通信与流式处理**: Ktor 3.5.2, SSE (Server-Sent Events)
- **数据序列化**: kotlinx-serialization (JSON)
- **AI 视觉与多模态服务**: Google Gemini API (多模态视觉生成与结构化解析)
- **本地算法与脚本**: Python 3.9+ (rembg, pillow, onnxruntime)
- **模型上下文协议**: Model Context Protocol (MCP)

---

© 2026 Gemini UI Forge Team. 驱动创意，重塑次世代游戏 UI 开发流程。
