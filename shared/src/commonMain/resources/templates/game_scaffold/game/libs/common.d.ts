//global
declare module gameLib {

    interface IData {

        /**
         * 是否是新版横屏游戏
         */
        isNewLandscapeGame: boolean

    }

    interface IGameData {

        /**
         * 用户今日在该游戏下注次数  服务端传送过来的
         * @default 0
         */
        userBetTimes?: number

        /**
         * 当前游戏是否有活动
         */
        isBetActivity?: boolean

    }

    interface BaseGameData {

        /**
         * 用户今日在该游戏下注次数  服务端传送过来的
         * @default 0
         */
        userBetTimes?: number

        /**
         * 当前游戏是否有活动
         */
        isBetActivity?: boolean

    }

    interface SlotModel {

        /**
         * 获取列数据
         * @param {number} col 第几列  0开始
         */
        getColumnData<T>(col: number): T

    }

}

declare module gameLib.SceneManager {
    /**
     * 调用api失败弹窗提示
     * @param {ParamHandler} callback 关闭弹窗后调用
     * @param request
     */
    export function showApiRequestFailed(callback?: ParamHandler, request?: tsCore.AjaxRequest): void

}


type initResponse = {}

type BetResponse = {
    /** 余额 */
    balance: number
    /** 盈利 */
    win: number
}

type FreeSpinResponse = {
    /** 余额 */
    balance: number
    /** 盈利 */
    win: number
}

/**
 * 金币散开即隐粒子动画播放配置
 */
type CoinBurstPlayConfig = {
    /** 起点对象（取其中心坐标，与 x/y 二选一） */
    anchor?: fgui.GObject;
    /** 固定起点 x（与 y 一起使用，优先于 anchor） */
    x?: number;
    /** 固定起点 y */
    y?: number;
    /** 中奖金额（决定金币数量，可选） */
    amount?: number;
    /** 金币容器父级（缺省 GRoot.inst） */
    parent?: fgui.GComponent;
    /** 是否强制指定竖版模式；缺省时根据宿主/舞台高宽比自动智能识别 (height > width) */
    isPortrait?: boolean;
    /** 自定义粒子基础缩放系数（默认 1.0） */
    scale?: number;
};

/**
 * 金币风暴满屏粒子动画播放配置
 */
type CoinShowerPlayConfig = {
    /** 金币数量（默认 40） */
    density?: number;
    /** 爆发中心 X（与 anchor 二选一） */
    x?: number;
    /** 爆发中心 Y */
    y?: number;
    /** FairyGUI 锚点（优先于 x/y） */
    anchor?: fgui.GObject;
    /** 金币容器父级（缺省 GRoot.inst） */
    parent?: fgui.GComponent;
    /** 渲染区域高度（缺省取 parent 高度） */
    height?: number;
    /** 是否强制指定竖版模式；缺省时根据宿主/舞台高宽比自动智能识别 (height > width) */
    isPortrait?: boolean;
    /** 自定义粒子基础缩放系数（默认 1.0） */
    scale?: number;
    /** 喷发冲力/高度缩放系数（默认 1.0，如 1.5 带来更高耸的喷射弧顶） */
    burstSpeedScale?: number;
    /** 全部完成回调 */
    onComplete?: (...args: any[]) => void;
};

/**
 * 金币飞向终点磁吸动画播放配置
 *
 * 两阶段动画：burst（喷发物理扩散）→ suck（磁吸终点轨迹插值）
 */
type CoinFlyPlayConfig = {
    /** 起点对象，自动取其中心坐标（除非已设置 pivotAsAnchor）；x/y 优先于此字段 */
    anchor?: fgui.GObject;
    /** 固定起点 x（需与 y 同时提供，优先于 anchor） */
    x?: number;
    /** 固定起点 y（需与 x 同时提供，优先于 anchor） */
    y?: number;
    /** 终点，支持三种类型：GObject（每帧动态追踪中心坐标）、[x, y] 数组、{ x, y } 对象 */
    end?: fgui.GObject | { x: number; y: number } | [number, number];
    /** 固定终点 x（需与 endY 同时提供，优先于 end） */
    endX?: number;
    /** 固定终点 y（需与 endX 同时提供，优先于 end） */
    endY?: number;
    /** 金币数量（默认 50；金币按 maxDelay = min(35, count×0.6) 帧分批延时出场） */
    coinCount?: number;
    /** 中奖总金额（默认 100，均分到每枚金币用于 onCoinArrive 回调的单币价值） */
    amount?: number;
    /** 喷发散射半径（默认 120，影响 bezier/ring 的 cp2 控制点偏移与 parabola 弧线侧偏移幅度） */
    spread?: number;
    /**
     * 飞行轨迹模式（默认 "bezier"）：
     *  - "bezier"：S 型贝塞尔曲线，burst 喷发后三次贝塞尔飞向终点，cp2 随机左右偏移制造 S 形
     *  - "parabola"：抛物线弧形，cp1 沿水平速度方向偏移、cp2 终点上方偏移，形成弧线
     *  - "linear"：直线飞向终点，无曲线插值
     *  - "vortex"：漩涡螺旋，以终点为中心极坐标收缩，旋转约 2.5 圈（5π 弧度）
     *  - "ring"：环形扩散，burst 阶段金币沿 360° 环形均匀分布，suck 阶段贝塞尔收束到终点
     */
    trajectory?: "bezier" | "parabola" | "linear" | "vortex" | "ring";
    /** 金币容器父级（缺省 GRoot.inst） */
    parent?: fgui.GComponent;
    /** 单枚金币抵达终点回调（参数为单币贡献金额 = amount / coinCount） */
    onCoinArrive?: (value: number) => void;
    /** 是否启用终点冲击动效（起点弹性放大 + 终点 Pulse Scale + 碰撞火花，默认 true） */
    impact?: boolean;
    /** 自定义粒子或飞行动画 URL（如 ui://...），缺省为金币 ui://gameCommon/CoinParticle */
    url?: string;
    /** 粒子 URL 别名 */
    particleUrl?: string;
    /** 是否强制指定竖版模式；缺省时根据宿主/舞台高宽比自动智能识别 (height > width) */
    isPortrait?: boolean;
    /** 自定义粒子基础缩放系数（默认 1.0） */
    scale?: number;
    /** 喷发冲力/高度缩放系数（默认 1.0，如 1.5 带来更高耸的喷射弧顶） */
    burstSpeedScale?: number;
    /** 是否启用竖屏超长距离平滑喷泉轨迹（默认 true） */
    verticalBurst?: boolean;
    /** 全部金币到达终点回调 */
    onComplete?: (...args: any[]) => void;
};

/**
 * 单对象/文本飞行动效配置
 */
type ObjectFlyPlayConfig = {
    /** 要飞行的显示对象或希望克隆飞行的源对象（如中奖文本 winText、GLoader、图标等） */
    target: fgui.GObject;
    /** 是否在内部自动高保真拷贝一份副本进行飞行（默认 true；若明确设为 false 则直接移动源 target） */
    clone?: boolean;
    /** 飞行结束后是否自动销毁拷贝的副本对象（默认 true，仅在 clone 为 true 时生效） */
    autoDispose?: boolean;
    /** 起点：支持 GObject（自动对齐几何中心）、Point、{x, y}、[x, y]；缺省自动以 target 当前几何中心为起点 */
    start?: fgui.GObject | Laya.Point | { x: number; y: number } | [number, number];
    /** 终点：支持 GObject（自动对齐几何中心）、Point、{x, y}、[x, y] */
    end: fgui.GObject | Laya.Point | { x: number; y: number } | [number, number];
    /** 是否强制指定竖屏模式（缺省自动根据屏幕高宽比智能识别 height > width） */
    isPortrait?: boolean;
    /** 统一动画时长（毫秒）。若显式指定此项，则横竖屏均采用此固定时长 */
    duration?: number;
    /** 竖屏模式下的专属动画时长（毫秒，缺省默认 650ms） */
    durationPortrait?: number;
    /** 横屏模式下的专属动画时长（毫秒，缺省默认 450ms） */
    durationLandscape?: number;
    /** 延时启动时间（毫秒），默认 0 */
    delay?: number;
    /** 终点等比缩放比例，默认 0.5 */
    scale?: number;
    /** X轴终点缩放比例（优先于 scale） */
    scaleX?: number;
    /** Y轴终点缩放比例（优先于 scale） */
    scaleY?: number;
    /** 终点透明度（默认保持不变） */
    alpha?: number;
    /** 缓动曲线，默认 Laya.Ease.quadOut */
    ease?: Function;
    /** 挂载父容器，缺省为 GRoot.inst */
    parent?: fgui.GComponent;
    /** 完成回调（支持普通函数或 Laya.Handler） */
    onComplete?: ((...args: any[]) => void) | Laya.Handler;
};

/**
 * 优惠券扩展（coupon/all 接口响应新增 label 字段）
 */
type CouponsLabelData = Coupons & {
    /** 来源标签文案；长度大于 0 时在 ActivityItem 中显示来源标签（仅控制显隐） */
    label?: string
}

declare namespace game {

	export enum Action {
	    SHOW_FEEDBACK = "show_feedback",
	    SHOW_BET_HISTORY = "show_bet_history",
	    /** 更新省钱卡信息 */
	    UPDATE_DEBUG_INFO = "update_debug_info",
	    /** 执行一次spin */
	    GAME_SEND_SPIN = "game_spin",
	    /** 开始 auto spin */
	    GAME_SEND_AUTO_SPIN = "game_send_auto_spin",
	    /** 结束 auto spin */
	    GAME_STOP_AUTO_SPIN = "game_stop_auto_spin",
	    /** 进入空闲状态 */
	    GAME_ENTER_IDLE = "game_enter_idle",
	    /** 给 bet 赋值到指定位置 */
	    GAME_SET_BET = "game_set_bet",
	    /**
	     * 显示自动spin设置窗口
	     */
	    GAME_SHOW_AUTO_SPIN_SETUP_WINDOW = "game_show_auto_spin_setup_window",
	    /** 显示bonus规则窗口 */
	    GAME_SHOW_BONUS_RULE_WINDOW = "game_show_bonus_rule_window",
	    /** 显示防沉迷提示 */
	    SHOW_ANTI_ADDICTION_TIPS = "show_anti_addiction_tips",
	    UPDATE_HEAD_ICON = "update_head_icon",
	    /** 完成下注次数调用 */
	    COMPLETE_USER_BET_COUNT = "complete_user_bet_count",
	    /** 显示中奖界面 */
	    GAME_AFRUIT_SHOW_WIN_WINDOW = "game_afruit_show_win_window",
	    GAME_BACCARAT_AGAIN = "game_baccarat_again",
	    /** 显示赢钱金额 */
	    GAME_BACCARAT_SHOW_WIN_WINDOW = "game_baccarat_show_win_window",
	    /** 显示中奖结算界面 */
	    GAME_ROULETTE_SHOW_LOTTERY_WINDOW = "game_roulette_show_lottery_window",
	    /** 更新总投注 */
	    GAME_WHEEL_UPDATE_BET_TOTAL = "game_wheel_update_bet_total",
	    /** 更新赢钱文本 */
	    GAME_WHEEL_UPDATE_WIN_TEXT = "game_wheel_update_win_text",
	    /** 外圈转动结束 */
	    GAME_WHEEL_OUTER_RUN_END = "game_wheel_outer_run_end",
	    /** 内圈转动结束 */
	    GAME_WHEEL_INNER_RUN_END = "game_wheel_inner_run_end",
	    /** 显示中奖角度灯光 */
	    GAME_WHEEL_SHOW_LOTTERY_ITEM_LIGHT = "game_wheel_show_lottery_item_light",
	    /** 显示外圈中奖角度灯光 */
	    GAME_WHEEL_SHOW_OUTER_LOTTERY_ITEM_LIGHT = "game_wheel_show_outer_lottery_item_light",
	    /** 播放滚动动画 */
	    GAME_WHEEL_PLAY_RUN_ANI = "game_wheel_play_run_ani",
	    /** 播放滚动倍数动画 */
	    GAME_WHEEL_PLAY_MULTIPLE_RUN_ANI = "game_wheel_play_multiple_run_ani",
	    /** 播放中奖动画 */
	    GAME_WHEEL_PLAY_WINNING_ANI = "game_wheel_play_winning_ani",
	    /** 显示赢钱的界面 */
	    GAME_WHEEL_SHOW_WIN_WINDOW = "game_wheel_show_win_window",
	    /** 显示中奖界面 */
	    GAME_AFRICA_CUP_SHOW_WIN_WINDOW = "game_africa_cup_show_win_window",
	    /** 显示大奖中奖界面 */
	    GAME_AFRICA_CUP_SHOW_GRAND_PRIZE_WIN_WINDOW = "game_africa_cup_show_grand_prize_win_window",
	    /** 显示选择队伍窗口 */
	    GAME_PREMIER_LEAGUE_SHOW_SELECT_WINDOW = "game_premier_league_show_select_window",
	    /** 显示选择后的数据 */
	    GAME_PREMIER_LEAGUE_UPDATE_SELECT = "game_premier_league_update_select",
	    /** 播放中奖动画 */
	    GAME_PREMIER_LEAGUE_PLAY_WIN_ANI = "game_premier_play_win_ani",
	    /** 播放中奖灯光 */
	    GAME_PREMIER_LEAGUE_PLAY_LIGHT_ANI = "game_premier_league_play_light_ani",
	    /** 显示赢钱界面 */
	    GAME_FIRE_OF_PASSION_SHOW_WIN_UI = "game_fire_of_passion_show_win_ui",
	    /** 显示中奖动画 */
	    GAME_FIRE_OF_PASSION_SHOW_LOTTERY_ANI = "game_fire_of_passion_show_lottery_ani",
	    /** 播放转盘动画 */
	    GAME_FIRE_OF_PASSION_PLAY_WHEEL_ANI = "game_fire_of_passion_play_wheel_ani",
	    /** 显示赢钱提示 */
	    GAME_WINGS_OF_FOREST_SHOW_WIN_TIP = "game_wings_of_forest_show_win_tip",
	    /** 显示中奖动画 */
	    GAME_WINGS_OF_FOREST_SHOW_LOTTERY_ANI = "game_wings_of_forest_show_lottery_ani",
	    /** 播放重复wild */
	    GAME_WINGS_OF_FOREST_PLAY_REPLACE_WILD_ANI = "game_wings_of_forest_play_replace_wild_ani",
	    /** 显示赢钱界面 */
	    GAME_WINGS_OF_FOREST_SHOW_WIN_UI = "game_wings_of_forest_show_win_ui",
	    /** 显示增加的 free spin */
	    GAME_WINGS_OF_FOREST_SHOW_FREE_SPIN_ADD = "game_wings_of_forest_show_free_spin_add"
	}
	
	export enum Analytics {
	    ENTER = 0
	}
	
	/**
	 * 金币散开即隐粒子动画
	 *
	 * 在指定位置喷发一批金币，向上高抛 + 重力下沉 + 后段渐隐，无磁吸终点。
	 * 参考 football carnival index.js 的 spawnParticles 物理模型，使用 FGUI GMovieClip 渲染。
	 */
	export class CoinBurstAni {
	    /** 金币序列帧资源（复用 TopView.goldAniUI） */
	    static readonly COIN_URL = "ui://gameCommon/CoinParticle";
	    private static readonly GRAVITY;
	    private static container;
	    private static coins;
	    /**
	     * 在指定位置播放散开即隐金币粒子
	     * @param config 配置：anchor（起点对象）或 x/y（固定坐标），amount（金额决定数量）
	     */
	    static play(config: CoinBurstPlayConfig): void;
	    private static update;
	    /** 销毁当前动画 */
	    static dispose(): void;
	}
	
	/**
	 * 金币飞向终点磁吸动画
	 *
	 * 两阶段：burst（喷发物理扩散）→ suck（磁吸终点轨迹插值）
	 * 支持 5 种轨迹：bezier(S曲线) / parabola(抛物线) / linear(直线) / vortex(漩涡) / ring(环形扩散)
	 * 参考 CoinWinAnimation.js v2.2.0 的物理模型，使用 FGUI GMovieClip 渲染。
	 */
	export class CoinFlyAni {
	    static readonly COIN_URL = "ui://gameCommon/CoinParticle";
	    private static readonly DRAG;
	    private static container;
	    private static particles;
	    private static isActive;
	    private static host;
	    private static onCompleteCallback;
	    private static endTarget;
	    private static fixedEndX;
	    private static fixedEndY;
	    private static startTarget;
	    private static impactTarget;
	    private static startScale;
	    private static impactScale;
	    private static sparks;
	    private static enableImpact;
	    /**
	     * 播放飞向终点磁吸动画
	     * @param config 起点终点、轨迹模式、金币数量、回调等
	     */
	    static play(config: CoinFlyPlayConfig): void;
	    private static resolveEnd;
	    private static update;
	    private static spawnSparks;
	    /** 强制停止并清理（触发回调） */
	    static hide(): void;
	    /** 销毁当前动画（不触发回调） */
	    static dispose(): void;
	}
	
	export type ObjectFlyPlayConfig = {
	    /** 要飞行的显示对象或希望克隆飞行的源对象（如中奖文本 winText、GLoader、图标等） */
	    target: fgui.GObject;
	    /** 是否在内部自动高保真拷贝一份副本进行飞行（默认 true；若明确设为 false 则直接移动源 target） */
	    clone?: boolean;
	    /** 飞行结束后是否自动销毁拷贝的副本对象（默认 true，仅在 clone 为 true 时生效） */
	    autoDispose?: boolean;
	    /** 起点：支持 GObject（自动对齐几何中心）、Point、{x, y}、[x, y]；缺省自动以 target 当前几何中心为起点 */
	    start?: fgui.GObject | Laya.Point | {
	        x: number;
	        y: number;
	    } | [number, number];
	    /** 终点：支持 GObject（自动对齐几何中心）、Point、{x, y}、[x, y] */
	    end: fgui.GObject | Laya.Point | {
	        x: number;
	        y: number;
	    } | [number, number];
	    /** 是否强制指定竖屏模式（缺省自动根据屏幕高宽比智能识别 height > width） */
	    isPortrait?: boolean;
	    /** 统一动画时长（毫秒）。若显式指定此项，则横竖屏均采用此固定时长 */
	    duration?: number;
	    /** 竖屏模式下的专属动画时长（毫秒，缺省默认 650ms） */
	    durationPortrait?: number;
	    /** 横屏模式下的专属动画时长（毫秒，缺省默认 450ms） */
	    durationLandscape?: number;
	    /** 延时启动时间（毫秒），默认 0 */
	    delay?: number;
	    /** 终点等比缩放比例，默认 0.5 */
	    scale?: number;
	    /** X轴终点缩放比例（优先于 scale） */
	    scaleX?: number;
	    /** Y轴终点缩放比例（优先于 scale） */
	    scaleY?: number;
	    /** 终点透明度（默认保持不变） */
	    alpha?: number;
	    /** 缓动曲线，默认 Laya.Ease.quadOut */
	    ease?: Function;
	    /** 挂载父容器，缺省为 GRoot.inst */
	    parent?: fgui.GComponent;
	    /** 完成回调（支持普通函数或 Laya.Handler） */
	    onComplete?: ((...args: any[]) => void) | Laya.Handler;
	};
	/**
	 * 单对象/中奖文本飞向目标缓动动效
	 * 替代原 gameLib.GoldAniUtils.playGoldPointAni，提供智能坐标系换算、几何中心绝对对齐、横竖屏自适应时长与生命周期自动闭环
	 */
	export class ObjectFlyAni {
	    /**
	     * 播放单对象/文本飞向终点收集动效
	     * 内部原生支持自动高保真拷贝、几何中心点绝对对齐与生命周期自动销毁
	     * @param config 配置参数
	     */
	    static play(config: ObjectFlyPlayConfig): Laya.Tween;
	    /**
	     * 兼容原 GoldAniUtils.playGoldPointAni 签名的便捷调用
	     */
	    static playPointAni(targetObject: fgui.GObject, startPoint: any, endPoint: any, endHandler?: any, parent?: fgui.GComponent, props?: any): Laya.Tween;
	    /**
	     * 解析任意目标（GObject / Point / {x,y} / [x,y]）的几何中心在指定容器 host 内部的局部坐标
	     * 无论目标对象的 pivot 设置如何，均提取其绝对几何中心 (width/2, height/2)
	     */
	    private static resolveCenterPosition;
	}
	
	/**
	 * 金币风暴满屏粒子动画
	 *
	 * 从指定位置全向 360° 爆发金币，重力下沉 + 风力扰动 + 触底渐隐，满屏金雨效果。
	 * 参考 SlotCoinShower win-animation.js 的物理模型，使用 FGUI GMovieClip 渲染。
	 */
	export class SlotCoinShowerAni {
	    /** 金币序列帧资源（复用 CoinParticle） */
	    static readonly COIN_URL = "ui://gameCommon/CoinParticle";
	    private static readonly GRAVITY_BASE;
	    private static readonly GRAVITY_RANGE;
	    private static readonly COIN_SIZE;
	    private static container;
	    private static particles;
	    private static isActive;
	    private static spawnTarget;
	    private static spawnedCount;
	    private static centerX;
	    private static centerY;
	    private static host;
	    private static boundsHeight;
	    private static onCompleteCallback;
	    private static isPortrait;
	    private static customScale?;
	    /**
	     * 播放金币风暴
	     * @param config.density 金币数量（默认 40）
	     * @param config.x 爆发中心 X（默认屏幕中心）
	     * @param config.y 爆发中心 Y（默认屏幕中心）
	     * @param config.anchor FairyGUI 锚点（优先于 x/y）
	     * @param config.parent 金币容器父级（缺省 GRoot.inst）
	     * @param config.onComplete 完成回调
	     */
	    static play(config?: CoinShowerPlayConfig): void;
	    private static spawnCoin;
	    private static update;
	    /** 强制停止并清理（触发回调） */
	    static hide(): void;
	    /** 销毁当前动画（不触发回调） */
	    static dispose(): void;
	}
	
	export type { ObjectFlyPlayConfig };
	/**
	 * 通用特效/动效门面库 (FxAni)
	 * 原 CoinAni 升级版，不仅支持金币，还支持钻石、道具、粒子雨及任意中奖文本/UI对象的缓动飞行与磁吸。
	 */
	export class FxAni {
	    /**
	     * 当前视口是否为竖版（高度大于宽度）
	     */
	    static get isPortrait(): boolean;
	    /** 散开即隐粒子（向上高抛 + 渐隐，无终点，支持自动竖版放大与高冲力） */
	    static playBurst(config: CoinBurstPlayConfig): void;
	    /** 满屏粒子雨/金币雨（全向爆炸 + 触底渐隐，无终点，支持自动竖版放大与高冲力） */
	    static playShower(config: CoinShowerPlayConfig): void;
	    /** 飞向终点磁吸（burst→suck 两阶段物理模型，5 种轨迹，支持金币及自定义粒子 url，自动适配竖版大尺寸与高纵深超千像素长弧贝塞尔弹道） */
	    static playFly(config: CoinFlyPlayConfig): void;
	    /**
	     * 单显示对象（中奖文本、宝箱、图标等）飞向目标缓动动效
	     * 彻底替代 gameLib.GoldAniUtils.playGoldPointAni
	     */
	    static playObjectFly(config: ObjectFlyPlayConfig): Laya.Tween;
	    /**
	     * 兼容原 GoldAniUtils.playGoldPointAni 参数签名的便捷调用
	     */
	    static playPointAni(targetObject: fgui.GObject, startPoint: any, endPoint: any, endHandler?: any, parent?: fgui.GComponent, props?: any): Laya.Tween;
	    /**
	     * 停止并清理当前飞行动效
	     */
	    static stopFly(): void;
	}
	
	/**
	 * 动效统一调用入口（向前兼容别名）
	 * 推荐后续统一使用 FxAni
	 */
	export class CoinAni extends FxAni {
	}
	
	export enum Consts {
	    /** 游戏首页 */
	    GAME_HOME = 999999,
	    /** 大满贯  全部中大的（除苹果和BAR）*/
	    GAME_FRUIT_BIG_GRAND_SLAM = 1,
	    /** 大火车   5节火车*/
	    GAME_FRUIT_BIG_TRAIN = 2,
	    /** 小火车   3节火车*/
	    GAME_FRUIT_SMALL_TRAIN = 3,
	    /** 大三元   中三个大结果*/
	    GAME_FRUIT_BIG_SAN_YUAN = 4,
	    /** 小满贯  全部中小的（除苹果和BAR）*/
	    GAME_FRUIT_SMALL_LITTLE_SLAM = 5,
	    /** 小三元 */
	    GAME_FRUIT_SMALL_SAN_YUAN = 6,
	    /** 大四喜  中四个苹果*/
	    GAME_FRUIT_BIG_SI_XI = 7,
	    /** 随机送灯  随机反弹一个结果*/
	    GAME_FRUIT_RANDOM = 8
	}
	
	export class GameData implements gameLib.IData {
	    betHistory: any[];
	    betStatic: any[];
	    initPeriod: number;
	    initRoomCurBet: any[];
	    initRoomTotalItem: any[];
	    isFirstLogin: boolean;
	    isFirstNick: boolean;
	    jackpot: number;
	    lotteryTime: number;
	    oldSendChatTimer: number;
	    period: number;
	    channel: string;
	    country: string;
	    language: string;
	    isNewLandscapeGame: boolean;
	    /** 是否是未充值过的用户 */
	    firstDepositActivity: number;
	    /**
	     * 周三活动开启否
	     */
	    wedDeposit: number;
	    /** 游戏开礼包获得的能量 */
	    afz_energy: number;
	    /** 默认头像 */
	    defaultIcon: string;
	    getGameUrl(url: string): string;
	    getWapUrl(url: string): string;
	    getCountry(urlParam: gameLib.UrlParam): string;
	    private _getCountry;
	    getErrorUrl(): string;
	}
	
	export enum GameStr {
	    /** 下载页面地址 */
	    DOWN_URL = 100,
	    /** 游戏最高额度不能满足使用卷的需求 */
	    NOT_MET_USE_NEED = 101,
	    /** 切换投注额 */
	    CHANGE_SLOT_BET_AMOUNT = 102,
	    /** 切换投注总额 */
	    CHANGE_STAKE_BET_AMOUNT = 103,
	    /** 切换投注额将会清空累计的奖励 */
	    CHANGE_SLOT_BET_AMOUNT_CONTENT = 104,
	    /** 切换投注总额将会清空累计的奖励 */
	    CHANGE_STAKE_BET_AMOUNT_CONTENT = 105,
	    /** 获得更多礼包 */
	    GET_MORE_GIFT = 106,
	    /** 防沉迷提示文案 */
	    ANTI_ADDICTION_TIPS = 107,
	    API_REQUEST_FAILED = 108
	}
	
	export class BetActivityBtn extends tsCore.EButton {
	    private clickInvalid;
	    constructor();
	    protected onConstruct(): void;
	    private onDragStart;
	    private onDragEnd;
	    private onBtnAction;
	    loaderRes(): void;
	    show(): void;
	    set selected(val: boolean);
	}
	
	export class MyUBBParser extends fgui.UBBParser {
	    constructor();
	    private onIcon;
	    protected onTag_U(tagName: string, end: boolean, attr: string): string;
	}
	
	export class DefineConfig {
	    static init(): void;
	    private static defineLaya;
	    private static defineFairy;
	    private static defineLib;
	    private static JavaSendOpen;
	}
	
	/**
	 * 奖金礼包数据格式
	 */
	export type GiftPacks = {
	    /**
	     * 创建礼包的时间 秒
	     */
	    created_at: number;
	    /** 距离到期还剩时间 秒 */
	    expire_sec: number;
	    /**
	     * 礼包唯一id
	     */
	    id: string;
	};
	
	export class GameConfig {
	    static width: number;
	    static height: number;
	    static scaleMode: string;
	    static screenMode: string;
	    static alignV: string;
	    static alignH: string;
	    static debug: boolean;
	    static stat: boolean;
	    static init(): void;
	}
	
	export class GuestModel implements gameLib.IGuestModel {
	    /** 游客id */
	    guestUID: number;
	    /** 游客模式玩次数 */
	    guestPlayCount: number;
	    guestInitMoney: number;
	    /** 玩家押注值 */
	    userReallyBet: number;
	    /** 玩家获得的刮刮卡 */
	    scratcherTickets: GiftPacks[];
	    /** 玩家已获得张数 */
	    getCount: number;
	    /** 刮刮卡累计ID */
	    private _id;
	    private getTicketIncBet;
	    constructor();
	    /** 玩家清空数据 */
	    clearData(): void;
	    /**
	     * 玩家增加押注
	     * @param url 访问网址
	     * @param data 押注额度
	     */
	    playAdd(url: string, data: any): void;
	    openHandler(value: any): {
	        code: number;
	        data: {
	            win: number;
	            balance: number;
	            user_really_bet: number;
	            get_ticket_inc_bet: number;
	            scratcher_tickets: GiftPacks[];
	        };
	        message: string;
	        "auth-token": string;
	    };
	}
	
	/**
	 * 玩法配置视图的宿主接口
	 */
	export interface IPlayConfigHost<T extends gameLib.BaseGameData = gameLib.BaseGameData> {
	    gameData: T;
	    lineNum: number;
	    linesChange?: tsCore.ValueSwitcher<number>;
	    betChange?: tsCore.ValueSwitcher<number>;
	    totalBetText?: fgui.GRichTextField;
	    winText?: fgui.GTextField;
	    winColorController?: fgui.Controller;
	    spinBtn?: fgui.GButton;
	    sendAction(action: string | number, ...args: any[]): void;
	    updateSpinText(money: number): void;
	}
	
	/**
	 * 玩法执行视图的宿主接口
	 */
	export interface IPlayExecHost<T extends gameLib.BaseGameData = gameLib.BaseGameData> {
	    gameData: T;
	    lineNum: number;
	    spinBtn: fgui.GButton;
	    spinStatusController: fgui.Controller;
	    spinSound?: string;
	    autoSpinBtn?: fgui.GButton;
	    couponsBtn?: gameLib.ActivityButton;
	    promptTip: gameLib.PromptTip;
	    createPopupMnu: boolean;
	    allBtnState: boolean;
	    betCountPopupMenu: tsCore.GamePopupMenu;
	    turboController?: fgui.Controller;
	    turboBtn?: fgui.GButton;
	    /** 自动旋转状态控制器（UI 有则激活时选中1，否则0） */
	    autoSpinController?: fgui.Controller;
	    AUTO_SPIN_AUTO_SPIN: string;
	    AUTO_SPIN_UNTIL_STOP: string;
	    AUTO_SPIN_AUTO_SPINS: string;
	    betChange?: tsCore.ValueSwitcher<number>;
	    linesChange?: tsCore.ValueSwitcher<number>;
	    sendAction(action: string | number, ...args: any[]): void;
	    getChild<R = fgui.GObject>(...name: string[]): R;
	    getController(name: string): fgui.Controller;
	}
	
	export enum Urls {
	    /** 获取服务器时间 */
	    GAME_SERVER_TIME = "/game/server-time",
	    /** 优惠券投注 */
	    URL_COUPON_BET = "/coupon/bet",
	    /** 获取奖池数据 */
	    URL_DAILY_CASH_POOL = "/daily/cashpool",
	    URL_COMMON_BET = "/game/{game}/bet",
	    URL_COMMON_BET_DEMO = "/game/{game}/bet/demo",
	    URL_COMMON_LUCKY = "/game/{game}/lucky",
	    URL_COMMON_LUCKY_DEMO = "/game/{game}/lucky/demo",
	    URL_COMMON_FREE = "/game/{game}/free",
	    URL_COMMON_FREE_DEMO = "/game/{game}/free/demo",
	    URL_COMMON_FREE_SELECT = "/game/{game}/freeSpinSelect",
	    URL_COMMON_FREE_SELECT_DEMO = "/game/{game}/freeSpinSelect/demo",
	    /** 水果机单机结果 */
	    AFRUIT_RESULT_URL = "/game/a-fruit/bet",
	    /** 水果机发送聊天 */
	    FRUIT_SEND_URL = "/game/fruit/send",
	    URL_DEMO_FRUIT = "/demo/fruit",
	    /** 单机版百家乐结果 */
	    ABACCARAT_RESULT_URL = "/game/a-baccarat/bet",
	    /** 百家乐结果 */
	    BACCARAT_RESULT_URL = "/game/baccarat/result",
	    /** 百家乐发送聊天 */
	    BACCARAT_SEND_URL = "/game/baccarat/send",
	    URL_DEMO_BACCARAT = "/demo/baccarat",
	    /** 单机版大转盘结果 */
	    URL_FRUIT_WHEEL_RESULT = "/game/a-big-wheel/bet",
	    /** 大转盘发送聊天 */
	    URL_WHEEL_SEND_URL = "/game/big-wheel/send",
	    URL_DEMO_BIT_WHEEL = "/demo/bigWheel",
	    /** 单机轮盘结果 */
	    AROULETTE_RESULT_URL = "/game/a-roulette/bet",
	    /** 网络轮盘投注 */
	    ROULETTE_BET_URL = "/game/roulette/bet",
	    /** 网络轮盘结果 */
	    ROULETTE_RESULT_URL = "/game/roulette/result",
	    URL_DEMO_ROULETTE = "/demo/roulette",
	    /** 动物园结果 */
	    ZOO_RESULT_URL = "/game/a-zoo/bet",
	    URL_DEMO_ZOO = "/demo/zoo",
	    /** 足球老虎机 */
	    URL_FOOTBALL_SLOT_MAC_RESULT = "/game/a-slot-mac/bet",
	    /** 免费游戏类型选择 */
	    URL_FREE_SPIN_SELECT = "/game/a-slot-mac/freeSpinSelect",
	    /** 免费游戏请求结果接口 */
	    URL_FREE_SPIN = "/game/a-slot-mac/freeSpin",
	    /** demo足球老虎机 */
	    URL_DEMO_FOOTBALL_SLOT_MAC_RESULT = "/demo/aSlotMac",
	    /** demo足球老虎机 */
	    URL_DEMO_FOOTBALL_SELECT = "/demo/aSlotFreeSpin/select",
	    /** demo免费游戏请求结果接口 */
	    URL_DEMO_FREE_SPIN = "/demo/aSlotFreeSpin",
	    /** 足球老虎机 */
	    URL_GOLD_CUP_K2_RESULT = "/game/goldcupk2/bet",
	    /** 免费游戏类型选择 */
	    URL_GOLD_CUP_K2_FREE_SPIN_SELECT = "/game/goldcupk2/freeSpinSelect",
	    /** 免费游戏请求结果接口 */
	    URL_GOLD_CUP_K2_FREE_SPIN = "/game/goldcupk2/freeSpin",
	    /** demo足球老虎机 */
	    URL_GOLD_CUP_K2_DEMO_RESULT = "/demo/goldcupk2/bet/demo",
	    /** demo足球老虎机 */
	    URL_GOLD_CUP_K2_DEMO_SELECT = "/demo/goldcupk2/freeSpinSelect/demo",
	    /** demo免费游戏请求结果接口 */
	    URL_GOLD_CUP_K2_DEMO_FREE_SPIN = "/demo/goldcupk2/freeSpin/demo",
	    URL_HOT_SPIN_BET = "/game/hot-spin/bet",
	    URL_HOT_SPIN_BET_DEMO = "/game/hot-spin/betdemo",
	    URL_HOT_SAFIRA_BET = "/game/slotmac/hotsafira/bet",
	    URL_HOT_SAFIRA_BET_DEMO = "/game/slotmac/hotsafira/betdemo",
	    URL_HOT_SAFIRA_K2_BET = "/game/hotsafirak2/bet",
	    URL_HOT_SAFIRA_K2_BET_DEMO = "/game/hotsafirak2/betdemo",
	    URL_GAME_PUKE_BET = "/game/puke/bet",
	    URL_GAME_PUKE_BET_DEMO = "/game/puke/bet/demo",
	    URL_GAME_FRUIT_BET = "/game/goldfruit/bet",
	    URL_GAME_FRUIT_BET_DEMO = "/game/goldfruit/bet/demo",
	    URL_GAME_FRUIT_AFRICA_CUP_BET = "/game/fruit/africa_cup/bet",
	    URL_GAME_FRUIT_AFRICA_CUP_BET_DEMO = "/game/fruit/africa_cup/betdemo",
	    URL_GAME_SLOT_BEAST_BET = "/game/slotmac/beast/bet",
	    URL_GAME_SLOT_BEAST_BET_DEMO = "/game/slotmac/beast/bet/demo",
	    URL_GAME_PREMIER_LEAGUE_BET = "/game/league/bet",
	    URL_GAME_PREMIER_LEAGUE_BET_DEMO = "/game/league/bet/demo",
	    URL_PREMIER_LEAGUE_STAR_BET = "/game/leaguestar/bet",
	    URL_PREMIER_LEAGUE_STAR_BET_DEMO = "/game/leaguestar/bet/demo",
	    URL_PREMIER_LEAGUE_STAR_LUCKY = "/game/leaguestar/lucky",
	    URL_PUNCH_IT_HARD_BET = "/game/punchhard/bet",
	    URL_PUNCH_IT_HARD_BET_DEMO = "/game/punchhard/bet/demo",
	    URL_PUNCH_IT_HARD_DICE = "/game/punchhard/dice",
	    URL_PUNCH_IT_HARD_LUCKY = "/game/punchhard/dicespin",
	    URL_BAHATI_KING_BET = "/game/bahatiking/bet",
	    URL_BAHATI_KING_BET_DEMO = "/game/bahatiking/bet/demo",
	    URL_BAHATI_KING_FREE = "/game/bahatiking/free",
	    URL_BAHATI_KING_FREE_DEMO = "/game/bahatiking/free/demo",
	    URL_FIRE_OF_PASSION_BET = "/game/fireofpassion/bet",
	    URL_FIRE_OF_PASSION_BET_DEMO = "/game/fireofpassion/bet/demo",
	    URL_FIRE_OF_PASSION_FREE = "/game/fireofpassion/free",
	    URL_FIRE_OF_PASSION_FREE_DEMO = "/game/fireofpassion/free/demo",
	    URL_WINGS_OF_FOREST_BET = "/game/wingsofforest/bet",
	    URL_WINGS_OF_FOREST_BET_DEMO = "/game/wingsofforest/bet/demo",
	    URL_WINGS_OF_FOREST_FREE = "/game/wingsofforest/free",
	    URL_WINGS_OF_FOREST_FREE_DEMO = "/game/wingsofforest/free/demo",
	    URL_CHRISTMAS_BOMB_BET = "/game/christmasbomb/bet",
	    URL_CHRISTMAS_BOMB_BET_DEMO = "/game/christmasbomb/bet/demo",
	    URL_CHRISTMAS_BOMB_FREE = "/game/christmasbomb/free",
	    URL_CHRISTMAS_BOMB_FREE_DEMO = "/game/christmasbomb/free/demo",
	    URL_SUPER_CHAMPIONS_BET = "/game/net/superchampions/bet",
	    URL_SUPER_CHAMPIONS_RESULT = "/game/net/superchampions/result",
	    URL_SUPER_CHAMPIONS_BET_HISTORY = "/game/net/superchampions/history",
	    URL_SUPER_CHAMPIONS_LEAGUE = "/game/net/superchampions/league",
	    URL_SUPER_CHAMPIONS_TEAM_MATCH_INFO = "/game/net/superchampions/team/matchinfo",
	    URL_SUPER_CHAMPIONS_SEASON_INFO = "/game/net/superchampions/season/info",
	    URL_KING_OF_BOXING_MATCH = "/game/kingbox/match",
	    URL_KING_OF_BOXING_PLAY = "/game/kingbox/play",
	    URL_KING_OF_BOXING_OPEN_CARD = "/game/kingbox/open_card",
	    URL_FOOTBALL_CARNIVAL_BET = "/game/carnival/bet",
	    URL_FOOTBALL_CARNIVAL_BET_DEMO = "/game/carnival/bet/demo",
	    URL_FOOTBALL_CARNIVAL_FREE = "/game/carnival/free",
	    URL_FOOTBALL_CARNIVAL_FREE_DEMO = "/game/carnival/free/demo",
	    URL_BINGO_BET = "/game/bingo/bet",
	    URL_BINGO_BET_DEMO = "/game/bingo/bet/demo",
	    URL_BINGO_REFRESH = "/game/bingo/refresh",
	    URL_BINGO_REFRESH_DEMO = "/game/bingo/refresh/demo",
	    URL_BINGO_EXTRA = "/game/bingo/extra",
	    URL_BINGO_EXTRA_DEMO = "/game/bingo/extra/demo",
	    URL_BINGO_EXTRA_CLOSE = "/game/bingo/extraclose",
	    URL_BINGO_EXTRA_CLOSE_DEMO = "/game/bingo/extraclose/demo",
	    URL_BINGO_BONUS = "/game/bingo/bonus",
	    URL_BINGO_BONUS_DEMO = "/game/bingo/bonus/demo",
	    URL_MIGHTY_BUFFALO_BET = "/game/mightybuffalo/bet",
	    URL_MIGHTY_BUFFALO_BET_DEMO = "/game/mightybuffalo/bet/demo",
	    URL_MIGHTY_BUFFALO_FREE = "/game/mightybuffalo/free",
	    URL_MIGHTY_BUFFALO_FREE_DEMO = "/game/mightybuffalo/free/demo",
	    URL_SAFARI_GOLD_BET = "/game/safarigold/bet",
	    URL_SAFARI_GOLD_BET_DEMO = "/game/safarigold/bet/demo",
	    URL_SAFARI_GOLD_FREE = "/game/safarigold/free",
	    URL_SAFARI_GOLD_FREE_DEMO = "/game/safarigold/free/demo",
	    URL_GHOST_PIRATES_BET = "/game/ghostpriates/bet",
	    URL_GHOST_PIRATES_BET_DEMO = "/game/ghostpriates/bet/demo",
	    URL_GHOST_PIRATES_FREE = "/game/ghostpriates/freeSpin",
	    URL_GHOST_PIRATES_FREE_DEMO = "/game/ghostpriates/freeSpin/demo",
	    URL_MAGIC_ORCHARD_BET = "/game/MagicOrchard/bet",
	    URL_MAGIC_ORCHARD_BET_DEMO = "/game/MagicOrchard/bet/demo",
	    URL_MAGIC_ORCHARD_FREE = "/game/MagicOrchard/lucky",
	    URL_MAGIC_ORCHARD_FREE_DEMO = "/game/MagicOrchard/lucky/demo",
	    URL_MAGIC_ORCHARD_WINPLAY = "/game/MagicOrchard/winplay",
	    URL_MAGIC_ORCHARD_WINPLAY_DEMO = "/game/MagicOrchard/winplay/demo",
	    URL_BOOK_OF_WIN_BET = "/game/bookofwin/bet",
	    URL_BOOK_OF_WIN_BET_DEMO = "/game/bookofwin/bet/demo",
	    URL_BOOK_OF_WIN_FREE = "/game/bookofwin/lucky",
	    URL_BOOK_OF_WIN_FREE_DEMO = "/game/bookofwin/lucky/demo",
	    URL_BOOK_OF_WIN_WINPLAY = "/game/bookofwin/winplay",
	    URL_BOOK_OF_WIN_WINPLAY_DEMO = "/game/bookofwin/winplay/demo",
	    URL_SPINIONS_DAY_BET = "/game/spinionsday/bet",
	    URL_SPINIONS_DAY_BET_DEMO = "/game/spinionsday/bet/demo",
	    URL_SPINIONS_DAY_FREE = "/game/spinionsday/lucky",
	    URL_SPINIONS_DAY_FREE_DEMO = "/game/spinionsday/lucky/demo",
	    URL_SPINIONS_DAY_RESPIN = "/game/spinionsday/respin",
	    URL_SPINIONS_DAY_RESPIN_DEMO = "/game/spinionsday/respin/demo",
	    URL_HOT_GIRL_BET = "/game/hotgirl/bet",
	    URL_HOT_GIRL_BET_DEMO = "/game/hotgirl/bet/demo",
	    URL_HOT_GIRL_FREE = "/game/hotgirl/lucky",
	    URL_HOT_GIRL_FREE_DEMO = "/game/hotgirl/lucky/demo",
	    URL_HOT_GIRL_RESPIN = "/game/hotgirl/respin",
	    URL_HOT_GIRL_RESPIN_DEMO = "/game/hotgirl/respin/demo",
	    URL_WILD_BULLETS_BET = "/game/wildbullets/bet",
	    URL_WILD_BULLETS_BET_DEMO = "/game/wildbullets/bet/demo",
	    URL_WILD_BULLETS_FREE = "/game/wildbullets/free",
	    URL_WILD_BULLETS_FREE_DEMO = "/game/wildbullets/free/demo"
	}
	
	/**
	 * GameMania Recharge SDK 实例最小接口（仅声明接入文档中实际使用的能力）
	 * @see E:/QUICKSTART.md 快速接入文档
	 */
	interface IRechargeSDKInstance {
	    mount(options: any): void;
	    openPopup(options?: any): void;
	    closePopup(): void;
	    updateToken(token: string): void;
	    updateInfo(info: any): void;
	    destroy(): void;
	}
	type IRechargeSDKCtor = new (config: any) => IRechargeSDKInstance;
	global {
	    interface Window {
	        GameManiaRechargeSDKMobile?: IRechargeSDKCtor;
	        GameManiaRechargeSDKPC?: IRechargeSDKCtor;
	    }
	}
	/**
	 * 第三方充值 SDK 管理器（单例）
	 * 负责动态加载 UMD 脚本（环境×端 CDN 四选一）、实例化与全端充值弹窗调起、
	 * track/navigate/close 事件桥接、token/用户信息实时同步。
	 * 加载或调用失败时由调用方（AccountTransferWindow / Main）回退旧充值链路。
	 */
	export class RechargeSDK {
	    private static _instance;
	    static get inst(): RechargeSDK;
	    /** CDN 域名（文档 v1 固定） */
	    private static readonly CDN_HOST;
	    /** CDN 路径模板，{side} 为 pc / mobile */
	    private static readonly CDN_PATH;
	    /** 已就绪的 SDK 实例 */
	    private sdk;
	    /** 加载单例 Promise，防止并发重复注入 <script> */
	    private loading;
	    /** 实例创建时的屏幕方向，用于检测动态切屏 */
	    private instanceLandscape;
	    /** 是否 PC 端（口径与 Main.ts 充值分支一致） */
	    private static get isPC();
	    /** 当前是否横屏（运行时实时方向） */
	    private static get isLandscape();
	    /**
	     * 端标识：1=H5 2=Android壳 3=PC 4=iOS壳
	     * 口径复用 AccountTransferWindow.topUpHandler
	     */
	    static getPlatform(): number;
	    /**
	     * 环境×端 → CDN 完整加载路径
	     * PROD→{side}Release，非 PROD→{side}Staging
	     */
	    private static getCdnUrl;
	    /**
	     * 动态加载 UMD 脚本并创建实例
	     * 任何失败 resolve(null)，由调用方回退旧链路
	     */
	    load(): Promise<IRechargeSDKInstance>;
	    /**
	     * 以当前玩家数据实例化 SDK（scene='game' 游戏内场景）
	     */
	    private createInstance;
	    /** 组装 SDK 用户基础信息 */
	    private buildInfo;
	    /**
	     * 计算充值弹窗尺寸：运行时读取当前游戏显示视口（iframe 场景即游戏显示区域真实像素）
	     * 按 90% 自适应，并按面板移动端设计基准封顶（420×640），防大屏拉伸
	     */
	    private calcPopupSize;
	    /**
	     * 打开充值弹窗（全端入口）
	     * 每次打开前同步最新 token/用户信息；返回 false 表示 SDK 不可用，需回退
	     */
	    openDeposit(): Promise<boolean>;
	    /**
	     * 统一事件桥接
	     * track → GA 埋点透传；navigate → 页面跳转；close → 刷新余额
	     */
	    private onEventBridge;
	    /** 关闭充值面板后刷新主钱包余额并联动 UI */
	    private refreshBalance;
	}
	
	/**
	 * 充值
	 */
	export class AccountTransferWindow extends gameLib.BaseWindow {
	    static depositCode: number;
	    static depositGameName: string;
	    /** 充值按钮 */
	    protected topUpBtn: fgui.GButton;
	    /** 输入框 */
	    protected transferInput: fgui.GTextInput;
	    protected depositList: fgui.GList;
	    /**
	     * 原始字体大小
	     */
	    protected inputFontSize: number;
	    /**
	     * 原始提示文案
	     */
	    protected inputPromptText: string;
	    protected clearBtn: fgui.GButton;
	    protected topMoney: number[];
	    constructor();
	    /**
	     * 第三方充值 SDK 面板优先；SDK 加载失败或不可用时回退旧充值弹窗
	     */
	    show(): void;
	    protected onInit(): void;
	    protected doShowAnimation(): void;
	    protected refresh(): void;
	    protected onFocus(): void;
	    protected onChange(): void;
	    protected contentEmpty(): void;
	    protected contentNotEmpty(): void;
	    protected topUpHandler(): void;
	    protected storeHandler(obj: HttpResponse): void;
	    protected storeErrorHandler(): void;
	    protected onClearInput(): void;
	    protected depositHandler(item: fgui.GButton): void;
	}
	
	export class ActivityButton extends tsCore.EButton {
	    protected tempValue: number;
	    protected clickInvalid: boolean;
	    callback: ParamHandler;
	    protected contentText: fgui.GTextField;
	    /** 当没有优惠卷使用的时候 是否自动隐藏 */
	    isAutoHide: boolean;
	    /** 自定义更新文字显示 */
	    updateText: ParamHandler;
	    protected stateController: fgui.Controller;
	    protected onConstruct(): void;
	    protected stopUseActivityHandler(): void;
	    protected useActivityHandler(): void;
	    protected onUpdateState(): void;
	    /**
	     * 设置角标
	     * @param value 剩余数量
	     */
	    setCorner(value: number): void;
	    protected clickHandler(): void;
	    protected addedHandler(): void;
	    /** 打开拖动 */
	    openDrag(): void;
	    protected onDragEnd(): void;
	    protected onDragStart(): void;
	}
	
	export class ActivityHelpItem extends tsCore.ELabel {
	    private loadController;
	    private iconloader;
	    protected onInit(): void;
	    set icon(value: string);
	    private loadHandler;
	}
	
	export class ActivityHelpView extends gameLib.BaseView {
	    private contentList;
	    private static RES;
	    private static RESV;
	    private ress;
	    private clickBtn;
	    /** 是否是横屏 */
	    isHorizontal: boolean;
	    private okUi;
	    private nextUi;
	    constructor();
	    protected onInit(): void;
	    private showHandler;
	    private clickHandler;
	    private itemRenderHandler;
	    dispose(): void;
	}
	
	/**
	 * 弹窗层
	 * @author boge
	 */
	export class AlertPanel extends fgui.GComponent {
	    private static _instance;
	    static get inst(): AlertPanel;
	    constructor();
	    protected createDisplayObject(): void;
	    private __winResize;
	}
	
	import type { PlayConfigLogic } from "./PlayConfigLogic";
	import type { IPlayExecHost } from "../interface/IPlayExecHost";
	/**
	 * 玩法执行视图（执行玩法）
	 *
	 * 由 {@link BottomView} 拆分而来，负责玩法行为的执行与操作：
	 * spin、autospin、gift（优惠券）、turbo（快速模式）。
	 *
	 * 本类为逻辑模块（非 BaseView 子类），由 BottomView 持有并委托调用。
	 * 所有 UI 成员仍保留在 BottomView 上（保证 20+ 游戏子类 this.xxx 直接访问的兼容性），
	 * 通过 host 引用进行操作。
	 */
	export class PlayExecLogic<T extends gameLib.BaseGameData = gameLib.BaseGameData> {
	    /** 宿主视图 */
	    host: IPlayExecHost<T>;
	    /** 配置玩法模块（互相引用） */
	    playConfig: PlayConfigLogic<T>;
	    constructor(host: IPlayExecHost<T>);
	    /**
	     * 初始化自动旋转次数菜单（原 onInit 的 createPopupMnu 块）
	     */
	    initPopupMenu(): void;
	    /**
	     * spin 按钮数据初始化（原 initData 的 spin 部分）
	     */
	    initSpinData(): void;
	    /**
	     * 延迟初始化 turbo 按钮（原 onLaterInit）
	     */
	    onLaterInit(): void;
	    onTurbo(): void;
	    onMenuItemClick(value: number): void;
	    /**
	     * 检验当前的额度是否满足优惠卷要求
	     * 没有自动游戏
	     * 不是slot游戏或没有特殊玩法
	     * 没有使用卷的时候
	     * 进行可用卷判断
	     */
	    onUpdateAutoUseCoupon(): void;
	    onStopUseActivity(): void;
	    onUseActivity(): void;
	    /**
	     * 更新自动开始次数
	     */
	    onUpdateAutoCount(): void;
	    /** 自动开始 */
	    onAutoSpin(): void;
	    onStopAutoSpin(): void;
	    onAutoSpinClick(): void;
	    onSpinClick(): void;
	    /**
	     * 更改开始按钮
	     * @param value 0 spin 1 stop
	     */
	    onChangeSpinText(value: number): void;
	    /**
	     * 运行 spin 事件动作前的逻辑处理
	     */
	    runSpinBefore(): void;
	    /** 改变所有按钮的状态 */
	    onAllBtnChangeState(value: boolean): void;
	    /**
	     * 初始化活动卷
	     * @param isOpenDrag 是否开启拖动(默认true)
	     * @param isAutoHide 当没有优惠卷使用的时候 是否自动隐藏(默认true)
	     * @param autoUpdate 是否要自定义显示卷信息(默认false)
	     */
	    initCouponsMenu(isOpenDrag?: boolean, isAutoHide?: boolean, autoUpdate?: boolean): void;
	    /**
	     * 自定义劵的文字显示
	     * @param coupons
	     */
	    onUpdateCouponsText(coupons: Coupons): void;
	    /** 更新中优惠券数量 */
	    updateTotalCoupons(): void;
	    activityHandler(): void;
	    /**
	     * 显示提示文本
	     */
	    onPromptCanUseCoupons(): void;
	    startGame(): void;
	    getSpin(): fgui.GButton;
	}
	
	import type { PlayExecLogic } from "./PlayExecLogic";
	import type { IPlayConfigHost } from "../interface/IPlayConfigHost";
	/**
	 * 玩法配置视图（调整玩法）
	 *
	 * 由 {@link BottomView} 拆分而来，负责玩法参数的配置与调节：
	 * bet line 切换、stake 显示、win 显示。
	 *
	 * 本类为逻辑模块（非 BaseView 子类），由 BottomView 持有并委托调用。
	 * 所有 UI 成员仍保留在 BottomView 上（保证 20+ 游戏子类 this.xxx 直接访问的兼容性），
	 * 通过 host 引用进行操作。
	 */
	export class PlayConfigLogic<T extends gameLib.BaseGameData = gameLib.BaseGameData> {
	    /** 宿主视图 */
	    host: IPlayConfigHost<T>;
	    /** 执行玩法模块（互相引用） */
	    playExec: PlayExecLogic<T>;
	    constructor(host: IPlayConfigHost<T>);
	    /**
	     * 投注数据初始化（原 BottomView.initData 的 bet 部分）
	     * 设置默认 bet/line 索引并刷新总投注
	     */
	    initBetData(): void;
	    /**
	     * 初始化默认投注
	     * 含 freeBet 处理与抵用券自动选择
	     */
	    initDefaultBet(): void;
	    /** 计算最小bet数据 */
	    get minBet(): {
	        index: number;
	        value: any;
	    };
	    onLinesChangeBefore(value: number): boolean;
	    onBetChangeBefore(value: number): boolean;
	    onBetChange(value: number): void;
	    onLines(value: number): void;
	    /** 更新总下注资金 */
	    onUpdateTotalBet(): void;
	    onSetBtn(index: number): void;
	    /**
	     * 重置投注（winText 归零 + 恢复按钮状态）
	     */
	    onResetBet(): void;
	    /** 更新总共赢钱 */
	    onUpdateWin(): void;
	    getWinText(): fgui.GTextField;
	    /**
	     * 新一局开始时重置 bet（原 onNewRoundStart 的 bet 重置部分）
	     */
	    onNewRoundStartResetBet(): void;
	}
	
	export class StakeView extends tsCore.ELabel {
	    bg: fgui.GLoader;
	    /**
	     * 标题文本
	     */
	    titleObject: fgui.GTextField;
	    rightBtn: fgui.GButton;
	    leftBtn: fgui.GButton;
	    private valueSwitcher;
	    protected onConstruct(): void;
	    /**
	     * 获取一个 ValueSwitcher
	     * 自动开启了长按功能
	     */
	    get asValueSwitcher(): tsCore.ValueSwitcher<any>;
	    set text(value: string);
	    get text(): string;
	}
	
	/**
	 * 底部操作栏视图
	 *
	 * 本类作为兼容门面，保留全部 UI 成员与方法签名（供 20+ 游戏子类 this.xxx 直接访问与 override）。
	 * 具体逻辑已按职责拆分委托至两个模块：
	 * - {@link PlayConfigLogic} 调整玩法：bet line 切换、stake/win 显示
	 * - {@link PlayExecLogic} 执行玩法：spin、autospin、gift、turbo
	 *
	 * 两个模块在 onInit 中创建并互相注入引用，本类方法体仅做转发委托。
	 */
	export class BottomView<T extends gameLib.BaseGameData = gameLib.BaseGameData> extends gameLib.BaseView {
	    /** 玩法配置模块（调整玩法） */
	    playConfigLogic: PlayConfigLogic<T>;
	    /** 玩法执行模块（执行玩法） */
	    playExecLogic: PlayExecLogic<T>;
	    /** 当前游戏的活动按钮 */
	    couponsBtn?: gameLib.ActivityButton;
	    /** 自动开始 */
	    autoSpinBtn?: fgui.GButton;
	    spinBtn: fgui.GButton;
	    /** 总投注 */
	    totalBetText?: fgui.GRichTextField;
	    winText?: fgui.GTextField;
	    /** win图标 */
	    winIcon?: fgui.GLoader;
	    linesChange?: tsCore.ValueSwitcher<number>;
	    betChange?: tsCore.ValueSwitcher<number>;
	    gameData: T;
	    /** sounds/${Player.inst.simpleName}/${this.spinSound} 只需填写音乐名字即可 */
	    spinSound?: string;
	    promptTip: gameLib.PromptTip;
	    /** 总共线数 */
	    lineNum: number;
	    /** 缓存字体大小 */
	    stakeSize: number;
	    betCountPopupMenu: tsCore.GamePopupMenu;
	    winColorController: fgui.Controller;
	    /**
	     * 快速模式的控制器
	     */
	    turboController?: fgui.Controller;
	    /** 自动旋转状态控制器 */
	    autoSpinController?: fgui.Controller;
	    /** 快速模式按钮 */
	    turboBtn: fgui.GButton;
	    spinStatusController: fgui.Controller;
	    /**
	     * 是否创建自动菜单
	     */
	    createPopupMnu: boolean;
	    /**
	     * 所有按钮的启用状态
	     */
	    allBtnState: boolean;
	    lineStake?: StakeView;
	    betStake?: StakeView;
	    stakeTitle: fgui.GTextField;
	    AUTO_SPIN_UNTIL_STOP: string;
	    AUTO_SPIN_AUTO_SPINS: string;
	    AUTO_SPIN_AUTO_SPIN: string;
	    constructor();
	    protected onInit(): void;
	    /**
	     * 延迟初始化 不分组件
	     */
	    protected onLaterInit(): void;
	    protected callInit(spinView: fgui.GLoader): void;
	    protected onTurbo(): void;
	    protected onMenuItemClick(value: number): void;
	    protected onNewRoundStart(): void;
	    /**
	     * 检验当前的额度是否满足优惠卷要求
	     * 没有自动游戏
	     * 不是slot游戏或没有特殊玩法
	     * 没有使用卷的时候
	     * 进行可用卷判断
	     */
	    onUpdateAutoUseCoupon(): void;
	    protected onSetBtn(index: number): void;
	    protected onResetBet(): void;
	    protected initData(): void;
	    /** 计算最小bet数据 */
	    protected get minBet(): {
	        index: number;
	        value: any;
	    };
	    protected onLinesChangeBefore(value: number): boolean;
	    protected onBetChangeBefore(value: number): boolean;
	    protected onBetChange(value: number): void;
	    protected onLines(value: number): void;
	    protected onStopUseActivity(): void;
	    protected onUseActivity(): void;
	    /**
	     * 更新自动开始次数
	     */
	    protected onUpdateAutoCount(): void;
	    onSizeChange(): void;
	    protected addedHandler(): void;
	    protected onStopAutoSpin(): void;
	    /** 自动开始 */
	    protected onAutoSpin(): void;
	    startGame(): void;
	    /** 更新总下注资金 */
	    protected onUpdateTotalBet(): void;
	    updateSpinText(money: number): void;
	    /**
	     * 更改开始按钮
	     * @param value 0 spin 1 stop
	     */
	    protected onChangeSpinText(value: number): void;
	    /**
	     * 运行 spin 事件动作前的逻辑处理
	     * @protected
	     */
	    protected runSpinBefore(): void;
	    protected onSpin(): void;
	    protected onSpinClick(): void;
	    protected onAutoSpinClick(): void;
	    /** 改变所有按钮的状态 */
	    protected onAllBtnChangeState(value: boolean): void;
	    /** 更新总共赢钱 */
	    protected onUpdateWin(): void;
	    getWinText(): fgui.GTextField;
	    getSpin(): fgui.GButton;
	    /**
	     * 初始化活动卷
	     * @param isOpenDrag 是否开启拖动(默认true)
	     * @param isAutoHide 当没有优惠卷使用的时候 是否自动隐藏(默认true)
	     * @param autoUpdate 是否要自定义显示卷信息(默认false)
	     */
	    protected initCouponsMenu(isOpenDrag?: boolean, isAutoHide?: boolean, autoUpdate?: boolean): void;
	    /**
	     * 自定义劵的文字显示
	     * @param coupons
	     * @protected
	     */
	    protected onUpdateCouponsText(coupons: Coupons): void;
	    /** 更新中优惠券数量 */
	    protected updateTotalCoupons(): void;
	    protected activityHandler(): void;
	    getScene(): gameLib.BaseScene<gameLib.BaseGameData>;
	    /**
	     * 显示提示文本
	     */
	    protected onPromptCanUseCoupons(): void;
	    dispose(): void;
	}
	
	import type { PlayConfigView } from "./PlayConfigView";
	import type { IPlayExecHost } from "../interface/IPlayExecHost";
	/**
	 * 玩法执行视图（执行玩法）
	 *
	 * 独立可绑定 BaseView，负责玩法行为的执行与操作：spin、autospin、gift（优惠券）、turbo（快速模式）。
	 * 由 BottomView 拆分而来，可被 FGUI 编辑器绑定扩展类，运行时 getChild 自动实例化。
	 *
	 * 跨类依赖（betChange/linesChange 在配置视图上）通过 playConfigView 引用访问，由场景注入。
	 * onInit 在构造时立即执行，此时 playConfigView 可能尚未注入，涉及跨类赋值处做空值容错。
	 */
	export class PlayExecView<T extends gameLib.BaseGameData = gameLib.BaseGameData> extends gameLib.BaseView implements IPlayExecHost<T> {
	    /** 核心逻辑模块 */
	    logic: PlayExecLogic<T>;
	    /** 配置玩法视图（跨引用，场景注入） */
	    playConfigView?: PlayConfigView<T>;
	    /** 当前游戏数据 */
	    gameData: T;
	    /** 总共线数 */
	    lineNum: number;
	    spinBtn: fgui.GButton;
	    /** 自动开始 */
	    autoSpinBtn?: fgui.GButton;
	    /** 当前游戏的活动按钮 */
	    couponsBtn?: gameLib.ActivityButton;
	    spinStatusController: fgui.Controller;
	    betCountPopupMenu: tsCore.GamePopupMenu;
	    /** 快速模式的控制器 */
	    turboController?: fgui.Controller;
	    /** 自动旋转状态控制器 */
	    autoSpinController?: fgui.Controller;
	    /** 快速模式按钮 */
	    turboBtn?: fgui.GButton;
	    /** sounds/${Player.inst.simpleName}/${this.spinSound} 只需填写音乐名字即可 */
	    spinSound?: string;
	    promptTip: gameLib.PromptTip;
	    /** 是否创建自动菜单 */
	    createPopupMnu: boolean;
	    /** 所有按钮的启用状态 */
	    allBtnState: boolean;
	    AUTO_SPIN_UNTIL_STOP: string;
	    AUTO_SPIN_AUTO_SPINS: string;
	    AUTO_SPIN_AUTO_SPIN: string;
	    /** IPlayExecHost 接口实现：跨类代理 bet 调节组件 */
	    get betChange(): tsCore.ValueSwitcher<number> | undefined;
	    /** IPlayExecHost 接口实现：跨类代理 lines 调节组件 */
	    get linesChange(): tsCore.ValueSwitcher<number> | undefined;
	    constructor();
	    protected onInit(): void;
	    /**
	     * 延迟初始化 spin 按钮（GLoader 延迟实例化场景）
	     */
	    protected callInit(spinView: fgui.GLoader): void;
	    protected addedHandler(): void;
	    /** 逻辑方法全权委托 Logic */
	    initPopupMenu(): void;
	    initSpinData(): void;
	    onLaterInit(): void;
	    onTurbo(): void;
	    onMenuItemClick(value: number): void;
	    onUpdateAutoUseCoupon(): void;
	    onStopUseActivity(): void;
	    onUseActivity(): void;
	    onUpdateAutoCount(): void;
	    onAutoSpin(): void;
	    onStopAutoSpin(): void;
	    onAutoSpinClick(): void;
	    onSpinClick(): void;
	    onChangeSpinText(value: number): void;
	    runSpinBefore(): void;
	    onAllBtnChangeState(value: boolean): void;
	    initCouponsMenu(isOpenDrag?: boolean, isAutoHide?: boolean, autoUpdate?: boolean): void;
	    onUpdateCouponsText(coupons: Coupons): void;
	    updateTotalCoupons(): void;
	    activityHandler(): void;
	    onPromptCanUseCoupons(): void;
	    startGame(): void;
	    getSpin(): fgui.GButton;
	    /**
	     * spin 事件（子类 override 实现具体旋转逻辑）
	     */
	    onSpin(): void;
	}
	
	import type { PlayExecView } from "./PlayExecView";
	import type { IPlayConfigHost } from "../interface/IPlayConfigHost";
	/**
	 * 玩法配置视图（调整玩法）
	 *
	 * 独立可绑定 BaseView，负责玩法参数的配置与调节：bet line 切换、stake 显示、win 显示。
	 * 由 BottomView 拆分而来，可被 FGUI 编辑器绑定扩展类，运行时 getChild 自动实例化。
	 *
	 * 跨类依赖（spinBtn 在执行视图上）通过 playExecView 引用访问，由场景注入。
	 * onInit 在构造时立即执行，此时 playExecView 可能尚未注入，onUpdateTotalBet 对其做空值容错，
	 * 场景注入后需触发 onUpdateTotalBet 刷新 spinBtn 文案。
	 */
	export class PlayConfigView<T extends gameLib.BaseGameData = gameLib.BaseGameData> extends gameLib.BaseView implements IPlayConfigHost<T> {
	    /** 核心逻辑模块 */
	    logic: PlayConfigLogic<T>;
	    /** 执行玩法视图（跨引用，场景注入） */
	    playExecView?: PlayExecView<T>;
	    winColorController: fgui.Controller;
	    /** 当前游戏数据 */
	    gameData: T;
	    /** 总共线数 */
	    lineNum: number;
	    linesChange?: tsCore.ValueSwitcher<number>;
	    betChange?: tsCore.ValueSwitcher<number>;
	    /** 总投注 */
	    totalBetText?: fgui.GRichTextField;
	    winText?: fgui.GTextField;
	    /** win图标 */
	    winIcon?: fgui.GLoader;
	    lineStake?: StakeView;
	    betStake?: StakeView;
	    stakeTitle: fgui.GTextField;
	    /** 缓存字体大小 */
	    stakeSize: number;
	    /** IPlayConfigHost 接口实现：跨类代理 spin 按钮 */
	    get spinBtn(): fgui.GButton | undefined;
	    constructor();
	    protected onInit(): void;
	    protected addedHandler(): void;
	    /** 逻辑方法全权委托 Logic */
	    initBetData(): void;
	    initDefaultBet(): void;
	    get minBet(): {
	        index: number;
	        value: any;
	    };
	    onLinesChangeBefore(value: number): boolean;
	    onBetChangeBefore(value: number): boolean;
	    onBetChange(value: number): void;
	    onLines(value: number): void;
	    onUpdateTotalBet(): void;
	    onSetBtn(index: number): void;
	    onResetBet(): void;
	    onUpdateWin(): void;
	    getWinText(): fgui.GTextField;
	    onNewRoundStartResetBet(): void;
	    updateSpinText(money: number): void;
	}
	
	export class TopMenuPopup extends gameLib.BaseView {
	    /**
	     * 偏移Y坐标
	     */
	    offsetY: number;
	    protected onInit(): void;
	    setXY(xv: number, yv: number): void;
	}
	
	export class TopView<T extends gameLib.BaseGameData = gameLib.BaseGameData> extends tsCore.ELabel {
	    protected gameData: T;
	    protected balanceText: fgui.GTextField;
	    backBtn: fgui.GButton;
	    menuBtn?: TopComboBox;
	    depositBtn?: fgui.GButton;
	    protected list: fgui.GList;
	    static bgMusicVolume: number;
	    /**
	     * 音乐地址
	     */
	    musicUrl: string;
	    /**
	     * 特殊场景使用的音乐地址
	     */
	    specialUrl: string;
	    /**
	     * 收取金币音效地址
	     */
	    collectUrl: string;
	    /**
	     * 收取金币动画ui  ui://gameCommon/MovieClip2
	     */
	    goldAniUI: string;
	    goldLaunch: gameLib.GoldLaunch;
	    /** @deprecated 请直接使用 FxAni 或 CoinAni，goldAniUtils 后续在库中将直接删除 */
	    get goldAniUtils(): any;
	    constructor();
	    /**
	     * 特殊模式中  回收金币收到winText处
	     */
	    specialModelCollectToWinText: boolean;
	    protected onInit(): void;
	    protected onBack(): void;
	    protected onAllBtnChangeState(value: boolean): void;
	    protected onOpenGiftAni(): void;
	    protected onGiftEnd(tf: fgui.GTextField): void;
	    /**
	     * 文字收集飞行动画到达目标点完成后的虚方法（子类可直接 override）
	     * @param target 目标文本组件（如底部 winText 或顶部 balanceText）
	     * @param winText 飞行的源文本组件
	     * @protected
	     */
	    protected onTextCollectEnd(target?: fgui.GTextField, winText?: fgui.GTextField): void;
	    /**
	     *
	     * @param winText 金币动画起始位置， 如果是bigAward可以是null将启用中心点
	     * @param [isLottery=false] 是否更新活动奖池数据
	     * @param [bigAward=false] 是否播放大奖动画
	     * @param [onCollectEnd] 动画到达目标点完成后的回调函数（用于跌落完成后的缩放回弹、数字翻牌等）
	     * @protected
	     */
	    /**
	     * 获取当前场景中的底部 WinText 文本对象（用于飞行动效锚点/落点）
	     */
	    protected getBottomWinText(): fgui.GTextField | null;
	    protected onPlayCollectGoldCoinsAni(winText?: fgui.GTextField, isLottery?: boolean, bigAward?: boolean, onCollectEnd?: (target?: fgui.GTextField, winText?: fgui.GTextField) => void): void;
	    /**
	     * 外部直接调用收金币/收集文字流程（支持直接传递完成回调）
	     */
	    playCollectGoldCoins(winText?: fgui.GTextField, isLottery?: boolean, bigAward?: boolean, onCollectEnd?: (target?: fgui.GTextField, winText?: fgui.GTextField) => void): void;
	    protected playTextCollect(winText: fgui.GTextField, target: fgui.GTextField, isLottery?: boolean, onCollectEnd?: (target?: fgui.GTextField, winText?: fgui.GTextField) => void): void;
	    /**
	     * 播放金币动画结束
	     * @param tf 小奖动画移动的文本对象
	     * @param [isLottery=false] 是否更新活动奖池数据
	     * @protected
	     */
	    protected onAniEnd(tf: fgui.GTextField, isLottery?: boolean): void;
	    private onDeposit;
	    protected addedHandler(): void;
	    /**
	     * 检测刘海屏
	     * @protected
	     */
	    protected checkNotch(): void;
	    /**
	     * 更新当前的余额
	     * @protected
	     */
	    protected updateMoney(): void;
	    /** 文本显示的金币数量 */
	    get textMoney(): number;
	    /**
	     * 在本金的基础上修改,获取当前显示的金额 直接+=传入值
	     */
	    plusMoney(value: number): void;
	    /** 设置显示金币值 */
	    set textMoney(value: number | string);
	    getTextMoney(): string;
	    /** 设置显示金币值 */
	    setTextMoney(value?: number | string): void;
	    /**
	     * 获取当前余额控件
	     */
	    getBalanceText(): fgui.GTextField;
	    protected onMenuClick(event: Laya.Event): void;
	    /**
	     * 播放背景音
	     * @param url 如果不存在 使用 musicUrl
	     * @param [volume=1]
	     * @protected
	     */
	    protected playBgMusic(url?: string, volume?: number): void;
	    dispose(): void;
	}
	export class TopComboBox extends tsCore.EComboBox {
	    protected closeSoundUrl: string;
	    protected topMenuBg: string;
	    constructor();
	    /**
	     * 初始化顶部下拉菜单项与资源配置（自动适配横版与返回按钮）
	     */
	    protected initTopMenuItems(): void;
	    protected showDropdown(): void;
	}
	
	/**
	 * 一个游戏舞台类
	 * @example
	 * BaseGameData = gameData
	 * TopView      = topBar
	 * BottomView   = bottom
	 * BaseSlotView = game
	 */
	export class GameScene<D extends gameLib.BaseGameData = gameLib.BaseGameData, T extends tsCore.ELabel = TopView, B extends gameLib.BaseView = BottomView, G extends gameLib.BaseView = gameLib.BaseSlotView> extends gameLib.BaseScene<D> {
	    protected _topView: T;
	    protected _bottomView: B;
	    protected _gameView: G;
	    protected _playConfigView: PlayConfigView;
	    protected _playExecView: PlayExecView;
	    protected debugText: fgui.GTextField;
	    protected onInit(): void;
	    private onDebugInfo;
	    protected addedHandler(): void;
	    /**
	     * 押注还原(用于押注失败,退还所有的押注)
	     * ```
	     * ● ActionLib.GAME_ALL_BTN_CHANGE_STATE       true
	     * ● ActionLib.GAME_UPDATE_MONEY
	     * ```
	     */
	    resetBet(): void;
	    protected onChangeAllBtnState(value: boolean): void;
	    /** 提示用户使用优惠券 */
	    protected onPromptCanUseActivity(): void;
	    /**
	     * @deprecated
	     */
	    protected initActivityMenu(component: fgui.GComponent, isOpenDrag?: boolean, isAutoHide?: boolean): void;
	    /**
	     * ```
	     * 获取顶部视图
	     * 如果顶部视图已存在，则直接返回
	     * 否则，通过getChild("topBar")方法获取顶部视图，并将其赋值给this._topView
	     * 最后返回顶部视图
	     * ```
	     */
	    get topView(): T;
	    /**
	     * ```
	     * 获取底部视图
	     * 如果底部视图已存在，则直接返回
	     * 否则，通过getChild("bottom")方法获取底部视图并赋值给this._bottomView
	     * 最后返回底部视图
	     * ```
	     */
	    get bottomView(): B;
	    /**
	     * 获取玩法配置视图（新游戏模式，无 bottom 组件时使用）
	     * FGUI 组件名：playConfig，绑定 PlayConfigView
	     */
	    get playConfigView(): PlayConfigView | undefined;
	    /**
	     * 获取玩法执行视图（新游戏模式，无 bottom 组件时使用）
	     * FGUI 组件名：playExec，绑定 PlayExecView
	     */
	    get playExecView(): PlayExecView | undefined;
	    /**
	     * 初始化两个独立玩法视图的跨引用并刷新显示
	     * 新游戏 scene 的 onInit 中调用（子组件 onInit 先于 scene onInit，此时跨引用尚未注入）
	     */
	    initPlayViews(): void;
	    /**
	     * ```
	     * 获取游戏视图
	     * 如果游戏视图已存在，则直接返回
	     * 否则，通过getChild("game")方法获取游戏视图并赋值给this._gameView
	     * 最后返回游戏视图
	     * ```
	     */
	    get gameView(): G;
	}
	
	/**
	 * 防止沉迷提示
	 */
	export class AntiAddictionTips extends tsCore.ELabel {
	    private tips;
	    protected onInit(): void;
	    private onShow;
	    private onHide;
	}
	
	export class AutoSpinSetupWindow extends gameLib.BaseWindow {
	    private cancelBtn;
	    private list;
	    private okBtn;
	    constructor();
	    protected onInit(): void;
	    protected onHide(): void;
	    private onCancel;
	}
	
	export class BonusBtn extends tsCore.EButton {
	    protected progress: tsCore.ProgressBar;
	    protected numTip: fgui.GTextField;
	    /** 缓存上次获得的张数 */
	    protected tempJackpotCount: number;
	    protected skeleton: tsCore.GSkeleton | tsCore.GSpineSkeleton;
	    protected bgImg: fgui.GImage;
	    static bonusDir: string;
	    protected onInit(): void;
	    protected createSpine(): void;
	    protected onLoaderEnd(): void;
	    protected displayHandler(): void;
	    protected updateJackpotPool(): void;
	    dispose(): void;
	}
	
	export class BonusItem extends tsCore.ELabel {
	    itemClickHandler: (value: BonusItem) => void;
	    transition: fgui.Transition;
	    index: number;
	    /** 当前礼包的id */
	    giftId: string;
	    protected onInit(): void;
	    private clickHandler;
	}
	
	export class BonusRuleWindow extends gameLib.BaseWindow {
	    private contentText;
	    static createContent: () => fgui.GComponent;
	    constructor();
	    protected onInit(): void;
	    protected doShowAnimation(): void;
	}
	
	export class BonusWindow extends gameLib.BaseWindow {
	    protected progress: tsCore.ProgressBar;
	    /** 缓存上次获得的张数 */
	    protected tempJackpotCount: number;
	    protected bonusView: BonusView;
	    /**
	     * 创建游戏奖励窗口的内容
	     *
	     * 此静态方法通过GameConfigKit获取游戏名称，并将其用作UI资源路径的一部分
	     * 它不接受任何参数，并返回一个Component对象，该对象是游戏奖励窗口的UI内容
	     */
	    static createContent: () => fgui.GComponent;
	    static isNewBonusUI: () => boolean;
	    constructor();
	    protected onInit(): void;
	    protected onHelp(): void;
	    protected onShown(): void;
	    protected updateJackpotPool(): void;
	    protected showHandler(): void;
	    protected doShowAnimation(): void;
	    protected doHideAnimation(): void;
	    protected onHide(): void;
	    dispose(): void;
	}
	
	export class BonusView extends gameLib.BaseView {
	    private richText;
	    private list;
	    private controller;
	    /** 时候正在播放删除动画 */
	    private isAction;
	    /** 动画 */
	    private tweens;
	    /** 动画结束次数 */
	    private endCount;
	    private gameData;
	    constructor();
	    protected onInit(): void;
	    /**
	     * 新界面打开礼包
	     */
	    openBonus(): void;
	    private displayHandler;
	    private itemRenderHandler;
	    private itemClickHandler;
	    /**
	     * 显示中奖界面
	     * @param item 要删除的项
	     * @param win 赢的钱
	     * @param remove 是否删除
	     */
	    private openWinHandler;
	    /**
	     * 删除项
	     * @param item 要删除的项
	     */
	    private removeItemHandler;
	    private moveEndHandler;
	    updateList(): void;
	    private onUpdateTime;
	    dispose(): void;
	}
	
	export class BonusWinWindow extends gameLib.BaseWindow {
	    protected moneyValue: fgui.GTextField;
	    protected handler: Laya.Handler;
	    protected win: number;
	    protected transition: fgui.Transition;
	    protected energyTitle: fgui.GTextField;
	    protected energyValue: fgui.GTextField;
	    protected energy: number;
	    protected controller: fgui.Controller;
	    static createContent: () => fgui.GComponent;
	    constructor();
	    protected showTip(win: number, handler: Laya.Handler): void;
	    protected onInit(): void;
	    protected onAccount(): void;
	    protected onConfirm(): void;
	    protected onShown(): void;
	    protected doShowAnimation(): void;
	    protected doHideAnimation(): void;
	    dispose(): void;
	    protected onHide(): void;
	}
	
	/** 表情框 */
	export class FaceWindow extends fgui.Window implements tsCore.IRecord {
	    private static _instance;
	    static get instance(): FaceWindow;
	    private list;
	    private func;
	    protected onInit(): void;
	    private onData;
	    private clickHandler;
	    private clickItemHandler;
	    showTip(func: Function, posY?: number): void;
	    hideRecord(): void;
	    showRecord(): void;
	}
	
	/** 聊天窗口 */
	export class ChatWindow extends fgui.Window {
	    /** 关闭按钮 */
	    private closeBtn;
	    /** 发送数据按钮 */
	    private sendDataBtn;
	    /** 打开按钮 */
	    private openBtn;
	    /** 表情按钮 */
	    private faceBtn;
	    /** 输入框 */
	    private inputChat;
	    /** 聊天内容显示区 */
	    private chatContentPanel;
	    /** 聊天内容框 */
	    private chatContent;
	    /** 聊天消息缓存 */
	    private chatMsgs;
	    private readonly comp;
	    private gameScene;
	    constructor(comp: fgui.GComponent, gameScene: gameLib.BaseScene);
	    protected onInit(): void;
	    setVi(): void;
	    /**
	     * 打开当前聊天界面
	     *
	     */
	    openHandler(): void;
	    private closeHandler;
	    protected doShowAnimation(): void;
	    /** 表情管理 */
	    private faceHandler;
	    private clickIcon;
	    /** 发送消息 */
	    private sendMssage;
	    /** 添加新聊天信息 */
	    addChat(obj: any): void;
	    /** 清理内容 */
	    clear(): void;
	}
	
	export class ClearDrag extends fgui.GButton {
	    private static _inst;
	    static get inst(): ClearDrag;
	    private loader;
	    private clickInvalid;
	    private callback;
	    protected constructFromXML(xml: any): void;
	    private clickHandler;
	    private onDragEnd;
	    private onDragStart;
	    show(pa: fgui.GComponent, callback: Laya.Handler): void;
	    hide(): void;
	}
	
	export class CompleteBetWindow extends gameLib.BaseWindow {
	    private url;
	    private handler;
	    private controller;
	    private typeController;
	    constructor(url?: string);
	    protected onInit(): void;
	    private onClose;
	    private showTip;
	    private onTypeStateChanged;
	    private onStateChanged;
	}
	
	export class CouponChangeBetWindow extends gameLib.BaseWindow {
	    private static _instance;
	    static get inst(): CouponChangeBetWindow;
	    private content;
	    /** 确定取消 */
	    private cancelBtn;
	    /** 确定 */
	    private continueBtn;
	    private continueFun;
	    private callback;
	    constructor();
	    protected onInit(): void;
	    private continueHandler;
	    private cancelHandler;
	    /**
	     * 带确认按钮的提示框
	     * @param msg 显示提示 参数多个类型:string-直接显示文本 、int-从语言包里面操作文本、array-带替换内容 [int|string, ...string]
	     * @param callback 确定回调方法
	     * @param isAction 动画显示或关闭
	     *
	     * @see LibStr
	     * @see ActionLib.GAME_SHOW_PROMPT_WINDOW
	     */
	    showTip(msg: string | number | any[] | PromptData, callback?: ParamHandler, isAction?: boolean): void;
	    private _show;
	    protected onHide(): void;
	    /** 结束回调 */
	    endCallHandler(): void;
	    /**
	     * 判断是否是接口 用 prototype 是否存在判断
	     * @param optional
	     */
	    isPromptData(optional: any): optional is PromptData;
	}
	
	export class CouponHelpWindow extends gameLib.BaseView {
	    protected contentList: fgui.GList;
	    protected ress: string[];
	    protected clickBtn: fgui.GButton;
	    protected okUi: string;
	    protected nextUi: string;
	    protected onInit(): void;
	    protected showHandler(): void;
	    protected updateBtnText(value: string): void;
	    protected clickHandler(): void;
	    protected itemRenderHandler(index: number, item: fgui.GLabel): void;
	    dispose(): void;
	}
	
	/** 活动弹窗 */
	export class CouponWindow extends gameLib.BaseWindow {
	    /** 当前拥有的优惠卷 */
	    protected list: fgui.GList;
	    /** 操作按钮 */
	    protected btn: fgui.GButton;
	    /** 当前展现的优惠卷 */
	    protected coupons: Coupons[];
	    /** 当前被选中的项 */
	    protected selectItem: Coupons;
	    /** 提示文本 */
	    protected tipMsgText: fgui.GTextField;
	    /** 创建窗体ui */
	    protected uiPath: string;
	    /** 主题类才可以使用 */
	    protected isRegAction: boolean;
	    protected defaultSelectIndex: number;
	    /**
	     * 只有大于这个最小数量  才算是有劵
	     * @protected
	     */
	    protected couponMinLen: number;
	    protected typeController: fgui.Controller;
	    /**
	     * 构造方法
	     * @param isRegAction 是否要注入监听事件
	     * @param uiPath
	     */
	    constructor(isRegAction?: boolean, uiPath?: string);
	    protected showTip(startPoint?: Laya.Point): void;
	    protected initCoupon(): void;
	    protected onSort(obj1: Coupons, obj2: Coupons): number;
	    protected onInit(): void;
	    protected clickHelpHandler(): void;
	    protected clickItemHandler(button: fgui.GButton): void;
	    protected showCouponChangeBetTip(needBet: number, config: {
	        index: number;
	        value: any;
	    }, tempSelectItem: Coupons): void;
	    /** 取消选中 */
	    protected cancelSelect(): void;
	    protected itemRenderHandler(index: number, item: fgui.GButton): void;
	    protected doShowAnimation(): void;
	    protected refreshCouponList(): void;
	    protected updateState(): void;
	    protected useHandler(): void;
	    protected doHideAnimation(): void;
	    protected onHide(): void;
	}
	
	export class DiscountText extends tsCore.ELabel {
	    private price;
	    private price2;
	    protected onInit(): void;
	    /** 设置字体向右对齐 */
	    setAlignRight(): void;
	    /** 设置未打折的投注额 */
	    setPrice(value: number): void;
	    /** 设置未打折的投注额颜色 */
	    setPriceColor(value: string): void;
	    set text(value: string);
	    set color(value: string);
	}
	
	/** 下载提示 */
	export class DownloadWindow extends gameLib.BaseWindow {
	    private handler;
	    private content;
	    constructor();
	    protected onInit(): void;
	    private notNowHandler;
	    private installHandler;
	    showTip(handler: Laya.Handler): void;
	}
	
	export class ExchangeWindow extends gameLib.BaseWindow {
	    private static _instance;
	    static get inst(): ExchangeWindow;
	    private exchange;
	    private myMoney;
	    private slider;
	    private handler;
	    constructor();
	    protected onInit(): void;
	    private confirmHandler;
	    protected closeEventHandler(): void;
	    private inputHandler;
	    protected doShowAnimation(): void;
	    private stateChangedHandler;
	    private updateMoney;
	    private allHandler;
	    showTip(handler: Laya.Handler): void;
	}
	
	/**
	 * 引导页
	 */
	export class GuideView extends gameLib.BaseView {
	    private static _inst;
	    static get inst(): GuideView;
	    /** 执行完成调用 */
	    private callback;
	    /** 加载资源 */
	    private loadUrl;
	    /** 背景图片 */
	    private bgIconList;
	    /** 滚动图标 */
	    private ballList;
	    protected onInit(): void;
	    private scrollEndHandler;
	    private bgIconHandler;
	    private clickHandler;
	    private ballItemHandler;
	    showTip(urls: any[], callback?: Laya.Handler): void;
	    private progressComplete;
	    private loaderComplete;
	    close(): void;
	    hideRecord(): void;
	}
	
	/**
	 * 帮助文档
	 * @author boge
	 *
	 */
	export class HelpWindow extends gameLib.BaseWindow {
	    private static _inst;
	    static get inst(): HelpWindow;
	    private static _instH;
	    static get instH(): HelpWindow;
	    private content;
	    private url;
	    private readonly horizontal;
	    constructor(horizontal?: boolean);
	    protected onInit(): void;
	    private loadComplete;
	    protected doShowAnimation(): void;
	    protected doHideAnimation(): void;
	    showTip(url: string): void;
	    close(): void;
	    protected onShown(): void;
	}
	
	export class JackpotBtn extends tsCore.EButton {
	    private progress;
	    private numTip;
	    /** 缓存上次获得的张数 */
	    private tempJackpotCount;
	    protected onInit(): void;
	    private displayHandler;
	    private updateJackpotPool;
	    dispose(): void;
	}
	
	export class JackpotView extends gameLib.BaseView {
	    static NAME: string;
	    private readonly loaders;
	    private createTime;
	    private clickInvalid;
	    jackpotBtn: fgui.GButton;
	    /** 是否在播放动画 */
	    private isAction;
	    protected onInit(): void;
	    set enabled(value: boolean);
	    get enabled(): boolean;
	    private jackpotBonusAnimationHandler;
	    private closeHandler;
	    protected addedHandler(): void;
	    private clickHandler;
	    private updateHandler;
	    private createGold;
	    private onDragStart;
	    private onDragEnd;
	    dispose(): void;
	}
	
	export class JackpotWindow extends gameLib.BaseWindow {
	    private progress;
	    /** 缓存上次获得的张数 */
	    private tempJackpotCount;
	    /** 说明文本 */
	    private rulesCom;
	    private bonusView;
	    constructor();
	    protected onInit(): void;
	    protected onShown(): void;
	    private updateJackpotPool;
	    private showHandler;
	    protected doShowAnimation(): void;
	    protected doHideAnimation(): void;
	    protected onHide(): void;
	    dispose(): void;
	}
	
	export class JackpotWinWindow extends gameLib.BaseWindow {
	    private moneyValue;
	    private handler;
	    private win;
	    private giftCom;
	    private transition;
	    private giftTransition;
	    private energy;
	    constructor();
	    private showTip;
	    protected onInit(): void;
	    protected onShown(): void;
	    private onPlayOpen;
	    private onOpenEnd;
	    protected doShowAnimation(): void;
	    protected doHideAnimation(): void;
	    dispose(): void;
	    protected onHide(): void;
	}
	
	export class Notice extends gameLib.BaseView {
	    private msgContentText;
	    congratulationsText: Laya.HTMLDivElement;
	    /** 是否正在运行通告 */
	    private _isRun;
	    /** 是否使用缓存 */
	    private cacheMsg;
	    /** 背景色 */
	    private graph;
	    /** 保存初始字体大小 */
	    private fontSize;
	    /** 当前舞台宽度 */
	    private tempMainWidth;
	    /** 当前舞台高度 */
	    private tempMainHeight;
	    protected onInit(): void;
	    private showRoomNoticeHandler;
	    private sizeChangedHandler;
	    set text(value: string);
	    /**
	     * 添加公告
	     * @param value 公告内容
	     * @param isFirst 是否优选
	     *
	     */
	    setNoticeMsg(value: string, isFirst?: boolean): void;
	    private moveWidthHandler;
	    /**
	     * 是否使用缓存   使用后  如果当前公告还在滚动  那么新的公告信息会放入缓存中
	     * @param value 是否缓存
	     *
	     */
	    set isCache(value: boolean);
	    /** 清理并结束当前的通告信息 */
	    clear(): void;
	    /** 清理资源 */
	    clearRes(): void;
	    /** 是否正在运行通告 */
	    isRun(): boolean;
	}
	
	/**
	 * 钱不够  提现充值
	 */
	export class RemindTransferWindow extends gameLib.BaseWindow {
	    private static _instance;
	    static get inst(): RemindTransferWindow;
	    private msgContent;
	    private submitBtn;
	    private isSubmit;
	    private controller;
	    private controllerIndex;
	    protected onInit(): void;
	    private submitHandler;
	    protected onHide(): void;
	    protected doShowAnimation(): void;
	    /**
	     *
	     * @param index
	     */
	    showTip(index: number): void;
	    show(): void;
	}
	
	/**
	 * 滚动panel
	 * @author boge
	 *
	 */
	export class RollPanel extends fgui.GComponent {
	    private list;
	    private datas;
	    /** 缓存id */
	    private cache;
	    /** 滚动结束 */
	    private scrollEnd;
	    protected constructFromXML(xml: any): void;
	    changeValue(value: number): void;
	    private scrollHandler;
	    private itemHandler;
	    /**
	     * 停止所有动效
	     */
	    stop(): void;
	}
	
	/**
	 * 房间选项
	 */
	export class RoomSlots extends gameLib.BaseWindow {
	    /** 进入低倍场 */
	    private lowBtn;
	    /** 进入高倍场 */
	    private highBtn;
	    constructor();
	    protected onInit(): void;
	    private highHandler;
	    private lowHandler;
	    closeWindow(): void;
	    hideRecord(): void;
	    showTip(): void;
	}
	
	export class SlotSpineItem extends gameLib.BaseSlotItem {
	    /**
	     * 元素id
	     * @private
	     */
	    iconId: number;
	    /**
	     * 图标
	     * @private
	     */
	    protected iconObject: fgui.GLoader;
	    /**
	     * 默认ui动画
	     * @private
	     */
	    protected skeleton: tsCore.GSpineSkeleton | tsCore.GSkeleton;
	    /**
	     * win动画效果
	     * @private
	     */
	    protected skeletonAni: tsCore.GSpineSkeleton | tsCore.GSkeleton;
	    protected lightOrDarkController: fgui.Controller;
	    protected defaultExtension: string;
	    protected onInit(): void;
	    createIconSpine(url?: ISkeletonData, skeleton?: {
	        new (): (tsCore.GSkeleton | tsCore.GSpineSkeleton);
	    }): void;
	    createAniSpine(url?: ISkeletonData, skeleton?: {
	        new (): (tsCore.GSkeleton | tsCore.GSpineSkeleton);
	    }): void;
	    setData(index: number, data: any): void;
	    setIcon(value: number): void;
	    resetUI(): void;
	    showWin(): void;
	    protected playWinSpine(): void;
	    dark(): void;
	    /**
	     * 播放最后的跌落 动画
	     *
	     * 子类实现
	     * @param onComplete 完成回调
	     * @param times 播放次数
	     */
	    playLanding(onComplete?: ParamHandler, times?: number): void;
	    protected spineLoadComplete(): void;
	}
	
	/**
	 * 当未重写 initListHandler 方法
	 * 那么listItem 必须是SlotSpineItem
	 */
	export class SlotGameView<T extends gameLib.BaseSlotGameData = gameLib.BaseSlotGameData> extends gameLib.BaseSlotView<T> {
	    /**
	     * 显示文本文案信息
	     * name= msgInfo
	     */
	    msgInfo: fgui.GTextField;
	    WON_LINE: string;
	    /** 执行单个中奖线展示时间 默认2000ms */
	    autoNextTime: number;
	    /**
	     * bonus win 动画激活数量 默认3
	     */
	    bonusActivateNum: number;
	    protected onInit(): void;
	    protected addedHandler(): void;
	    /**
	     * 显示盈利界面 默认执行 showAllWinLine
	     */
	    protected onShowWin(): void;
	    /**
	     * 显示所有中奖线
	     * @param autoNext 自动下一步单独显示每条线 默认true
	     */
	    protected showAllWinLine(autoNext?: boolean): void;
	    protected showWinLineText(): void;
	    protected onShowDefaultInfo(): void;
	    protected onUpdateAutoSpinNumber(): void;
	    /**
	     * 设置信息文本
	     * @param value
	     */
	    setMsgInfo(value: string): void;
	    /**
	     *
	     */
	    protected onCloseAllAni(): void;
	    protected initListHandler(index: number, child: fgui.GList): void;
	    protected listItemHandler(index: number, item: SlotSpineItem): void;
	}
	
	export class SlotListItem extends gameLib.BaseView {
	    protected list: fgui.GList;
	    protected onInit(): void;
	    getList(): fgui.GList;
	}
	
	/**
	 * 一个Slot游戏舞台类
	 * @example
	 * TopView      = topBar
	 * BottomView   = bottom
	 * BaseSlotView = game
	 */
	export class SlotScene<D extends gameLib.BaseSlotGameData = gameLib.BaseSlotGameData, T extends tsCore.ELabel = TopView, B extends gameLib.BaseView = BottomView, G extends gameLib.BaseView = gameLib.BaseSlotView> extends GameScene<D, T, B, G> {
	    /**
	     * 第一次是否弹出 free spin 窗口
	     *
	     * 如为true 则调用 ActionLib.GAME_FREE_SPIN_START
	     * @default true
	     * @protected
	     */
	    protected isFreeShowFirstWindow: boolean;
	    /**
	     * 在startGame中是否自动更新余额
	     * @protected
	     */
	    protected isStartGameUpdateMoney: boolean;
	    startGame(): void;
	    /**
	     * 实现依次执行
	     * ```
	     * ● ActionLib.GAME_STOP_SLOT_LIST_RUN_ANI
	     * ● super.resetBet()
	     * ● 使用this.gameData.tempLotteryId的数据 重置滚动列表
	     * ● ActionLib.GAME_START
	     * ```
	     * @inheritDoc
	     */
	    resetBet(): void;
	    /**
	     * 会执行
	     * bottomView.startGame
	     * ActionLib.GAME_NEW_ROUND_START
	     */
	    protected onStartNewGame(): void;
	    protected addedHandler(): void;
	}
	
	/** 赢界面 */
	export class WinScene extends fgui.Window {
	    private static _instance;
	    static get inst(): WinScene;
	    /** 增长变化值 */
	    private moneyText;
	    /** 变化存值值 */
	    private obj;
	    private callbakc;
	    protected onInit(): void;
	    show(): void;
	    /** 播放金币增长动画 */
	    playMoneyUp(value: number, callbakc: Laya.Handler): void;
	    private completeHandler;
	    private updateHandler;
	    /** 重置所有属性 动作停止 */
	    resetData(): void;
	}
	
}