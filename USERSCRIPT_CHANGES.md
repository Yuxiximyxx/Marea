# 油猴脚本兼容性修复

## 根因 → 修复

| 问题 | 现象 | 修复 |
| --- | --- | --- |
| 运行时外层写死 `"use strict"` | 用了未声明变量赋值、`with`、`arguments.callee` 的旧脚本整体报错不执行 | 运行时改为非严格模式；用户代码放在独立内层函数里，`let GM = …` 之类重名不再 SyntaxError |
| `@require` 从未加载（`requireCode` 永远为空） | 依赖 jQuery 等库的脚本全部失效 | 新增 `ScriptResources`：保存/导入脚本时后台下载并磁盘缓存，注入时读缓存；支持 `//` 协议相对地址、`#sha256=` 片段、`data:` URI |
| 没有 `@resource` | `GM_getResourceText/URL` 返回空 | 同样缓存，注入为 data URL |
| `GM.*`（GM4 风格）同步返回 | `GM.getValue(...).then` 报 TypeError | 全部 `GM.*` 返回 Promise；`GM_*` 保持同步 |
| `@match`/`@include` 一律当通配符 | `/regex/` 写法、`*.example.com` 匹配裸域、`<all_urls>`、`@exclude-match`、端口、大小写全部失配 | 运行时重写匹配：匹配模式 + glob + 正则字面量 |
| `@noframes` 解析错误（无值行被正则丢弃） | 该声明始终无效 | 解析修复并在运行时按框架强制 |
| 只在页面加载完成后注入主框架；`document-start` 在 `onPageStarted` 才安装（赶不上本次导航） | `document-start` 脚本不生效；iframe 里的脚本从不运行 | 所有启用脚本都通过 `addDocumentStartJavaScript` 注入全部框架，URL/run-at 由运行时在各框架内判断；脚本列表变化时立即重装；`evaluateJavascript` 仅作兜底 |
| 编辑 / 开关脚本后不生效 | `LaunchedEffect` 只以脚本数量为 key | 以脚本内容为 key |
| `GM_xmlhttpRequest` 很简陋 | 无 `onerror`/`ontimeout`/`onabort`、无 `responseType`、无响应头、二进制损坏、不处理 http→https 重定向、不带 Cookie、不能发 FormData/Blob | 重写：base64 传输字节，支持 json/arraybuffer/blob/document、charset、响应头、手动跟随重定向、Cookie 往返、`anonymous`、`user/password`、`timeout`、`abort()`、FormData/URLSearchParams/Blob/ArrayBuffer；结果由发起请求的框架轮询取回（iframe 也能收到） |
| `GM_registerMenuCommand` 是空实现 | 靠菜单触发的脚本无法使用 | 「更多」面板新增「脚本命令」入口，列出并执行当前页脚本注册的命令；`GM_unregisterMenuCommand` 同步 |
| `GM_addStyle` 在严格 CSP 下无效、`document-start` 时 `<head>` 不存在 | 样式不生效 / 报错 | 等待根节点；被 CSP 拦截时回退到 `adoptedStyleSheets` |
| API 缺失 | `GM_addElement`、`GM_getValues/setValues/deleteValues`、`GM_addValueChangeListener`（原为空）、`GM_download`（原为开新标签）、`GM_setClipboard`、`GM_notification` 对象写法、`GM_cookie`、`GM_getTab(s)/saveTab`、`window.onurlchange`、`GM_info.script.*` 完整字段、`@top-level-await` | 全部补齐 |
| UMD 库误走 AMD 分支 | 页面有 `define.amd` 时 `@require` 的 jQuery 不挂全局 | 脚本作用域内 `define/module/exports` 置为 undefined |
| `.user.js` 链接检测只认 `endsWith` | 带查询串/片段的安装链接、以附件下发的脚本不会弹导入 | 按 URL path 判断，并拦截下载回调 |

## 改动文件
- 新增 `app/src/main/assets/mafari_gm_runtime.js`：GM 运行时（匹配、run-at、全部 GM API）
- 重写 `UserscriptEngine.kt`、`GmBridge.kt`；新增 `ScriptResources.kt`
- 修改 `BrowserState.kt`、`BrowserScreen.kt`、`MenuActions.kt`、`Extensions.kt`
- 新增 `tools/userscript-test/`：77 项 Chromium 端到端测试

## 已知限制
- 脚本与页面共用同一个 JS 全局（WebView 没有隔离世界）。`@require` 的库若写全局（如 jQuery）会覆盖页面同名变量。
- `GM_info.scriptHandler` 为兼容性报告为 `Tampermonkey`（另有 `GM_info.mafari = true`）。
- 脚本命令只收集主框架注册的命令。
- 桥接对象对页面可见（`addJavascriptInterface` 的固有限制），与旧版一致。
- Kotlin 部分在无 Android SDK 的环境里改写，未能编译，请在本地 `./gradlew assembleDebug` 验证。
