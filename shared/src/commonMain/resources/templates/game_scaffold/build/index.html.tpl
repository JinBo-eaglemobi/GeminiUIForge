<!DOCTYPE html>
<html>
<head>
    <meta charset='utf-8'/>
    <!--    <meta name='viewport' content='width=device-width,initial-scale=1.0,minimum-scale=1.0,maximum-scale=1.0,user-scalable=no,viewport-fit=cover'/>-->
    <meta name='renderer' content='webkit'>
    <meta name='apple-mobile-web-app-capable' content='yes'/>
    <meta name='full-screen' content='true'/>
    <meta name='x5-fullscreen' content='true'/>
    <meta name='360-fullscreen' content='true'/>
    <meta name='laya' logoimg='logo.png' logobkcolor='#000000' screenorientation='{{SCREEN_ORIENTATION}}' cacheid='fffffffff'/>
    <meta http-equiv='X-UA-Compatible' content='IE=edge,chrome=1'>
    <title>{{PROJECT_NAME}}</title>
    <style id="outfit-font-face">
        @font-face {
            font-family: 'Outfit';
            src: url('assets/font/outfit.woff2') format('woff2'),
                 url('assets/font/outfit.woff') format('woff'),
                 url('assets/font/outfit.ttf') format('truetype');
            font-display: swap;
        }
    </style>
    <style>
        .safe-area {
            padding: env(safe-area-inset-top, 0px) env(safe-area-inset-right, 0px) env(safe-area-inset-bottom, 0px) env(safe-area-inset-left, 0px)
        }
    </style>
</head>
<body style='background-color: #000;'>
<script type="text/javascript">
    function loadLib(url) {
        var script = document.createElement("script")
        script.async = true
        script.src = url
        document.body.appendChild(script)
    }

    function backGame() {
        window.location.replace("./config.js")
    }

</script>
<script type="text/javascript">
    // 顶层窗口且无壳注入 APP 时，同步加载自定义加载页（iframe 嵌入不加载）
    (function () {
        if (window.APP) return;
        try { if (window.top !== window.self) return; } catch (e) { return; }
        // document.write('<script src="./loadingPage.js"><\/script>');
    })();
</script>
<script type="text/javascript" src="./index.js"></script>
<div class="safe-area"></div>
</body>
</html>
