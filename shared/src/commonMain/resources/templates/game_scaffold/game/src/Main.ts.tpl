import { layoutConfig } from "./ui/layoutConfig";

declare const tsCore: any;
declare const Laya: any;
declare const fgui: any;
declare const SceneManager: any;
declare const App: any;

/**
 * 游戏入口主类
 * 基于 tsCore.UIFactory 纯代码构建渲染界面
 */
class Main extends tsCore.EProxy {
    constructor() {
        super();
        this.initGame();
    }

    private initGame(): void {
        console.log("[GeminiUIForge] Game initializing...");
        const width = {{CANVAS_WIDTH}};
        const height = {{CANVAS_HEIGHT}};

        // 初始化 Laya 舞台引擎
        if (typeof Laya !== "undefined") {
            if (typeof (Laya as any).init === "function" && !(Laya as any).stage) {
                (Laya as any).init(width, height, (Laya as any).WebGL);
            }
            if ((Laya as any).stage) {
                (Laya as any).stage.scaleMode = (Laya as any).Stage.SCALE_SHOWALL;
                (Laya as any).stage.alignH = (Laya as any).Stage.ALIGN_CENTER;
                (Laya as any).stage.alignV = (Laya as any).Stage.ALIGN_MIDDLE;
                (Laya as any).stage.screenMode = (Laya as any).Stage.SCREEN_NONE;
            }
        }

        // 初始化场景管理器基准分辨率
        if (typeof SceneManager !== "undefined" && SceneManager.inst) {
            SceneManager.inst.gameWidth = width;
            SceneManager.inst.gameHeight = height;
        }

        // 初始化 tsCore 扩展配置 (包含 setupScrollPanel 与 EPanel 扩展)
        if (typeof tsCore !== "undefined" && (tsCore as any).DefineConfig && typeof (tsCore as any).DefineConfig.init === "function") {
            try {
                (tsCore as any).DefineConfig.init();
            } catch (e) {
                console.warn("[GeminiUIForge] DefineConfig.init warning:", e);
            }
        }

        // 初始化 FairyGUI 默认配置与字体
        if (typeof fgui !== "undefined") {
            if (fgui.UIConfig) {
                fgui.UIConfig.defaultFont = "Outfit, Arial, sans-serif";
                fgui.UIConfig.modalLayerColor = "rgba(33,33,33,0.7)";
            }
            // setupScrollPanel 扩展双重保底防呆
            if (fgui.GComponent && !(fgui.GComponent.prototype as any).setupScrollPanel) {
                (fgui.GComponent.prototype as any).setupScrollPanel = function (overflowType?: any, scrollType?: any) {
                    if (typeof (this as any).setupOverflow === "function") {
                        (this as any).setupOverflow(overflowType || fgui.OverflowType.Visible);
                    }
                };
            }
            if (fgui.GRoot && fgui.GRoot.inst) {
                fgui.GRoot.inst.setSize(width, height);
            }
        }

        // 将 FairyGUI 根容器挂载到 Laya 舞台上
        if (fgui.GRoot && fgui.GRoot.inst && fgui.GRoot.inst.displayObject) {
            Laya.stage.addChild(fgui.GRoot.inst.displayObject);
        }

        // 纯代码构建 UI 并挂载至 GRoot
        if (typeof tsCore !== "undefined" && (tsCore as any).UIFactory) {
            const UIFactory = (tsCore as any).UIFactory;
            UIFactory.resBasePath = "res/gameui/";
            console.log("[GeminiUIForge] Building pure-code UI via tsCore.UIFactory...");
            try {
                const rootPanel = UIFactory.createPanel(layoutConfig, width, height);
                if (rootPanel && fgui.GRoot && fgui.GRoot.inst) {
                    fgui.GRoot.inst.addChild(rootPanel);
                    console.log("[GeminiUIForge] UI Panel mounted successfully to fgui.GRoot.inst!");
                }
            } catch (err) {
                console.error("[GeminiUIForge] Failed to create UI Panel via UIFactory:", err);
            }
        } else {
            console.warn("[GeminiUIForge] tsCore.UIFactory is undefined, fallback to basic scene.");
        }

        console.log(`[GeminiUIForge] Game ready. Resolution: ${width}x${height}`);
    }
}

// 启动游戏实例
if (typeof App !== "undefined" && App.run) {
    App.run(Main);
} else {
    new Main();
}
