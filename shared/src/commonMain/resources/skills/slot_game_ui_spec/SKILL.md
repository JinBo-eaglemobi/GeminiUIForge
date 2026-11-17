---
id: slot_game_ui_spec
name: Slot 游戏工业级 UI 构建与全套项目排版规范
description: 专业的 Slot（老虎机/轮盘）游戏全生命周期 UI 架构与排版工程指南。深入阐述底层框架代码已实现的核心功能、UI 契约命名与代码自动识别绑定机制，涵盖 spinBtn（SPIN/STOP双态急停）、autoSpinBtn（自动旋转与局数弹窗）、activityBtn（Gift优惠券系统与抵扣下注）、StakeView（通用步进器与总注额联动）、SlotItem（变暗控制器与落地冲击回弹动效）、JackpotView（靠边悬浮奖池）及全套配套弹窗。
triggers:
  - 创建界面
  - 生成界面
  - 排版
  - 自动排版
  - 生成视图
  - 布局
  - spec
  - 规范
  - 组装
  - 老虎机
  - slot
  - slot游戏
  - 轮盘
  - 转轴
  - 创建slot
tools:
  - edit_view_layout
---

你是一位顶级游戏 UI/UX 架构与排版工程大师，专精于工业级 Slot（老虎机/轮盘）游戏的前端架构设计与图元树排版。
本规范的核心宗旨是：**为开发者与自动化智能体提供一套“可直接与成熟底层框架代码（PlayExecLogic、PlayConfigLogic、BaseSlotView、BaseSlotItem 等）无缝自动化绑定、复用既有功能”的工业级 UI 排版实战指南**。

在 UI Studio 中排版时，只要严格按照本规范的**节点层级、契约命名、控制器（Controllers）与动效（Transitions）**进行组织，底层框架代码即可**100% 自动识别 UI 节点、自动注入事件并驱动完整的动态交互效果**，无需额外编写胶水代码！

> 📌 **【尺寸与坐标绝对原则 (Dimension & Coordinate Principle)】**：
> 规范仅定义**“结构拓扑、组件契约命名、图元类型映射、代码自动绑定机制、状态机控制器、动效机制与相对对齐关系”**。**全套项目所有页面、组件与图元的具体宽高尺寸和坐标位置，100% 取决于当前具体游戏的美术设计稿与视口基准分辨率（无论是横屏还是竖屏），严禁在规范或代码中定死任何具体物理数值**！各图元应通过相对关联（如 `width-width`、`height-height`、居中 `center-center`、底部 `bottom-bottom` 等）实现弹性适配。

---

## 一、 全局基础组件类型枚举 (`UIBlockType` 唯一合法集合)

调用 `edit_view_layout` 时，每个图元的 `type` 字段**仅允许从以下 13 个基础组件类型中选取**：
- **`COMPONENT`**：通用组件、面板容器或引用独立子页面的组件实例（设置 `targetPageId` 时强制为 `COMPONENT`）
- **`LIST`**：动态滚动列表/网格容器（对应 `fgui.GList` / `PureList`，如横向多列转轴列表、单列垂直滚动列表、优惠券列表）
- **`IMAGE`**：静态图片、背景底图、装饰外框或符号静态纹理
- **`LOADER`**：动态资源装载器（对应 `fgui.GLoader`，如符号动态图标槽 `icon`、赢分图标 `winIcon`）
- **`SPINE`**：骨骼动画与特效节点（对应 `fgui.GLoader3D` / `tsCore.GSpineSkeleton`）
- **`BUTTON`**：交互按钮（包含核心 `spinBtn`、`autoSpinBtn`、`backBtn`、`SymbolBtn` 等一切可点击按钮）
- **`TEXT`**：文本标签与富文本数值框（对应 `fgui.GTextField` / `fgui.GRichTextField`，如 `msgInfo`、`balanceText`、`winText`、`totalBetText`）
- **`COMBO_BOX`**：下拉选择框（对应 `TopComboBox`，如顶部菜单 `menuBtn`）
- **`PROGRESS_BAR`**：进度条（对应 `tsCore.ProgressBar`，如奖池收集进度条）
- **`SLIDER`** / **`SCROLL_BAR`** / **`POPUP_MENU`** / **`INPUT`**

> 📌 **【组件类型约束】**：图元类型必须严格从上述 13 个标准枚举中选取，严禁使用非标准或自定义复合类型名；所有复合容器与子页面引用一律使用 `COMPONENT` 类型。

---

## 二、 整体架构需在 UI 上制作的 14 个完整布局全景清单

搭建一个生产级完备的 Slot 游戏，需要在 UI Studio 中制作以下 **14 个核心页面与独立组件**：

```
Slot 游戏全套 UI 制作清单
├── 1. 顶层主场景 (Scene): 游戏视口根页面（横屏或竖屏，尺寸依设计稿而定）
│    ├── bg (静态 IMAGE 直铺，或 COMPONENT -> 2. BgView 动态背景)
│    ├── topBar (COMPONENT -> 3. TopView 顶部状态栏)
│    ├── game (COMPONENT -> 4. SlotGameView 核心转轴主视口)
│    ├── bottom (COMPONENT -> 7. BottomView 底部控制操作栏)
│    ├── jackpot (COMPONENT -> 10. JackpotView 靠边悬浮奖池微标)
│    └── aniPanel (COMPONENT 全景透明动效覆盖层)
│
├── 2. 核心转轴系统 (Reel Matrix System)
│    ├── 4. SlotGameView (转轴主显示容器: 游戏名外框 + 播报文本 + 横向多列 list + 连线层)
│    ├── 5. SlotListItem (单列滚动列模板: 垂直 list 虚拟循环滚动)
│    ├── 6. SlotItem (符号原子模板: c1双态控制器 + icon + 变暗遮罩 + spine + t2落地回弹)
│    └── 14. LineGraphics (矢量连线画布容器)
│
├── 3. 控制台与步进调节器 (Controls & Stake System)
│    ├── 7. BottomView (控制台: Spin主按键 + AutoSpin + winText + totalBetText + 调节器)
│    ├── 8. StakeView (通用步进调节器组件: 承载 lineStake 与 betStake)
│    ├── 9. SymbolBtn (微调加减按钮组件: 减号 / 加号)
│    └── 11. ActivityButton (Gift 优惠券/活动按钮: 礼品图标 + c1双态可用/抵扣文本)
│
├── 4. 悬浮奖池玩法 (Jackpot System)
│    └── 10. JackpotView (界面空白处靠边悬浮: 宝箱晃动动效 t0 + ProgressBar + 张数角标)
│
└── 5. 配套弹窗与结算视窗 (Windows & Overlays Ecosystem)
     ├── 12. CouponWindow (Gift 优惠券选择弹窗: c1/c2控制器 + 优惠券列表 + 总额统计)
     ├── 13. CouponItem (单张优惠券列表项: Check模式单选框 + 面值 + 门槛 + 张数)
     ├── WinTip (即时行赢分提示浮层: 局部坐标冒泡淡出动效 t0)
     ├── WinWindow (大奖全屏结算弹窗: 全屏遮罩 + 大数字跳动 + 金币动效)
     ├── FreeSpinView (免费游戏过渡与结算视图: c1双态进入/结算 + 次数播报)
     ├── FreeSpinAdd (免费次数追加提示: "+X FREE SPINS" 悬浮横幅)
     └── HelpWindow (规则与赔率说明窗口: 关闭按键 + 赔率表 Paytable 与连线图 Lines)
```

---

## 三、 核心旋转组件深度规范与代码自动绑定 (`spinBtn` / `SpinBotton`)

### 1. 样式组成与内部图元节点
`spinBtn`（或组件 `SpinBotton`）是玩家操作权重最高的按键，其内部由以下结构组成：
- **底板与光晕 (`n0` / 背景)**：大圆形或胶囊形态的金属/宝石质感底座；
- **动态图标槽 (`icon`，`LOADER`)**：负责根据状态显示不同的图标纹理；
- **提示文本 (`title`，`TEXT`)**：在自动旋转或急停状态下展示辅助说明（如 `"UNTIL STOP"`、`"STOP"`）。

### 2. 控制器 (Controllers) 规范
- **状态控制器 `c1`**：**强制定义为双页控制器**
  - **`c1 = 0` (SPIN 待机态)**：显示旋转初始图标；
  - **`c1 = 1` (STOP 旋转中态)**：通过 `gearIcon` 自动切换为停止（STOP）图标；
- **交互控制器 `button`**：FGUI 标准按钮四态（`up`, `down`, `disabled`）。

### 3. 底层代码实现的功能与自动识别绑定
底层 `PlayExecLogic` 模块已封装完备的状态机逻辑，在初始化时自动通过 `getChild("spin")` 或 `getChild("spinBtn")` 捕获该节点：
- **自动绑定点击事件**：无需手动挂载回调，框架自动监听点击并分发 `onSpinClick()`；
- **启动旋转与自动变身 STOP**：点击触发旋转后，框架自动调用 `onChangeSpinText(1)`，将 `spinBtn.c1` 切换为 `1`，视觉上瞬间从 **SPIN** 变为 **STOP**；
- **提前急停机制 (Skill Stop)**：转轴正在高速巡航时，若玩家再次点击已变为 STOP 的按键，框架捕获该事件并立刻调用 `stopRollSlot()`，驱动滚轴按序紧急刹车落地；
- **复位与结算**：当所有滚轴急停落地并完成连线结算后，框架自动调用 `onChangeSpinText(0)`，将控制器切回 `0`，图标平滑恢复为 **SPIN** 待机态；
- **余额不足与禁用保护**：当玩家余额低于当前总注额时，框架自动将 `spinBtn` 置为 `disabled` 状态，防止非法请求。

---

## 四、 自动旋转系统与局数选择弹窗 (`autoSpinBtn` / `PopupMenu`)

### 1. 自动按键布局与样式 (`AutoSpin`，`BUTTON`)
- **节点结构**：外框底图 (`n0`) + 文本标签 (`title`，`TEXT`，默认文本 `"Auto Spin"`)；
- **角标/剩余次数提示**：支持在文本或微标上动态刷新剩余局数。

### 2. 自动旋转局数选择弹窗/菜单 (`PopupMenu` / `AutoSpinWindow`)
当点击 `autoSpinBtn` 时，底层自动弹出的局数配置视窗结构：
- **局数选择按钮组 (Button List)**：提供标准预设档位按键（如 `10`, `20`, `50`, `100`, `UNTIL STOP (无限次)`）；
- **高级停止条件选项 (可选)**：
  - 单次赢分超过特定额度时停止；
  - 余额增加/减少超过特定阈值时停止；
  - 触发免费游戏 (Free Spin) 时自动停止。

### 3. 底层代码实现的功能与自动识别绑定
底层 `PlayExecLogic.initPopupMenu()` 自动为 `autoSpinBtn` 建立全生命周期交互：
- **点击自动呼出选择菜单**：点击 `autoSpinBtn` 自动唤起预设局数弹窗；
- **局数选定与巡航启动**：玩家点击某个局数后，框架自动将数值写入 `gameData.autoBetCount` 并启动自动连转流水线；
- **实时倒计时与数字刷新**：每局结算时，框架自动调用 `onUpdateAutoCount()`，将剩余局数（如 `"Auto (18)"`）实时刷新到 `autoSpinBtn.title` 上；
- **一键终止自动巡航**：自动旋转进行中，`autoSpinBtn` 处于激活高亮态，玩家随时再次点击该按钮，框架立即执行 `onStopAutoSpin()`，本局结束后平滑退出自动模式。

---

## 五、 Gift 优惠券系统深度规范 (`activityBtn` / `CouponWindow` / `CouponItem`)

成熟商业 Slot 游戏均具备优惠券/免费投注券（Gift/Coupon）抵扣下注功能，底层框架 `PlayExecLogic.initCouponsMenu()` 已完整实现了数据驱动与抵扣下注闭环。

### 1. 控制台入口组件布局 (`ActivityButton`，挂载于 `BottomView`)
- **节点结构**：
  - 礼包/优惠券图标 (`n7`) + 折叠展开状态指示箭头 (`n6`)；
  - 标题标签 (`n1`，`TEXT`)：静态展示 `"Gift "`；
  - 动态状态文本 (`title` / `n10`，`TEXT`)；
- **控制器 `c1`（双态切换）**：
  - **`c1 = 0 (未选优惠券)`**：显示可用礼金余额文本（如 `"BAL 99000 Available"`）；
  - **`c1 = 1 (已选优惠券)`**：显示当前抵扣面值文本（如 `"[color=#ff0000]-10Ksh[/color]"`）。

### 2. 优惠券管理弹窗布局 (`CouponWindow`)
- **控制器体系**：
  - **`c1`**：`pages="0,没有优惠卷, 1,有优惠卷"`（无券时显示空状态文本，有券时显示滚动列表）；
  - **`c2`**：`pages="0,获取更多, 1,使用投注劵, 2,使用抵用券, 3,停止使用"`（联动控制底部大按钮的图标与行为）；
  - **`c3`**：`pages="0,竖版, 1,横版"`（自适应横竖屏关闭按钮坐标）；
- **内部节点结构**：
  - 背景底图外框 + 关闭按钮；
  - 优惠券滚动列表 (**`list`，`LIST`**，默认项模板为 `CouponItem`)；
  - 空状态提示文本（`"Currently no available gifts."`，受 `c1=0` 联动）；
  - 优惠券总额统计文本（`"Total: KES 5000"`）；
  - 底部操作大按钮（受 `c2` 联动切换）。

### 3. 优惠券列表项组件布局 (`CouponItem`)
- **组件模式**：`extention="Button"`, `Button mode="Check"`（单选勾选模式）；
- **节点结构**：
  - 背景底板 + 选中单选框 (`n4`) + 绿色对勾图标 (`n9`，选中时显示)；
  - 优惠券面值大文本 (`title`，如 `"500"`) + 货币单位文本（如 `"KSH"`）；
  - 拥有张数文本（`"Amount: {num=12}"`）；
  - 使用门槛限制说明文本（`"On Stakes of {num=10} or More"`，受 `c1=1` 联动显示）。

### 4. 底层代码实现的功能与自动识别绑定
- **自动检测与入口刷新**：框架登录后自动拉取玩家背包优惠券数据，自动计算可用礼金并刷新 `activityBtn` 上的显示；
- **点击自动弹出弹窗**：点击 `activityBtn` 自动实例化并弹出 `CouponWindow`；
- **单选互斥与门槛校验**：列表中每个 `CouponItem` 互斥单选，当玩家勾选某张券时，框架底层 `onUseActivity()` 自动比对当前注额是否满足门槛；若满足，自动将下注额标记为优惠券抵扣；
- **下注抵扣与回显联动**：选定后，`activityBtn` 自动切为 `c1=1`，以醒目红字显示抵扣金额；在发送下注协议时自动附加优惠券 ID，实现零现金扣除下注！

---

## 六、 通用步进调节器组件深度规范 (`StakeView` / `SymbolBtn`)

### 1. 组件内部结构与节点命名
线数调节器 (`lineStake`) 与单注调节器 (`betStake`) **强制统一复用标准的通用步进组件 `StakeView`**：
- **底板背景 (`n0`，`IMAGE`/`COMPONENT`)**：外框底座；
- **标题标签 (`n1`，`TEXT`)**：标识调节维度（由实例属性动态赋为 `"Line"` 或 `"BET"`）；
- **当前数值文本 (`title`，`TEXT`)**：居中大号加粗文本，显示当前挡位数值（如 `"20"`, `"1"`）；
- **减少按钮 (`leftBtn` / `n5`，`BUTTON`)** 与 **增加按钮 (`rightBtn` / `n4`，`BUTTON`)**：复用微调按键模板 `SymbolBtn`。

### 2. 底层代码实现的功能与自动识别绑定
底层 `PlayConfigLogic` 模块在初始化时自动通过 `getChild("betStake")` 与 `getChild("lineStake")` 捕获组件：
- **档位数组自动挂接**：框架根据服务端下发的注额梯阶配置 `betChange: ValueSwitcher` 与线数梯阶配置 `linesChange: ValueSwitcher`；
- **点击加减自动步进与边界置灰**：
  - 点击 `rightBtn`，框架自动切换到下一个更高的档位；若已达最高档，框架自动将 `rightBtn` 置灰禁用 (`enabled = false`)；
  - 点击 `leftBtn`，框架自动切换到上一个更低的档位；若已达最低档，框架自动将 `leftBtn` 置灰禁用 (`enabled = false`)；
- **实时联动计算总下注额**：每当线数或单注发生变动，框架自动触发 `onUpdateTotalBet()`：
  $$\text{总注额} = \text{单注 (bet)} \times \text{线数 (lines)}$$
  并自动将计算好的格式化总注额实时刷新到控制台主界面上的 **`totalBetText`**（`GRichTextField`）中，实现完全零代码的自动化数据联动！

---

## 七、 符号原子组件全状态机与落地冲击动效 (`SlotItem`)

### 1. 节点结构与分层拓扑
- **底层图标槽 (`icon`，`LOADER` 或 `IMAGE`)**：居中展示静态符号切片纹理；
- **中层变暗遮罩 (`n1`，矩形遮罩图元)**：黑色半透明矩形，受 `c1` 控制器驱动；
- **顶层骨骼特效 (`spine` / `skeleton`，`SPINE`)**：中奖高光动画层。

### 2. 控制器与动效契约
- **状态控制器 `c1`**：`pages="0,正常, 1,变暗"`；
- **动效 `t0`**：缩放呼吸微动效（Scale 1.0 $\rightarrow$ 0.9 $\rightarrow$ 1.0）；
- **动效 `t2`（落地回弹冲击动效）**：定义 `XY` 轴微小下沉并快速弹起归位（模拟真实物理重力撞击底座的回弹感）。

### 3. 底层代码实现的功能与自动识别绑定
`SlotItem` 继承自框架基类 `BaseSlotItem`，框架在滚轴转动与结算周期内自动调用其生命周期钩子：
- **急停落地物理冲击 (`playLandingAni`)**：当单列滚轴急停落地的那一瞬间，框架自动触发该列所有符号的 `playLandingAni()`，调用动效 **`t2`** 播放下沉微弹跳，呈现拳拳到肉的机械击打声画质感；
- **中奖高亮与骨骼自动播放 (`showWin`)**：当判定本局某条线中奖时，中奖线上的符号自动触发 `showWin()`：框架自动将静态 `icon` 隐藏，并唤醒 `spine` 节点循环播放炫酷的骨骼动画或粒子光效；
- **未中奖暗态压暗 (`dark`)**：同时，所有未中奖的陪衬符号自动触发 `dark()`：框架自动将其控制器切为 `c1 = 1`，呼出黑色半透明遮罩 `n1`，使未中奖区域整体压暗，强力聚焦中奖路线！
- **重置复原 (`resetUI`)**：新一局开始时，框架自动调用 `resetUI()`，切回 `c1 = 0`，隐藏骨骼、显示静态 `icon`，回归纯净待机态。

---

## 八、 核心转轴主视口与矢量中奖连线 (`SlotGameView` / `LineGraphics`)

### 1. 结构与节点规范
- **游戏标题外框 (`name` / `title`，`IMAGE`)**；
- **状态播报文本框 (`msgInfo` / `winLineTextField`，`TEXT`)**：居中横跨于转轴上方，富文本格式；
- **横向列列表 (`list`，`LIST`)**：`layout="row"`，容纳 $N$ 列单列实例（复用 `SlotListItem`）；
- **矢量连线画布 (`linePanel`，`COMPONENT`，引用 `LineGraphics`)**：挂载于转轴列表正上方。

### 2. 底层代码实现的功能与自动识别绑定
底层 `BaseSlotView` 自动接管连线逻辑：
- **矢量折线动态绘制**：中奖结算时，框架自动读取中奖线配置与当前中奖索引，在 `linePanel` 表面通过矢量绘图 API 以亮色（如青色 `#40ffff`、高光描边）瞬间绘制出贯穿各轴符号中心点的中奖折线；
- **轮播中奖线与文案播报**：若同时中多条线，框架自动启动轮播计时器，每隔 1~2 秒切换高亮一条中奖线，并自动在 `winLineTextField` 上实时更新文本：`"You won line 3 !"`，直至下一次旋转开始。

---

## 九、 悬浮奖池体系规范 (`JackpotView` / `BonusBtn`)

### 1. 视口排布与人机工程学
- **排布特征**：**必须定位于转轴外部的界面空白区域，靠边悬浮（如右上角或左上角）**；
- **防遮挡原则**：绝对不能遮挡核心滚轴主矩阵与顶栏状态文本，形成呼吸式边缘悬浮。

### 2. 页面结构规范与动效 (`JackpotView`)
内部包含奖池入口按钮 **`BonusBtn`**（`BUTTON`）：
- **宝箱/奖池图标 (`n0`)**：居中展示切片，挂载常驻微晃动过渡动效 **`t0`**（自动循环播放位移下沉与角度微倾斜动效，吸引玩家注意力）；
- **奖池收集进度条 (`n6`)**：采用 **`PROGRESS_BAR`**（`ProgressBar`），实时呈现当前奖池蓄水进度（`value / max`）；
- **收集角标 (`n8`, `n9`)**：显示玩家当前积攒的抽奖券张数或奖池等级倍数文本。

---

## 十、 配套弹窗与结算视窗生态规范 (Windows & Overlays)

### 1. 即时行赢分提示浮层 (`WinTip`)
- **功能**：小额或单线中奖时，在对应中奖连线位置浮现冒出的即时赢分气泡；
- **结构**：大号高亮描边数值文本框，挂载透明度淡出动效 `t0`（Alpha 0 $\rightarrow$ 1 $\rightarrow$ 漂移淡出）。

### 2. 大奖结算全屏弹窗 (`WinWindow`)
- **功能**：触发 Big Win / Mega Win / Super Win 等大额奖励时的全屏高潮结算界面；
- **结构**：全屏半透明暗色背景遮罩 + 大号位图字体动态跳动文本 + 金币爆发动效。

### 3. 免费游戏过渡与结算视图 (`FreeSpinView` / `FreeSpinAdd`)
- **双状态控制器 `c1`**：`pages="0,进入, 1,结算"`；
- `c1=0 (进入)`：展示获得的免费旋转总次数，提示用户点击开始；
- `c1=1 (结算)`：展示免费游戏累积赢得的总金币，提示用户结算领取；
- 确认大按钮（`Play Now` / `Claim`），挂载入场平活动效 `t0`。
- `FreeSpinAdd`：免费模式中再次命中 Scatter 时的 "+X FREE SPINS" 悬浮奖励横幅。

### 4. 规则与赔率说明窗口 (`HelpWindow`)
- 全景外框 + 关闭按键 + 滚动富文本面板（`HtmlPanel`），清晰呈现赔率表（Paytable）与连线走势图（Paylines）。

---

## 十一、 顶部状态栏 (`TopView` / `topBar`) 组件契约

主舞台顶层实例名为 **`topBar`**，独立页面名为 **`TopView`**：
| 契约节点 `id` | 强制 `type` | 说明与结构要求 |
| :--- | :---: | :--- |
| **`balanceText`** | **`TEXT`** | 玩家当前可用金币/余额文本（若有底板切图，底板为 `balanceBar`，`balanceText` 必须为 **`TEXT`**） |
| **`backBtn`** | **`BUTTON`** | 返回退出大厅按键 |
| **`menuBtn`** | **`COMBO_BOX`** 或 **`BUTTON`** | 下拉菜单按键（包含静音、帮助、历史记录等） |
| **`depositBtn`** | **`BUTTON`** | 充值/商城入口按键 |
| **`list`** | **`LIST`** | 顶部常驻活动、礼物或跑马灯入口列表 |

---

## 十二、 标准工程化自底向上装配与交付流水线

当在 UI Studio 中构建或修复一个完整的 Slot 游戏项目时，必须遵循**自底向上（Bottom-up）的装配顺序**，所有图元尺寸与坐标均依据当前设计稿进行赋值，保证所有依赖的子组件模板先行就绪，最终完成顶层主场景组装：

1. **第一步（原子组件与微控件）**：
   - 构建 **`SlotItem`**（原子符号模板：含 `icon` 图标、`c1` 变暗控制器、`t2` 落地冲击动效）；
   - 构建 **`SymbolBtn`**（微调加减按钮模板）；
   - 构建 **`CouponItem`**（单张优惠券 Check 模板）；
2. **第二步（复合微控件与单列模板）**：
   - 构建 **`StakeView`**（步进调节器模板：含底板、标签、数值、复用 `SymbolBtn` 加减键）；
   - 构建 **`ActivityButton`**（Gift 优惠券按钮模板：含礼品图标、`c1` 双态可用/抵扣文本）；
   - 构建 **`SlotListItem`**（单列滚动模板：含垂直滚动列表 `list` $\rightarrow$ 槽位实例引用 `targetPageId: "SlotItem"`）；
   - 构建 **`LineGraphics`**（矢量连线画布容器）；
   - 构建 **`JackpotView`**（悬浮奖池微标：含 `BonusBtn`、晃动动效 `t0`、收集进度条 `ProgressBar`）；
3. **第三步（核心转轴与控制台）**：
   - 构建 **`SlotGameView`** (或 `"GameView"`，转轴主视口：含横向列列表 `list` $\rightarrow$ 各列实例引用 `targetPageId: "SlotListItem"`，以及连线层 `linePanel`、状态富文本 `msgInfo`)；
   - 构建 **`BottomView`**（控制台：含 Spin、AutoSpin、注额与线数复用 `StakeView`、优惠券复用 `ActivityButton`、总注额与赢分文本）；
   - 构建 **`TopView`**（顶部状态栏）；
4. **第四步（配套弹窗生态）**：
   - 构建 **`CouponWindow`**（Gift 优惠券管理弹窗）；
   - 构建 **`WinTip`**（即时赢分提示浮层）；
   - 构建 **`WinWindow`**（大奖结算弹窗）；
   - 构建 **`FreeSpinView`**（免费游戏过渡与结算视图）；
   - 构建 **`HelpWindow`**（规则与赔率说明窗口）；
5. **第五步（主场景顶层组装）**：
   - 构建 **主场景根页面 (Scene)**：
     - 若为纯静态背景，挂载底层单张 `bg: IMAGE`（模式 A）；若为复合动态背景，挂载 `bg: COMPONENT -> BgView`（模式 B）；
     - 挂载 `topBar: COMPONENT -> TopView`（通栏顶置对齐）；
     - 挂载 `game: COMPONENT -> SlotGameView`（核心区域居中对齐）；
     - 挂载 `bottom: COMPONENT -> BottomView`（通栏底置对齐）；
     - 挂载 `jackpot: COMPONENT -> JackpotView`（定位在转轴外部空白处靠边悬浮）；
6. **第六步（原子持久化与健康诊断）**：
   - 立即调用 `save_project` 执行磁盘原子落盘；
   - 调用 `diagnose_view_data` 进行全工程数据完整性断言，确保 `isHealthy == true` 且 0 孤儿节点/0 悬空引用。
