# 代码框视口吸边与快速滚动接力待优化缺陷档案 (TODO Optimization)

## 一、 功能概述与当前实现现状
在 UI Studio AI 对话工作区中，大模型生成的结构化界面排版结果（或长代码）通过 Markdown 代码块（`AppMarkdownCodeBlock.kt`）呈现。

### 当前已实现功能：
1. **纯净排版提取与美化**：通过平衡括号计数器算法从 Google Interactions SSE 事件流中精准剥离出纯净的 JSON 布局树，使用 Markdown ````json ... ```` 代码框包裹展示；
2. **工具栏全套交互**：支持右上角一键复制代码（带 1.5s 状态提示）、折叠/展开动态切换、语言 Tag 与代码行数统计；
3. **独立最大高度限制**：展开长代码时，采用 `Modifier.heightIn(max = currentViewportHeight * 0.70f)` 约束最大可视高度，避免无限溢出；
4. **内部双向滚动**：代码框支持内部水平滚动与垂直滚动，配有跨端自适应滚动条。

---

## 二、 遗留缺陷与待优化技术核心 (Core Pending Issues)

### 🚨 待修复缺陷 1：快速滑动（Fling）或连续拖拽时，依然存在跳过代码框吸顶/直接冲穿的问题
- **现象描述**：
  当用户手指或鼠标以较快速度猛烈滑动页面（产生高速 Fling 冲量）时，或者长距离快速拖拽时，外层列表的滑动冲量有时依然能够直接掠过代码框，导致页面直接从最顶部穿透到最底部（或反向穿透），使得中间超长代码框的“吸顶暂留并滚动代码内部”的行为没有 100% 稳定生效。
- **底层技术瓶颈剖析**：
  在 Compose Multiplatform (Desktop JVM) 的 `NestedScrollConnection` 机制下，`onPreFling` 与 `onPreScroll` 的物理事件分发依赖于指针命中目标（Pointer Target）。
  如果用户的滑动命中点位于代码框外侧（例如气泡外边距、空白区域），手势冲量直接作用于外层父级列表，外层列表在原生惯性驱动下飞速滚动，代码块自身的局部连接器无法感知到该手势，导致吸顶在快速甩动下被“旁路穿透”。
- **后续优化思路**：
  后续需要探索真正的桌面级手势代理架构：在外层视口层面将代码块作为一个显式的“手势磁吸锚点（Gesture Magnet/Snap Anchor）”，无论光标落在何处，只要快速滑动经过超长代码块的吸顶区间，便通过动态计算直接强行将冲量吸收注入代码框内部，彻底终结高速甩动穿透。

---

## 三、 相关代码与核心资产清单
- **代码框组件**：`shared/src/commonMain/kotlin/org/gemini/ui/forge/ui/component/markdown/AppMarkdownCodeBlock.kt`
- **代码框顶栏组件**：`shared/src/commonMain/kotlin/org/gemini/ui/forge/ui/component/markdown/AppMarkdownCodeHeader.kt`
- **滚动协调感知器**：`shared/src/commonMain/kotlin/org/gemini/ui/forge/ui/component/markdown/CodeBlockScrollCoordinator.kt`
- **外层聊天滚动容器**：`shared/src/commonMain/kotlin/org/gemini/ui/forge/ui/component/chat/CommonChatMessageList.kt`
- **排版结果投影服务**：`shared/src/commonMain/kotlin/org/gemini/ui/forge/service/ai/ChatPayloadViewProjector.kt`
- **模型气泡承载**：`shared/src/commonMain/kotlin/org/gemini/ui/forge/ui/component/chat/bubble/CommonModelBubble.kt`
