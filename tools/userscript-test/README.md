# GM 运行时测试

`app/src/main/assets/mafari_gm_runtime.js` 是纯 JS，可以不装 Android 直接在 Chromium 里测：

    npm i playwright && npx playwright install chromium
    node tools/userscript-test/run.js

`harness.js` 用与 `UserscriptEngine.buildSource` 相同的占位符替换方式拼装脚本，并用页面内的假 `MafariGM` 模拟原生桥。
