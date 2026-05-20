# Gemini UI Forge - Gemini CLI 指令规范

## 质量与校验 (Quality & Validation)
- **定制化验证指令**: 遵循全局“任务闭环”的验证环节。对于本项目，每次代码逻辑或文件结构的改动完成后，**必须**立即执行以下编译命令进行实证校验：
  - `./gradlew :composeApp:compileKotlinJvm`
- **编译优先 (Compiling First)**: **【红线规则】** 验证是任务完成的唯一标准。严禁在未确认编译通过的情况下交付任务。若编译或自动化校验过程中出现报错，必须主动修复直至完全通过，不得带病进入下一阶段。

## 技术栈规范 (Tech Stack & Conventions)
- **核心框架**: Kotlin Multiplatform (KMP), Compose Multiplatform。
- **文件规范**: 严格遵循“一文件一类（One Class Per File）”的原则。禁止将多个类（Class/Interface/Enum 等）声明在同一个物理文件中，除非是私有的匿名内部类、紧密相关的极小数据类或密封类扩展。同时，每个被 `@Composable` 标记的方法也应尽量保持独立文件；若属于同一功能块的多个组件实现，应通过文件夹进行组织，禁止在同一个物理文件中定义多个主 `@Composable` 函数。
- **UI 开发**: 优先使用 Compose 原生声明式组件，保持与 Slots 游戏模板的解耦。
- **命名规范**: 遵循 Kotlin 官方编码规范；UI 组件使用大驼峰（PascalCase），逻辑变量使用小驼峰（camelCase）。
- **注释规范**: 所有生成的代码必须包含相关说明与注释，且注释内容必须统一使用**中文**。
- **界面与多语言规范 (I18n)**: 
  - 严禁在任何 UI 组件（`.kt` 界面文件）中硬编码中英文字符串。
  - 所有新增的界面文案必须通过 `composeResources/values/strings.xml` (默认/英文) 和 `values-zh/strings.xml` (中文) 进行注册 and 读取。
  - 对于带参数的动态文本，必须使用 Compose 原生的花括号占位符格式（例如：`{0}`, `{1}`），并通过 `stringResource(Res.string.XXX, arg1)` 进行赋值传递，**严禁使用 `%s`, `%d` 或在代码中通过 `.replace()` 手动拼接字符串**。

## 任务执行原则
- **安全性**: 严禁泄露任何 API Keys（尤其是 Gemini/Nanobanana 相关配置）。
- **依赖库确认**: 若方案涉及添加新的第三方库或依赖，**必须**通过 `ask_user` 获得用户明确许可。若用户拒绝，必须调整方案，利用项目现有的库和功能实现需求。
- **依赖库检索**: 如果需要查看或分析第三方依赖库的文件或源码，优先前往 Maven 本地缓存目录（如 `~/.m2/repository`）或 Gradle 本地缓存目录（如 `~/.gradle/caches`）中查找，以减少不必要的网络搜索。
- **最小改动**: 仅修改与当前任务直接相关的代码，避免大面积重构，除非方案中已明确说明并获得许可。
- **透明度**: 在执行任何 Shell 命令前，必须完整打印命令内容。
