---
id: pure-code-ui-creation
name: 纯代码创建UI规则 (UIFactory)
triggers:
  - 纯代码UI
  - UIFactory
  - 生成UI代码
  - 导出UI
  - 创建UI
  - pure-code-ui
description: 基于 TSCore UIFactory 的纯代码 FGUI 游戏界面构建全套规范。包含元素多态路由（panel/icon/text/graph/button/list）、流式响应式排版、切片资源加载与 Main 挂载。
---

# UIFactory — 纯代码 FGUI 界面构建指南（入口指引）

> 适用范围：TSCore 框架下所有**纯代码实现的界面**（不依赖 FGUI 编辑器资源包）。
> 框架以 `tsCore` 命名空间对外暴露 `UIFactory`。

---

## 一、详细规范获取方式（使用本技能前必读）

本技能的**完整详细使用规范**（技术选型三级铁律、元素多态路由表、各元素声明式配置
Schema、响应式防拉伸排版、Spine 骨骼动画挂接、十大红线铁律等）**不在本文展开**，
统一由专用参考文档承载，按以下优先级获取：

### 1. 随包参考文档（首选）

- 文档位置（本技能目录内，相对路径）：[UIFactory_AI_UI_Creation_Guide.md](UIFactory_AI_UI_Creation_Guide.md)
- 对应应用存储相对路径：`skills/pure_code_ui_creation/UIFactory_AI_UI_Creation_Guide.md`
- 激活本技能后，**必须优先尝试通过 `read_project_file` 元工具读取上述路径**；
- 若本地路径未命中（外部存储目录尚未释放该文档），立即降级走下方官方下载地址获取。

### 2. 官方下载地址（兜底 / 获取最新版）

```
https://raw.githubusercontent.com/boge-mvp/TypeScriptLib/refs/heads/master/AIGuideKit/UIFactory_AI_UI_Creation_Guide.md
```

- 通过 `web_fetch` 元工具从该地址拉取完整规范内容；
- 该地址同时用于本地随包文档缺失、损坏或需要获取最新版本的场景。

---

## 二、执行纪律（红线摘要）

1. **先读文档，再写代码**：生成任何纯代码 UI 前，必须先完整阅读上述参考文档，
   严禁凭记忆臆造配置字段或跳过规范直接生成；
2. **三级技术选型铁律**（详见参考文档第一节）：
   - ① 首选 `UIFactory` 配置化工厂（text / icon / graph / panel / list / button 等内置元素）；
   - ② 兜底 `fgui` 原生组件（`new fgui.XXX()`，如 GProgressBar / GSlider / GComboBox 等）；
   - ③ **绝对禁止**使用 Laya 原生显示对象（`new Laya.Sprite()` / `Text()` / `Image()` 等）构建 UI。
