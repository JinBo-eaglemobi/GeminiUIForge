# Gemini UI Forge - 仓库智能体指令

基于 Google Gemini AI 与 Compose Multiplatform 的跨平台游戏 UI 辅助开发工具（AI 生图、视觉重塑、布局编辑、自动抠图）。核心框架：Kotlin Multiplatform (KMP) + Compose Multiplatform；网络层 Ktor；序列化 kotlinx-serialization。

## 模块与平台边界

- `shared` — 核心共享模块，几乎所有业务代码都在此（commonMain 按职责分包：`data / event / manager / model / service / state / ui / utils / viewmodel`）。
- `desktopApp` — 桌面端独立应用壳模块（application），专职 JVM 启动、JVM 内存调优参数与原生安装包分发打包，入口位于 `desktopApp/src/main/kotlin/org/gemini/ui/forge/main.kt`。
- `androidApp` — Android 壳模块（application），仅依赖 `:shared`。
- `webApp` — 独立 Web 浏览器宿主壳模块（application），仅依赖 `:shared`，包含 `index.html`、`styles.css` 及浏览器入口。
- `iosApp/` — Xcode 工程，独立于 Gradle 构建。
- **实际 Gradle 编译目标覆盖 android / jvm / js(browser)**；`iosMain` 源码集导出 `Shared.framework` 供 Xcode 消费。

### 平台优先级规范（桌面版优先）

- **桌面端 (JVM) 为第一优先级平台**：任何代码改动必须首先保证桌面端编译通过且功能正常运行，再考虑其他平台。
- 其他平台（Android / JS / iOS）的适配与修复**可延后**，不阻塞任务交付；当多平台实现存在冲突时，以桌面端实现为准，其余平台差异通过 `expect/actual` 机制后续补齐。
- 若改动涉及其他平台但未在本次验证，必须在交付说明中显式标注"待适配 / 未验证"。

## 常用命令

```shell
./gradlew :desktopApp:run                          # 运行桌面端 (JVM)
./gradlew :desktopApp:compileKotlin                # 桌面端最小编译校验
./gradlew :shared:compileKotlinJvm                 # 共享库 JVM 编译校验
./gradlew :webApp:jsBrowserDevelopmentRun          # Web 端 (JS) 运行与调试
./gradlew :androidApp:assembleRelease              # Android APK → androidApp/build/outputs/apk/release/
./gradlew :desktopApp:createDistributable          # 桌面绿色版 → desktopApp/build/compose/binaries/main/app/
./gradlew :desktopApp:packageDistributionForCurrentOS  # 桌面安装包 (MSI/EXE/DMG/DEB)
./gradlew :shared:jvmTest                          # 运行 JVM 测试
```

构建环境本地锁定 **JDK 25** (`D:\apps\sdks\java\jbr-25.0.2`，锁定于 `gradle.properties` 与 IDEA 配置)；toolchain 缺失时由 foojay resolver 自动下载。

## 质量与校验（红线规则）

- **IntelliJ IDEA 原生 Gradle 编译校验规范（MCP Run Configuration 优先铁律）**: **【红线规则】**
  每次非热重载开发模式下的代码改动完成后，如果影响项目代码编译，**必须**执行桌面端真实编译校验：
  1. **首选通道 (IntelliJ IDEA 原生 Gradle 任务调度)**：
     - 若当前挂载了 IDEA MCP 工具，**强制优先调用 `idea_execute_run_configuration(configurationName = "compileDesktop", projectPath = "<项目绝对根路径>", timeout = 180000, waitForExit = true)`**；
     - 运行配置位于 `.run/compileDesktop.run.xml`，以 `exitCode: 0` 作为桌面端真实编译通过的终审凭证；
  2. **快速语法初筛 (辅助通道)**：按需调用 `idea_get_file_problems` 或 `idea_build_project` 做毫秒级局部语法速查；
  3. **兜底通道 (命令行 Gradle)**：仅当无 IDEA MCP 工具时回退命令行：`./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin`；
  4. 纯文档、注释或非代码修改不触发编译校验。
- **校验阶段仅校验桌面版（JVM 优先铁律）**: **【红线规则】** 自动化构建与闭环验证阶段**一律且仅执行桌面端 (JVM) 编译校验**。其他平台（Web / Android / iOS）全部交由手动按需校验，严禁在日常迭代后自动触发耗时冗长的多端全量编译，最大化提升开发反馈速度。
- **物理校验优先 (Physical Check First)**: **【红线规则】** 外部脚本或工具修改文件后，切勿单凭编辑器的视觉表现来判断修改成败。必须始终通过原生 `git diff` 或 `read` / `Get-Content` 物理读取作为落盘的唯一铁证。
- **校验阶段人工交互流程优先规范 (Human-like Realistic Workflow for Validation)**: **【红线规则】**
  在日常校验、问题复现或实机验证阶段，**一律优先走正常的人工操作流程与完整交互链路**（如先选中图层、在界面属性面板正常点击开关或按钮、在图层树真实拖拽定位等）。**严禁为了图省事而频繁执行底层私有命令、修改内存对象或绕过 UI 流程直接打开内部功能**。只有通过完整的真实用户操作链路，才能切实暴露和验证 UI 线程事件分发、状态联动、弹窗时序与手势交互中的真实表现与潜在缺陷。
- **破坏性文件与数据清理强制二次确认 (Mandatory Confirmation for Destructive Actions)**: **【红线规则】** 严禁编写“未经确认直接物理删除本地磁盘文件或核心数据”的代码。任何涉及物理删除文件、清空资产历史库、删除项目模板或破坏性重置的操作，**必须强制弹出带有清晰后果警示说明的二次确认弹窗（如 `AppConfirmDialog`）**，且确认操作必须使用警示样式（`isDestructive = true`）。
- **定位底层具体实现 (Target Direct Implementations)**: 在 JetBrains Compose 等界面开发中，大片 UI 卡片常常被抽取成独立组件或同模块下的辅助文件。编辑前必须使用 `grep` 检索全文，**确保将具体修改落实到承载具体逻辑的组件定义体内，而不是主界面内的调用点**。

## 业务架构与模块模型规范

- **全屏游戏背景底图规范 (BACKGROUND Specification)**: **【业务铁律】**
  - 背景模块（`UIBlockType.BACKGROUND`）默认是游戏整个界面的背景层，**其尺寸必须恒等于全屏画布/屏幕尺寸**；
  - 在大模型识别、离线模板生成、手动添加模块（`addBlock`）或切换模块类型（`updateBlockType`）时，只要是顶层背景模块，其坐标与尺寸**必须直接初始化为全屏大小 `bounds = SerialRect(0f, 0f, width, height)` 与 `cropRect = SerialRect(0f, 0f, width, height)`**，严禁使用局部或默认小尺寸；
  - **初次大模型生成全屏兜底自愈铁律 (Auto Full-Canvas Guard)**：视觉多模态大模型在初次分析全景图提取背景时，极易根据画面局部视觉草坪或看台推导出带有局部边距的非全屏坐标。在模板生成落盘、模板加载及首次进入校验阶段，**必须在代码层强制执行兜底自愈**，无条件将顶层 BACKGROUND 的 `bounds` 与 `cropRect` 纠偏重置为全屏 `[0, 0, canvasWidth, canvasHeight]`，彻底根绝画布边缘留白与背景错位。
- **纯容器 / 组合占位层规范 (CONTAINER & isPureContainer)**:
  - `UIBlockType.CONTAINER` 或标记 `isPureContainer = true` 的模块属于纯占位或组合层，**绝对不参与任何 AI 图片资源生成**；
  - 批量生图（`BatchAssetGenDialog`）自动过滤排除所有纯容器模块；
  - 属性面板自适应隐藏已绑定图片资产卡片与生图参考图设置，仅保留几何坐标与“容器内部区域重塑”功能；
  - 画布渲染（`RenderBlock`）对纯容器不渲染任何背景图片与加载菊花，保持内部嵌套子组件通透可见。
- **参考图区域与模块显示坐标彻底二元解耦 (Decoupled Reference Area)**:
  - 模块在画布上的显示大小与坐标（`UIBlock.bounds`）和 AI 生图的局部参考区域（`UIBlock.cropRect` 与切片 `referenceImage`）**完全独立解耦**；
  - 打开设置参考区域窗口时，初始选区读取 `cropRect ?: toAbsoluteBounds()`（真实反映参考图覆盖范围）；
  - 调整保存参考区域时，**仅更新 `cropRect` 与切片 `referenceImage`，绝对不修改模块自身的显示 `bounds`**。
- **聊天生成图片本地存储、自愈恢复与安全定位规范**:
  - 聊天生成图片落盘路径：`~/.geminiuiforge/templates/{projectName}/assets/{blockId}/`；
  - 原始通信报文备份路径：`~/.geminiuiforge/sessions/{safeScope}/{sessionId}/traffic/`；
  - **自动再次缓存**：本地图片文件被删除后，用户在聊天界面点击「应用」时，系统自动回溯读取会话对应的 `RESP.json` 报文，提取 Base64 重新落盘生成本地缓存文件后应用；
  - **安全定位目录**：点击打开文件目录时，文件存在则高亮文件；文件不存在但父目录存在则打开该空白目录；若均不存在则优雅弹出气泡提示，严禁静默无响应。

- **画布命中选择的层级门禁规范 (Canvas Hit-Test Hierarchy Gate Specification)**: **【红线规则】**
  - **二元命中域铁律 (Global vs Group-Edit Scoping)**：
    画布手势命中检测 (`List<UIBlock>.findHitBlock`) 严格按编辑上下文二元分流：
    1. **全局模式 (`editingGroupId == null`)**：仅允许命中**顶层根模块**（按渲染列表倒序检索，父偏移恒为 0），严禁任何形式的深度递归穿透——点击父容器内部区域一律返回该顶层容器本身；
    2. **组编辑模式 (`editingGroupId` 激活)**：仅允许命中**被编辑组的直接子模块列表**（倒序、不递归）；子元素均未命中但点击落在组容器 bounds 内时返回组容器本身，否则返回 null。
  - **逐层编辑门禁铁律 (Layer-by-Layer Edit Gate)**：
    深层子模块严禁被画布直接点选，必须遵循标准编辑链路：双击容器进入组编辑模式 → 才能选中该层直接子模块 → 双击叶子自动切入其父组编辑。命中深度永远与 `editingGroupId` 的编辑链深度严格一致；
  - **单一命中管道铁律 (Single Hit Pipeline)**：
    画布的单击选中、双击进出组、拖拽命中等所有手势**必须复用同一个 `findHitBlock` 函数**，严禁在业务端散落手写第二套碰撞检测逻辑，杜绝命中规则不一致的分裂行为。

- **层级树祖先隐藏视觉联动规范 (Ancestor Hidden Dimming Specification)**:
  - **父链隐藏同步变暗铁律**：
    图层树中任一祖先模块被隐藏时，其整棵子树的行项（类型图标、名称、ID）必须**同步继承变暗状态**（名称/图标透明度 0.4、ID 透明度 0.3），与点亮层节点形成清晰的视觉层级区分；实现上通过递归渲染参数（如 `isAncestorHidden`）向下累积传递（`isAncestorHidden || !block.isVisible`），严禁子节点仅凭自身 `isVisible` 判断视觉状态；
  - **眼睛开关独立性铁律**：
    每个节点的隐藏/显示眼睛图标**永远只反映并控制模块自身的 `isVisible` 状态**，不随祖先隐藏状态联动变化——祖先隐藏影响的是视觉呈现（变暗），不篡改子模块自身的显示数据。

## 代码规范

- **一文件一 Composable 强制红线 (One Composable Per File Specification)**: **【红线规则】**
  严格遵循**“一文件一 Composable（One Composable Per File）”**的绝对铁律。
  1. **严禁同文件内多 Composable 声明**：除合法的内联 Slot 插槽 Lambda（如 `content: @Composable () -> Unit`）外，**绝对禁止在同一个物理 `.kt` 文件中声明 2 个或以上带有独立命名的 `@Composable fun`**（不论其可见性为 `public`、`internal` 还是 `private`）；
  2. **严禁就地声明私有辅助组件**：严禁在主界面或主弹窗文件中编写如 `private fun CredentialSection`、`private fun LogPanel`、`private fun StepRow` 等局部卡片或行项组件。所有的子卡片、行项（Item/Row）、子面板、弹窗覆层必须按功能职责拆分成独立的物理文件，统一归入对应功能模块的 `component/` 子目录组织；
  3. **单文件规模严格受控**：单个 UI 文件代码量原则上严格控制在 **100 ~ 300 行** 黄金可维护区间（上限不得超过 400 行）。做到“一文件一职责，看文件名即可秒懂其 UI 渲染结构”。
- **禁止硬编码数字与尺寸 (Design Tokens & Spacing System)**: **【红线规则】** 严禁在 UI 代码中随意写死硬编码数字（如 `8.dp`, `16.dp`, `440.dp` 等物理常数）。所有的边距、间隙、内边距、组件宽高以及弹窗尺寸等，必须统一使用项目中公用的设计系统配置（Design Tokens，如 `LocalAppSpacing.current` / `AppSpacing.kt` 中声明的语义化属性）来进行赋值。弹窗宽度（如 `dialogConfirmWidth`, `dialogConfigWidth`）与通用组件尺寸必须统一收拢到 `AppSpacing.kt` 或对应的公共维度配置中管理，实现一处修改、全局自动响应。
- **PC 端交互按钮 Tooltip 规范**: 所有 PC 桌面端的交互型按钮（包括但不限于 `Button`、`IconButton`、`TextButton`、`OutlinedButton` 以及各类可点击的操作图标/胶囊等所有可交互按钮）均必须使用项目内置的轻量单例修饰符 `Modifier.tip(...)`（来自 `AppTooltip.kt`）挂载悬浮提示信息，提示文案必须严格遵循下述 I18n 规范通过 `stringResource(...)` 注入，严禁硬编码文案。
- **多层树状图元层级坐标系通用规范 (Hierarchical Coordinate System Specification)**: **【红线规则】**
  - **二元空间分离铁律 (Local vs Absolute)**：
    1. **持久化局部相对性 (Local Space)**：在多层模块树中，图元数据模型（`UIBlock.bounds`）持久化存储的**永远是相对于其直接父容器的局部相对矩形**。严禁将全局绝对坐标直接写回未解耦的实体，防止破坏树状层级相对拓扑；
    2. **全景绝对全局性 (Global Space)**：所有跨越父级边界的行为（包括全景原图视口呈现、全屏选区框选、全图物理裁剪切片、画布碰撞检测与悬浮吸附），必须统一运行在**整页全局绝对坐标系**下。
  - **模块自推导与无参转换管道 (Zero-Argument Pipeline)**：
    1. **运行时父引用阻断序列化**：图元在运行时通过 `@Transient var parent: UIBlock?` 持有直接父级引用，完全阻断 JSON 序列化死循环；加载反序列化后通过 `bindParents()` 统一递归自动接线；
    2. **纯平移变换与宽高绝对解耦原则**：父容器的层级嵌套影响本质上纯粹是二维平面位移量 ($\Delta X, \Delta Y$)。图元的宽度 `width` 与高度 `height` 在局部与全局坐标系下**永远绝对恒定守恒，严禁将宽高卷入平移加减计算**；
    3. **零中间对象分配性能原则 (Zero Allocation)**：坐标推导与父级累计位移必须采用扁平 `while` 循环与原始浮点数寄存器累加，严禁在递归或迭代中频繁创建临时的 `SerialRect` 垃圾对象，杜绝 GC 抖动；
    4. **强制使用模块内建管道，严禁散落手写**：严禁在任何 ViewModel、UI 界面或算法中散落手写 `parentOffset.x` 的加减计算；
    5. **标准无参调用范式**：
       - 获取全局绝对矩形：一律访问模块属性 `block.absoluteBounds`（或 `block.toAbsoluteBounds()`）；
       - 逆向映射回局部矩形：一律调用对称方法 `block.toLocalBounds(absoluteRect)`；
  - **坐标转换可逆与守恒定律**：全局绝对矩形逆向转回局部矩形时，必须满足代数守恒：`block.toLocalBounds(block.absoluteBounds) == block.bounds`，确保无论树嵌套多深，坐标运算均无损可逆。
- **画布鼠标坐标交互、视口缩放与高 DPI 精度规范 (Pointer Interaction, Zoom & High-DPI Specification)**: **【红线规则】**
  - **绝对位置锚定优于相对增量累加 (Direct Position Pinning vs Delta Accumulation)**：
    在具有自由视口缩放 (Zoom)、画布平移 (Pan) 或高 DPI 密度的图形交互界面中，对于控制点拉伸（如选区 8 手柄、图元边缘拉伸等），**严禁采用每帧微小增量（如 `dragAmount / scale`）逐步累加的方式**。因为在非整数缩放因数、鼠标高速拖拽以及边缘截断时，增量累加必然发生浮点丢失与严重滞后，导致“鼠标拉出很远，手柄才挪动一点点”的严重失步缺陷；
  - **光标逻辑位置直接锚定铁律**：
    控制点拉伸坐标**必须统一直接锚定在当前光标反投影到画布的逻辑绝对坐标点（`screenToLogical(cursorPosition)`）**。光标拖到哪个逻辑像素，手柄坐标就直接赋值到该像素，从根本上保证光标与手柄 100% 绝对粘合对齐，误差永远为 0 像素！
  - **手势协程唯一保活原则**：
    所有复杂画布手势修饰器一律使用长效保活的 `.pointerInput(Unit)`，动态视口状态（Scale、Pan、Bounds）一律通过 `rememberUpdatedState` 传入，严禁将动态变化的数据作为 key 传入，杜绝手势中途被打断重启。
- **提示词编辑界面规范与组件复用 (Prompt UI & BilingualPromptEditor)**: **【红线规则】**
  - **严禁重复手搓 Prompt UI**：全项目凡是涉及或创建"AI 生图提示词 / 提交文案 (Prompt)"的输入与编辑界面（无论是在属性面板、弹窗还是页面中），**一律强制复用标准公共组件 `BilingualPromptEditor`**（位于 `org.gemini.ui.forge.ui.dialog.ai.component.BilingualPromptEditor` 或公共组件库），严禁在业务代码中再次手搓双语切换 Tab、输入框及优化按钮。
  - **组件核心规范与标准使用范式**：
    1. **中英文双语无缝切换**: 内置 `SingleChoiceSegmentedButtonRow` 单行防折行机制，支持分别查看/编辑 `userPromptZh` 与 `userPromptEn`，并提供跨语言智能参考占位（正在编辑中文时智能提示英文参考，反之亦然）；
    2. **AI 优化辅助入口**: 内置 34dp 胶囊式"AI 提示词优化 (AI Optimize)"按钮，自带加载动画与防重点击；
    3. **变动感知与显式确认同步**: 内置脏数据对比机制，发生修改或 AI 优化生成后动态高亮"确认修改"操作，用户显式确认后方回调 `onPromptConfirmed(zh, en)` 同步到 `ProjectState` / `UIBlock` 模块树中并触发持久化；
    4. **标准调用示例**:
       ```kotlin
       BilingualPromptEditor(
           promptZh = block.userPromptZh,
           promptEn = block.userPromptEn,
           onPromptConfirmed = { newZh, newEn ->
               val updatedBlock = block.copy(userPromptZh = newZh, userPromptEn = newEn)
               viewModel.assetManager.updateBlock(updatedBlock)
           },
           onOptimizeRequested = { sourceText, isZh ->
               viewModel.layoutEditor.optimizePrompt(block.id, apiKey, if (isZh) PromptLanguage.ZH else PromptLanguage.EN)
           },
           isOptimizing = isOptimizingPrompt,
           showExplicitConfirmButton = true, // 开启变动感知显式确认
           showChatContextOption = false      // 按需开启会话模式上下文选项
       )
       ```
- **UI 开发**: 优先使用 Compose 原生声明式组件，保持与 Slots 游戏模板的解耦。
- **命名规范**: 遵循 Kotlin 官方编码规范；UI 组件使用大驼峰（PascalCase），逻辑变量使用小驼峰（camelCase）。
- **导入与类名规范**: 代码中尽量使用 `import` 导入其它包的类，代码中应保持使用简单的类名，避免使用带有包路径的全限定类名。
- **注释规范**: 所有生成的代码必须包含相关说明与注释，且注释内容必须统一使用**中文**。
- **界面与多语言规范 (I18n)**:
  - 严禁在任何 UI 组件（`.kt` 界面文件）中硬编码中英文字符串。
  - 所有新增的界面文案必须通过 `shared/src/commonMain/composeResources/values/strings.xml` (默认/英文) 和 `values-zh/strings.xml` (中文) 进行注册和读取。
  - 对于带参数的动态文本，必须使用 Compose/Android 标准的百分号占位符格式（例如：`%1$d`, `%1$s` 或 `%d`, `%s`），并通过 `stringResource(Res.string.XXX, arg1)` 进行赋值传递，**严禁在代码中通过 `.replace()` 手动拼接字符串**。

## 任务执行原则

- **安全性**: 严禁泄露任何 API Keys（尤其是 Gemini/Nanobanana 相关配置）。
- **依赖库确认**: 若方案涉及添加新的第三方库或依赖，**必须**先获得用户明确许可。若用户拒绝，必须调整方案，利用项目现有的库和功能实现需求。
- **依赖库检索**: 如果需要查看或分析第三方依赖库的文件或源码，优先前往 Maven 本地缓存目录（如 `~/.m2/repository`）或 Gradle 本地缓存目录（如 `~/.gradle/caches`）中查找，以减少不必要的网络搜索。
- **最小改动**: 仅修改与当前任务直接相关的代码，避免大面积重构，除非方案中已明确说明并获得许可。
- **透明度**: 在执行任何 Shell 命令前，必须完整打印命令内容。

## 常用公共组件与核心工具封装规范 (Core Framework & Utility Conventions)

- **剪贴板工具与点击复制 (Clipboard Specification)**: **【红线规则】**
  - **严禁直接散落手写底层剪贴板与协程**：全项目凡是需要将文本、命令、URL 或日志写入系统剪贴板的场景，**一律强制复用项目内建的高阶扩展**（位于 `org.gemini.ui.forge.extend.ClipboardExtend.kt`），严禁在业务 UI 内重复声明 `LocalClipboard.current`、`rememberCoroutineScope()` 与 `scope.launch`。
  - **标准调用范式**：
    1. **组件内动作式**：使用 `val copy = rememberClipboardAction()`，在任意点击回调中调用 `copy(text, toastMessage)`，支持传入 `onResult = { isSuccess -> ... }` 获取执行成功与否的布尔值以执行自定义联动；
    2. **修饰符声明式**：针对整行、卡片等点击即复制的场景，直接链式调用 `Modifier.copyOnClick(text, toastMessage, onResult)`；
    3. **内置异常与体验保障**：该扩展自动处理 Windows 剪贴板独占瞬锁异常重试、自动打包跨端 `toClipEntry()` 并弹出成功 Toast 提示。
- **全局通知与气泡 (Toast Specification)**:
  - 界面全局轻量级提示统一使用单例 `Toast.show(message, type = ToastType.SUCCESS/INFO/ERROR, durationMillis = 3000L)`（来自 `org.gemini.ui.forge.utils.Toast`），严禁手搓独立浮层。
- **设计系统间距与弹窗规范 (Modal Dialog Width System)**: **【红线规则】**
  - **严禁随意手写硬编码弹窗宽度**：全项目凡是弹窗（`Dialog` / `AlertDialog` / `Surface` 浮层），一律严禁随意手写非标数值（如 `680.dp`, `760.dp`, `800.dp`, `880.dp` 等）。必须统一严格遵循 `LocalAppSpacing.current` 声明的标准五阶 Tokens 体系：
    1. `dialogAlertWidth` (440.dp) — 轻量警告、操作二次确认弹窗；
    2. `dialogConfigWidth` (520.dp) — 基础设置、单列配置表单；
    3. `dialogMediumWidth` (720.dp) — 中型详情、普通列表弹窗；
    4. `dialogLargeWidth` (960.dp) — 大型 PC 仪表盘、2 列 Bento、MCP 服务中心与调试控制台；
    5. `dialogHugeWidth` (1280.dp) — 全景宽屏通信档案、全景对比编辑器。
- **折叠组件一键全量折叠/展开规范 (One-Click Global Folding Specification)**: **【红线规则】**
  - 全项目凡是具有 2 个或以上独立可折叠卡片的界面（如 Bento 仪表盘、多模块属性面板、报文流水），**一律强制在顶部 Header 或工具栏配备“一键折叠全部 / 一键展开全部”的全局切换按钮**（使用 `Icons.Default.UnfoldLess` 与 `Icons.Default.UnfoldMore`，并挂载当前状态 Tooltip），彻底消除用户逐卡片点击的操作疲劳。
- **PC 悬浮提示 Tooltip 视口智能翻转与防遮挡规范 (Tooltip Viewport Specification)**: **【红线规则】**
  - 所有 PC 桌面端 Tooltip 统一复用 `Modifier.tip(...)` 与全局宿主 `GlobalTooltipHost`；
  - 宿主内置窗口视口边界碰撞检测：当目标位于屏幕下边缘（如底部状态栏）时，浮层**强制自动向上翻转显示**，严禁强制向下偏移遮挡交互按钮本体；当靠近右边缘时自动向左内缩，确保 100% 完整可见。
- **Compose 运行态原生语义节点动态易失性与感知规范 (Dynamic Semantic Node Volatility Specification)**: **【红线规则】**
  - **严禁硬编码动态数字 Node ID**：Compose Multiplatform 在界面重组（Recomposition）、导航跳转或列表展开后，数字 ID 是动态重新分配的临时运行时句柄，绝对禁止在脚本或调用中硬编码任何历史 node ID；
  - **动态语义检索优先**：执行任何模拟人工点击（`click_ui_node`）或文本输入（`set_ui_input_text`）前，**必须先调用 `get_semantic_tree(query = ...)` 动态查询当前生效的节点 ID 并确认动作能力后方可分发**，杜绝因 ID 飘移导致误触或调用失败。

## 代码生成与版本号

- `shared/build.gradle.kts` 中的 `generateProjectConfig` 任务会在构建时生成 `ProjectConfig.kt`（位于 `shared/build/generated/`），提供 `ProjectConfig.VERSION`。**勿手动编辑该生成文件**。
- 版本号推导链（取首个命中）：`-PversionName=x.y.z` 参数 > 环境变量 `GITHUB_REF_NAME`（CI tag）> `git describe --tags` > 兜底 `1.0.0`。本地不带参数构建时版本号为 1.0.0。

## 发布与 CI

- 发布完全由 `.github/workflows/release.yml` 承担，通过 **git tag 触发**（也支持 workflow_dispatch 手动触发），产物上传至 GitHub Releases。
- 按需构建后缀（控制 CI 只构建对应平台）：
  | 标签格式 | 触发行为 |
  |---|---|
  | `v1.0.0` | 全平台构建（Win exe/msi/jar/zip、Mac dmg/zip、Android apk） |
  | `v1.0.0-win` | 仅 Windows |
  | `v1.0.0-mac` | 仅 macOS |
  | `v1.0.0-android` | 仅 Android |
- **Android 签名未接线**：`gradle.properties` 中的 `KEY_STORE_*` 属性与根目录 `app.jks` 未接入任何构建脚本，CI 产出的 APK 为未签名版本（仅测试用）。不要在文档或代码中复制这些签名配置值。
- 依赖管理使用 Version Catalog（`gradle/libs.versions.toml`）；仓库源已配置 Aliyun 镜像 + mavenLocal，新增依赖必须走 Catalog 并显式声明版本。

## 其他注意

- 抠图功能依赖本地 Python 环境（rembg/pillow），脚本位于 `shared/src/commonMain/resources/scripts/remove_bg.py`，应用内置环境自检与自动安装。
- 桌面端运行时会读取 `~/.geminiuiforge/app.vmoptions` 覆盖 JVM 参数（调试内存问题时注意）。
- 分支为单 `master`；提交信息遵循 Conventional Commits 且描述用中文，如 `feat(workspace): 重构旋转按钮组件支持双状态独立配置`。
- 详细文档：`README.md`（功能概览，注意其中 wasm 命令已过时）、`RELEASE_GUIDE.md`（发布流程）、`shared/src/commonMain/resources/HELP.md`（用户手册）。
