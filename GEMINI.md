# Gemini UI Forge - Gemini CLI 指令规范

## 核心工作流 (Mandatory Workflow)
**所有修改类任务必须严格遵守以下流程：**

1.  **分析 (Analysis)**: 深入研究需求，识别受影响的文件、类和依赖关系。
2.  **方案列举 (Proposed Solutions)**: 针对需求提出至少一个（推荐两个）技术方案，说明各方案的优缺点。
3.  **用户确认 (User Confirmation)**: 使用 `ask_user` 工具展示方案并征求用户同意。**严禁在未经用户明确选择/同意前修改任何项目文件。**
4.  **执行 (Execution)**: 按照用户选定的方案进行手术刀式的代码修改。
5.  **验证 (Validation)**: 运行编译或相关测试确保修改正确。

## 技术栈规范 (Tech Stack & Conventions)
- **核心框架**: Kotlin Multiplatform (KMP), Compose Multiplatform。
- **目录结构**: 
  - `commonMain`: 跨平台核心逻辑与 UI。
  - `androidMain`, `iosMain`, `jvmMain`, `wasmJsMain`: 平台特定实现。
- **UI 开发**: 优先使用 Compose 原生声明式组件，保持与 Slots 游戏模板的解耦。
- **命名规范**: 遵循 Kotlin 官方编码规范；UI 组件使用大驼峰（PascalCase），逻辑变量使用小驼峰（camelCase）。
- **注释规范**: 所有生成的代码必须包含相关说明与注释，且注释内容必须统一使用**中文**。

## 任务执行原则
- **安全性**: 严禁泄露任何 API Keys（尤其是 Gemini/Nanobanana 相关配置）。
- **依赖库确认**: 若方案涉及添加新的第三方库或依赖，**必须**通过 `ask_user` 获得用户明确许可。若用户拒绝，必须调整方案，利用项目现有的库和功能实现需求。
- **依赖库检索**: 如果需要查看或分析第三方依赖库的文件或源码，优先前往 Maven 本地缓存目录（如 `~/.m2/repository`）或 Gradle 本地缓存目录（如 `~/.gradle/caches`）中查找，以减少不必要的网络搜索。
- **最小改动**: 仅修改与当前任务直接相关的代码，避免大面积重构，除非方案中已明确说明并获得许可。
- **透明度**: 在执行任何 Shell 命令前，必须完整打印命令内容。
