window.screenOrientation = '{{SCREEN_ORIENTATION}}';

let loadBaseUrl = "./"

let arr = [
    {
        res: {key: 'config.js', v: 1}
    },
    {
        res: [
            {key: "domparserinone.min.js", v: 1},
            {key: 'laya.core.min.js', v: 1},
        ]
    },
    {
        res: [
            {key: 'laya.ani.min.js', v: 1},
            {key: 'laya.html.min.js', v: 1},
            {key: "spine-core-3.8.min.js", v: 1},
            {key: 'fairygui.min.js', v: 1},
        ]
    },
    {
        res: [
            {key: 'reflect.min.js', v: 1},
            {key: 'laya.spine.min.js', v: 1},
        ]
    },
    {
        parallel: false, res: [
            {key: 'tsCore.min.js', v: 1},
            {key: 'gameLib.min.js', v: 1},
            {key: 'common.min.js', v: 1}
        ]
    },
    {
        res: {key: 'main.min.js', v: 1}
    }
];
const httpProtocol = window.location.protocol === "http:"

const showProgress = function (v) {
    v = parseFloat(v.toFixed(2))
    if (callIOS("progress", {
        value: v
    })) return
    if (window.APP && window.APP.progress) {
        window.APP.progress(v)
    } else if (window.parent.GameToHall && window.parent.GameToHall.progress) {
        window.parent.GameToHall.progress(v)
    }
    if (window.parent.GameToHall && window.parent.GameToHall.getProgress) {
        window.parent.GameToHall.getProgress(v)
    } else if (window.loadingView && window.loadingView.executionJavascript) {
        window.loadingView.executionJavascript("window.GameToHall.getProgress(" + v + ")")
    } else if (window.loadingView && window.loadingView.loading) {
        window.loadingView.loading(v)
    }
}

const loadInitProgress = function (pro) {
    let v = getProgress(pro, 1, 4)
    v = parseFloat(v.toFixed(2))
    console.debug("loadInitProgress->" + v)
    showProgress(v)
}

const loadInitError = function () {
    let errorText = "init: Failed to load resource!"
    if (window.loadingView && window.loadingView.executionJavascript) {
        window.loadingView.executionJavascript("window.GameToHall.openModal('" + errorText + "')")
        window.loadingView.executionJavascript("window.GameToHall.gameClose(" + 0 + ", " + 0 + ")")
    } else if (window.parent.GameToHall) {
        window.parent.GameToHall.openModal(errorText)
        window.parent.GameToHall.gameClose(0, 0)
    } else {
        if (window.APP && window.APP.openModal) window.APP.openModal(errorText)
        if (window.APP && window.APP.gameClose) window.APP.gameClose(0, 0)
    }
}

function startInit() {
    loaderTotalNum = arr.reduce((previousValue, currentValue) =>
        previousValue + (Array.isArray(currentValue.res) ? currentValue.res.length : 1), 0)

    window.onProgress = loadInitProgress
    window.onError = loadInitError
    window.crashUrl = () => {
        return window.gameCrash + (window["conch"] ? "app-crash" : "wap-crash")
    }
    window.sendErrorVerifies = (msg, url)=> {
        if (typeof msg === "string" && msg.containsIgnore("NS_ERROR_FAILURE",
            "NS_ERROR_OUT_OF_MEMORY", "out of memory")) {
            return false
        }
        if (url && url.length > 0 && (!url.startsWith("http") || url.containsIgnore(".html"))) return false
        return true
    }

    let country = getQueryString("country");
    let channel = getQueryString("channel");

    window.country = country
    window.channel = getQueryString("channel")
    if (!channel) window.channel = country

    loadNext()
}

let gameloadJs = {key: 'gameload.min.js', v: 1}
const script = document.createElement("script")
script.onerror = loadInitError
script.onload = startInit
script.src = `${gameloadJs.key}${(!window.conch && !window.conchMarket) ? "?v=" + gameloadJs.v : ""}`
document.head.appendChild(script)

let loadIndex = 0;
let isLoadError = false;

function loadNext() {
    if (isLoadError) return
    const config = arr.shift()
    let res = config.res
    if (!Array.isArray(res)) res = [res]
    res.forEach(value => {
        if (!value.key.startsWith("http")) {
            value.key = loadBaseUrl + value.key
        }
    })

    loadBatch(config.res, config.parallel, () => {
        loadIndex++
        if (arr.length > 0) loadNext()
    })
}
