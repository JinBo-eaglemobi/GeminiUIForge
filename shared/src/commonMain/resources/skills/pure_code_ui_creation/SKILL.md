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

# UIFactory — 纯代码 FGUI 界面构建指南

> 适用范围：TSCore 框架下所有**纯代码实现的界面**（不依赖 FGUI 编辑器资源包）。
> 框架以 `tsCore` 命名空间对外暴露 `UIFactory`，本文所有示例直接以 `UIFactory.xxx` 调用。

---

## 一、技术选型铁律（必须遵守）

纯代码构建界面时，组件来源按以下三级优先级决策，**不得越级、不得跳过**：

| 优先级 | 来源 | 适用范围 |
|---|---|---|
| ① 首选 | **UIFactory 配置化工厂** | 凡是它能创建的，一律用它：text / icon / graph / panel / list / button 六类内置元素 + `registerCreator` 注册的自定义类型 |
| ② 兜底 | **fgui 原生组件**（`template/fairygui.min.js` 运行时，`fgui.*` 命名空间） | UIFactory 覆盖不到的组件：GProgressBar、GSlider、GComboBox、GTextInput、GTree、GLoader3D、GMovieClip、GGroup、Transition、Controller 等，直接 `new fgui.XXX()` 实例化后 `addChild` |
| ③ 红线 | ~~Laya 原生显示对象~~ | **绝对禁止**直接使用 `new Laya.Sprite()`、`new Laya.Text()`、`new Laya.Image()`、`new Laya.Button()` 等原生对象创建 UI |

---

## 二、UIFactory 能力总览

### 2.1 元素多态路由表（`createElement` 按 `type` 分发）

| type 配置值 | 产出 FGUI 对象 | 创建函数 | 说明 |
|---|---|---|---|
| `"text"` | `fgui.GBasicTextField` | `createText` | 普通文本 |
| `"text"` + `isUbb:true` | `fgui.GRichTextField` | `createRichTextField` | UBB 富文本（自动启用 ubb 语法解析） |
| `"icon"` | `fgui.GLoader` | `createIcon` | 图片装载器 |
| `"graph"` | `fgui.GGraph` | `createGraph` | 矢量几何图形 |
| `"panel"` | `EPanel` | `createPanel` | 子面板容器（可递归嵌套） |
| `"list"` | `PureList` | `createList` | 动态列表/网格渲染引擎 |
| `"button"` | `fgui.GButton` | `createButton` | 按钮（有皮肤 / 无皮肤纯代码） |

---

## 三、元素多态配置参考

### 3.1 icon — 图片装载器（IIconConfig）
```typescript
{
    type: "icon",
    name: "bgImage",
    url: "background.png", // 相对路径，会自动拼接 tsCore.UIFactory.resBasePath = "res/gameui/"
    x: 0,
    y: 0,
    width: 1280,
    height: 720,
    alpha: 1
}
```

### 3.2 text — 文本（ITextConfig）
```typescript
{
    type: "text",
    name: "titleLabel",
    text: "Title Text",
    fontSize: 32,
    color: "#ffffff",
    align: "center",
    valign: "middle",
    x: 100,
    y: 50,
    width: 300,
    height: 60
}
```

### 3.3 panel — 容器（IPanelConfig）
```typescript
{
    type: "panel",
    name: "rootContainer",
    x: 0,
    y: 0,
    width: 1280,
    height: 720,
    elements: [
        // 子元素列表
    ]
}
```

### 3.4 button — 交互按钮（IButtonConfig）
```typescript
{
    type: "button",
    name: "spinBtn",
    x: 500,
    y: 600,
    width: 120,
    height: 60,
    elements: [
        {
            type: "icon",
            url: "btn_spin.png",
            width: 120,
            height: 60
        }
    ]
}
```

---

## 四、主类挂载标准流程

```typescript
// 1. 设置切片资源加载根目录
tsCore.UIFactory.resBasePath = "res/gameui/";

// 2. 根据布局数据创建根面板
const rootPanel = tsCore.UIFactory.createPanel(layoutConfig, canvasWidth, canvasHeight);

// 3. 挂载到 FGUI 根显示列表
fgui.GRoot.inst.addChild(rootPanel);
```
