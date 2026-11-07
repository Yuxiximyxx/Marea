# Mafari 指纹保护：架构与安全边界

适用代码：`assets/mafari_fingerprint.js`、`FingerprintInjector.kt`、`AdBlock.kt`（指纹相关部分）、`BrowserState.kt`（导航入口）。

## 1. 主架构：origin-scoped document-start 注入 + 内联配置

核心变化：**不再让网页向原生层询问「我是谁、该用什么配置」**。由 WebView 在注入之前确定来源，并且只向匹配来源的框架交付对应配置。

```
原生侧                                                  WebView                    页面
─────────────────────────────────────────────────────────────────────────────────────────
prepare(url)  ──生成 origin 专属脚本（配置内联）──▶ addDocumentStartJavaScript(
                                                       script, setOf("https://a.com"))
                                                    匹配到 a.com 的框架才拿到脚本 ──▶ 第一个页面脚本之前执行
                                                                                       配置只在脚本闭包里
```

- 配置 `{mask, seed, origin}` 由原生侧按 origin 生成，作为参数内联进脚本模板（占位符见 `mafari_fingerprint.js` 末尾）。
- **每个 origin 一份独立脚本**。`addDocumentStartJavaScript` 的 origin 规则只是「注入范围」，不是每个 frame 的配置回调；
  所以绝不能把所有站点的配置放进同一份脚本再让页面去读。
- 页面里没有任何指纹相关的 JS 桥、全局变量或可调用接口。页面无法通过伪造 `host` / `topHost` 枚举其他站点的配置，
  因为根本没有可以传参的入口。
- seed = HMAC-SHA256(会话密钥, 可注册域名) 的前 32 位；会话密钥只存在于原生进程内，重启后变化。
- 脚本运行时还会自检：`self.origin` 必须等于内联的 origin，否则整段不执行（防止规则匹配与预期不一致，例如 about:blank 继承来源）。

### 已移除
`@JavascriptInterface fun fp(host, topHost)`（`MafariAdblock.fp`）已删除。它既不能提供严格的来源验证，
又会把保护机制变成可探测的攻击面。去广告的 `MafariAdblock.css()` 桥是另一项功能，保持不变。

## 2. 两个不同的名单

| 名单 | 含义 | 位置 |
|---|---|---|
| **保护豁免列表** | 命中后**不注入**指纹保护，并撤销该 origin 已有的注册 | `AdBlock.fpExemptHosts`（设置 → 隐私与安全 → 指纹保护 → 保护豁免站点） |
| **允许注入的 origin** | 原生侧已经为它注册了脚本的 origin；不是用户可编辑的名单 | `FingerprintInjector.handlers`（每个 WebView 最多 16 个，LRU） |

- 豁免列表只按 **frame 自己的 host（含子域名）** 判断，不看顶层站点（不再有 `topHost` 语义）。
- 去广告的「站点白名单」（`AdBlock.allowHosts`）与豁免列表**相互独立**，修改一个不影响另一个。
  为保持升级后的行为不变，首次升级时会把现有去广告白名单一次性复制到豁免列表；此后两边各自维护。
- 判定顺序：四项开关全关 → 不注入；host 命中豁免 → 不注入；否则为该 origin 注册脚本。

## 3. 动态导航（最大的工程难点）

注入脚本**只作用于注册完成之后才开始加载的文档**，所以必须在导航开始之前注册：

| 导航来源 | 处理 | 可靠性 |
|---|---|---|
| Mafari 自己 `load()` / `loadUrl()` / 桌面模式重载 | 先 `prepare(url)` 再加载 | 可靠 |
| `reload()` | 先 `prepare(webView.url)`（设置变更后旧注册已作废） | 可靠 |
| 前进 / 后退 | 先为目标历史项 `prepareHistory(±1)` | 可靠 |
| 用户点击链接 / 脚本触发的主框架导航 | `shouldOverrideUrlLoading` 里先注册，再放行 | 尽力而为 |
| 服务端重定向的落地页 | 同上（重定向也会回调），但与加载存在竞态 | 尽力而为 |
| 没经过任何回调的导航 | `onPageStarted` 补注册，**只对之后的加载生效** | 本次加载无保护 |
| 跨源 iframe | 不注册 | **默认不保护** |

**不能宣传为「所有未知跨源框架都能自动获得保护」。**

关于跨源 iframe：脚本内置检查，只要任一祖先 origin（`location.ancestorOrigins`）与自身不同就直接退出。
所以即使某个跨源 frame 的 origin 因为曾经作为顶层页面访问过而仍在注册表里，它被嵌入别处时也不会被保护。
同源 iframe 能否拿到脚本取决于 WebView 对 origin 规则的匹配（`about:blank` / `srcdoc` 子框架是否匹配尚未在多机型上验证）。

设置或豁免列表变化后，已注册的脚本里内联着旧配置：`fingerprintGeneration` 递增，各 WebView 在下一次 `prepare`
时撤销全部旧注册；对当前页面需刷新后生效。

## 4. 降级路径

`WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)` 为 false 时（**必须运行时判断**，不能只看 Android / Chromium 版本）：

- 不注册脚本；仅此时才安装无参桥 `MafariFp.fp()`，没有 `host` / `topHost` 参数；
- 原生侧自己维护「当前顶层 origin」，`fp()` 只返回该 origin 的 `掩码|种子|origin`；
- 脚本核对返回的 origin 与 `self.origin` 一致后才使用，并且只保护主框架 / 祖先链同源的 iframe。

降级路径的安全边界**明显弱于主路径**：

- 脚本由 `onPageStarted` 里的 `evaluateJavascript` 补跑，**不保证先于页面第一个脚本**；
- `fp()` 会把 seed 交给页面可调用的桥：页面脚本（包括抢先执行的）能读到**自己**这一站的 seed，并据此反推噪声。
  它读不到别的站点的配置，但无法对抗恶意页面本身；脚本读取后会 `delete window.MafariFp`，这只是减少暴露，不是安全边界；
- 脚本需要一个 `__mafariFp3` 标记保证幂等，页面可据此探测到保护已开启。

## 5. WebMessageListener：低频可信通道

`TrustedChannel.kt`，页面里的对象名 `MafariTrusted`。它**不承担指纹配置下发**，也不向页面返回任何数据；
只承载低频、可异步、需要框架给出严格来源的操作。

| op | 含义 | 约束 |
|---|---|---|
| `fp.stats` | 注入脚本在 `pagehide` / 页面隐藏时上报一次「被处理的读取次数」 | 只收主框架；单次上限 100000；只做展示（设置 → 指纹保护里的本次启动计数） |
| `fp.exempt.request` | 页面请求「关闭对我的指纹保护」 | 只收主框架；必须是当前显示页面的 origin；**用户确认**后才写入豁免列表；只豁免该 origin 自己的 host；每个 origin 每次运行最多弹 3 次、间隔 ≥ 30 秒；与「正在等待的重定向确认」互斥 |

安全约束：

- 来源只信框架给的 `sourceOrigin` / `isMainFrame`，**不读消息里自带的 host / origin**；
- 只接受 http(s) 来源、字符串消息、≤ 256 字符、`op` 在固定白名单内，其余丢弃；
- 注册规则用 `"*"` 只是让对象注入到所有 frame，真正的来源校验在分发时做；
- 对象在页面上是可见的（页面能知道 Mafari 提供了这个通道）；页面能做的只有：给自己的计数加数、请求用户确认一次豁免；
- 运行时检查 `WEB_MESSAGE_LISTENER`，不支持就不安装，指纹保护不受影响；
- 脚本在 document-start 捕获通道的 `postMessage`，之后页面改写 `window.MafariTrusted` 不影响上报。

**权限申请不走这里**：摄像头 / 麦克风 / 定位 / 通知本来就经 `WebChromeClient` 的标准回调，来源由框架给出，且已有确认弹窗，没有必要再造一条自定义通道。

轻应用没有确认弹窗，所以**不安装**可信通道：轻应用里没有统计，也不接受页面发起的豁免请求，豁免只能在浏览器的设置里改。

## 6. 依赖

`DOCUMENT_START_SCRIPT` 需要 AndroidX WebKit ≥ 1.9.0。项目已升级到 `androidx.webkit:webkit:1.18.0`（2026-10-21 稳定版，minSdk 24，项目 minSdk 33 满足）。

## 7. 不在保护范围内

Worker、WebGL2 PBO、所有 CSS 字体探测、OfflineAudioContext 的 `getChannelData` 路径。

轻应用（`WebAppActivity`）已接入同一套 origin 注册（`loadUrl` / 刷新 / 后退 / 主框架导航）。
与浏览器的差别：从保存的状态恢复时，恢复出的历史页若与起始 origin 不同，那一次加载无保护（`onPageStarted` 只能补注册，对之后的加载生效）。

## 8. 待实机验证

1. 同源 `about:blank` / `srcdoc` iframe 是否收到脚本；
2. 重定向落地页与 `shouldOverrideUrlLoading` 注册之间的竞态；
3. bfcache 恢复的页面（不会重新执行脚本，沿用首次加载时的配置）；
4. 一个 WebView 注册 16 个 origin 时的内存与启动开销；
5. `MafariTrusted` 对象注入先于 document-start 脚本执行（脚本依赖这一点来捕获通道；没有时只是不上报）；
6. 升级到 webkit 1.18.0 后的编译（1.18 有若干 API 重命名，本项目用到的 API 预计不受影响，但未实际编译）。
