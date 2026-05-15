# Gemini UI Forge 帮助手册

欢迎使用 **Gemini UI Forge**！本工具利用 Google Gemini AI 与 Compose Multiplatform 打造，旨在通过 AI 能力大幅提升游戏 UI 资产与布局的设计效率。

---

## 1. 核心工作流 (Core Workflow)

### 🏠 首页工作台 (Home)
在首页，您可以总览并管理所有项目模板。
- **编辑布局**：进入 UI 结构定义模式，设计界面骨架。
- **生成资源**：进入 AI 图片产出模式，为设计骨架填补细节。

![首页概览](images/help/home_overview.png)

### 📐 布局编辑模式 (Layout Design)
为您提供直观的可视化界面编辑体验。
- **层级隔离**：**双击组模块**进入“隔离编辑模式”，仅能操作该组的直接子项，防止误触其他图层；**双击空白处**即可退出隔离。
- **直观交互**：直接拖拽和缩放图层。使用鼠标滚轮或顶部工具栏缩放画布。
- **精准控制**：在右侧属性面板精确输入数值（X, Y, W, H）以微调位置和大小。

![布局编辑界面](images/help/layout_editor.png)

### 🎨 核心资源生成 (Asset Generation)
将您的文本创意直接转化为游戏 UI 资源。
- **生图指令**：在侧边栏输入详细的提示词描述，点击“开始生成”。
- **智能适配**：生成的图片将自动利用 Python (rembg) 进行抠图，并裁剪透明边框以完美适配您定义的矩形区域。

![资源生成面板](images/help/asset_generation.png)

---

## 2. 进阶 AI 生成 (Advanced AI Features)

随着版本的迭代，Forge 引入了更多高效的批量与状态生成工具：

### 📦 批量资产生成 (Batch Generation)
通过 `BatchAssetGenDialog` 支持一次性为多个 UI 节点生成资源，无需手动逐一操作，极大提升基础资产铺设的速度。

![批量生成面板](images/help/batch_generation.png)

### 🔘 按钮状态智能生成 (Button State Gen)
普通/按下/禁用状态一键搞定！系统能够根据基础按钮图片，智能推演并生成配套的交互状态切图。

![按钮状态生成](images/help/button_state.png)

### ☁️ 云端资产库 (Cloud Assets)
无缝衔接云端模型产出的资产，通过云端资源对话框 (`CloudAssetDialog`) 浏览并快速引入高质量的通用组件素材。

---

## 3. 高级编辑工具 (Pro Editing Tools)

### 🪄 内置图像编辑器 (Image Editor)
无需切换到 PS！Forge 内置了强大的图像编辑功能 (`ImageEditorDialog`)：
- **画笔与擦除**：对 AI 生成的瑕疵进行快速修复。
- **透明背景处理**：支持多种抠图与边缘羽化参数调整。
- **区域裁剪与滤镜**：轻松调整色调与重点区域。

![图像编辑器](images/help/image_editor.png)

### 📝 文本样式编辑器 (Text Style Editor)
不止于图片！对于游戏中的动态文本节点，提供丰富的排版配置。
- 调整字体大小、行高、对齐方式。
- 配置阴影、描边以及渐变色填充。

![文本样式编辑](images/help/text_style.png)

### 🎯 可视化微调 (Visual Refine)
针对 AI 产出不理想的局部，使用区域圈选与局部重绘 (`VisualRefineDialog`) 功能，让 AI 只针对选定范围进行精准修改。

---

## 4. 环境与后台监控

### ⚙️ 环境配置与自动修复
如果您需要启用**本地背景移除（高性能抠图）**功能，请确保系统满足：
- **Python 3.9+** 安装并配置在环境变量中。
- **必要库**：`rembg`, `pillow`, `onnxruntime`。
*提示：在“设置 -> 环境检测”中，您可以一键扫描并让 Forge 自动为您安装修复缺失的依赖。*

### 📋 后台任务看板 (AI Task Progress)
生成大批图片时不想干等？
呼出后台任务面板 (`AITaskProgressDialog`)，随时查看、暂停或取消当前的 AI 生成队列。

![任务进度看板](images/help/task_progress.png)

### ⌨️ 常用快捷键 (Shortcuts)
| 快捷键 | 功能 |
| --- | --- |
| `Ctrl + Z` | 撤销操作 |
| `Ctrl + Shift + Z` / `Ctrl + Y` | 重做操作 |
| `Ctrl + S` | 手动保存当前模板 |
| `Delete` | 删除选中图层 |

---
*更多高级技巧与最新源码，请访问 Gemini UI Forge 的 GitHub 代码仓库。*
