/**
 * UI Studio 纯代码界面布局配置数据（自动生成，遵循 UIFactory 规范）
 */
export const layoutConfig = {
    type: "panel",
    name: "MainView",
    x: 0,
    y: 0,
    width: {{CANVAS_WIDTH}},
    height: {{CANVAS_HEIGHT}},
    elements: [
        {
            type: "graph",
            name: "backgroundMask",
            x: 0,
            y: 0,
            width: {{CANVAS_WIDTH}},
            height: {{CANVAS_HEIGHT}},
            color: "#1e1e24",
            alpha: 1
        },
        {
            type: "text",
            name: "welcomeTitle",
            text: "{{PROJECT_NAME}}",
            fontSize: 48,
            color: "#ffffff",
            bold: true,
            align: "center",
            valign: "middle",
            x: 0,
            y: Math.floor({{CANVAS_HEIGHT}} / 2 - 60),
            width: {{CANVAS_WIDTH}},
            height: 60
        },
        {
            type: "text",
            name: "subtitle",
            text: "Powered by GeminiUIForge & UIFactory",
            fontSize: 24,
            color: "#9e9e9e",
            align: "center",
            valign: "middle",
            x: 0,
            y: Math.floor({{CANVAS_HEIGHT}} / 2 + 10),
            width: {{CANVAS_WIDTH}},
            height: 40
        }
    ]
};
