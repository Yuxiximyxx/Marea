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
- 注册粒度是「站点」（scheme + 可注册域名）：同一站点的来源规则（裸域、`*.站点`、清单里的落点 / frame）合并为一份脚本、一组规则，同一个 seed。
- 脚本运行时还会自检：`self.origin` 必须命中内联的 `rules`（精确来源或前导 `*.` 子域通配，通配不含裸域），否则整段不执行（防止规则匹配与预期不一致，例如 about:blank 继承来源）。祖先链见 §3 / §9。

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
| 同站点重定向（裸域 / www / m / login …） | `prepare` 时把裸域和 `*.站点` 一起注册（§9.1） | 可靠（需实机确认通配 / 裸域语义） |
| 预注册清单里的重定向落点（IdP 等） | 导航到清单里的站点前，落点一并注册（§9.1） | 可靠（仅限清单内） |
| 其它服务端重定向的落地页 | `shouldOverrideUrlLoading` 补注册；`doUpdateVisitedHistory` 检测最终落点并按兜底策略处理（§9.2） | 尽力而为；本次加载可能无保护 |
| 没经过任何回调的导航 | `onPageStarted` 检测 + 补注册，补注册**只对之后的加载生效**，本次按兜底策略处理 | 本次加载可能无保护 |
| 预注册清单里的跨源 iframe | 清单 frame 在首次导航前批量注册并固定；嵌入方作为允许的祖先内联（§9.3） | 仅限清单内 |
| 清单之外的跨源 iframe | 不注册 | **不保护（fail-open，有意如此）** |

**不能宣传为「所有未知跨源框架都能自动获得保护」。**

关于跨源 iframe：脚本内置检查，`location.ancestorOrigins` 里的每个祖先都必须是「自己人」——等于自身、命中自己的规则集合（同站点跨子域），或命中预注册清单为它内联的嵌入方——否则直接退出。
所以即使某个跨源 frame 的 origin 因为曾经作为顶层页面访问过而仍在注册表里，它被清单之外的站点嵌入时也不会被保护。
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
| `fp.stats` | 注入脚本在 `pagehide` / 页面隐藏时上报一次「被处理的读取次数」 | 只收主框架；单次上限 100000；只做展示（累计计数持久化，从「全关 → 开启」起重新计数；显示在指纹保护 sheet 顶部的隐私报告和首页隐私报告卡里） |
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
6. `https://*.example.com` 规则是否同时匹配裸域 `https://example.com`（原生统一把裸域另写一条，不依赖这一点；但需确认两条同时存在时不会重复注入、不会冲突）；
7. 「同步注册后放行」（`shouldOverrideUrlLoading`）是否等价于 renderer 在 document-start 前已收到注册；
8. 同一 WebView 内一个站点的规则集合 / 嵌入方变化时「先注册新脚本再撤销旧脚本」是否会出现短暂的重复执行（脚本无状态标记，重复执行只会叠加噪声，需确认是否可见）；
9. `doUpdateVisitedHistory` 在各类服务端重定向（301/302/307、meta refresh、JS 跳转）下给出的是否都是最终地址，以及 RELOAD 兜底对 POST 落地页的影响（重载会变成 GET）；
10. 升级到 webkit 1.18.0 后的编译（1.18 有若干 API 重命名，本项目用到的 API 预计不受影响，但未实际编译）。


## 9. 重定向与跨源 iframe：预注册 + 落地检测 + 边界声明

`addDocumentStartJavaScript` 的注入判定严格发生在文档开始加载之前，原生侧不可能在页面已经开始加载后再补上 document-start。
所以收窄这两个缺口只有三类手段：**加载前尽量多注册**、**加载后检测并止损**、**对外口径写清楚边界**。

### 9.1 预注册（主要手段）

预注册清单：`assets/mafari_fp_prereg.json`（产品 / 运营侧维护，默认空）。

```json
{
  "version": 1,
  "redirects": [
    { "site": "example.com",
      "targets": ["https://login.idp.example", "https://*.idp.example"] }
  ],
  "embeds": [
    { "embedders": ["https://*.example.org"],
      "frames":    ["https://player.vendor.example", "https://*.comments.example"] }
  ]
}
```

- 规则语法（`OriginRule`）只有两种：`scheme://host[:port]` 与 `scheme://*.host[:port]`；通配**只匹配子域名，不含裸域**，裸域要另写一条。
  不接受裸 `*`、中间 / 末尾通配、公共后缀通配（`*.com`、`*.co.uk`）、IP 通配、非 http(s)。非法条目静默丢弃。
- **自动同站点兄弟**：导航到某个普通域名（默认端口、非 IP）时，原生自动把 `scheme://站点` 和 `scheme://*.站点` 一起注册进同一份脚本，
  覆盖 www ↔ m ↔ login 这类站内重定向，不需要人工维护清单。站点下只要有任何豁免条目，就退回精确来源、不使用通配，避免把保护注入到被豁免的子域名。
- **redirects**：导航到 `site`（可注册域名）之前，`targets` 一并注册（进入普通 LRU，每个 WebView 最多 16 个站点）。
- **embeds.frames**：WebView 初始化后、首次导航前（第一次 `prepare` 触发 `ensurePreregistered`）批量注册，**固定**，不被 LRU 驱逐，最多 24 个站点；
  设置 / 豁免列表变化后随 `clear()` 失效并在下一次 `prepare` 重新注册。
- 不支持把嵌入方写成 `*`：那等于放弃来源边界。确实需要时，只能作为明确的产品取舍（例如只用于登录流程的独立 WebView 实例），不得与「严格来源验证」混用口径。

### 9.2 落地检测与兜底（不是注入时机）

`onPageStarted` 给的是重定向**之前**的地址；服务端重定向的最终落点要在 `doUpdateVisitedHistory`（提交时）才看得到。
两处都会调用同一个检测：主框架落在一个「本应保护、却没有任何注册覆盖」的 http(s) origin 上，就是漏网。
检测一定发生在补注册之前，否则永远通过。无论策略如何，都会先补注册（对之后的加载生效）。

设置 → 指纹保护 →「未预注册页面的兜底」：

| 选项 | 行为 |
|---|---|
| 仅记录，不干预 | 只记日志并补注册，页面照常加载 |
| 停止并在保护下重载（默认） | `stopLoading()` 后用已注册的保护重新加载一次（同一 URL 只重载一次，防循环）。**带一次性凭证的地址**（`code=` / `state=` / `token=` / `ticket=` / `SAMLResponse=` 等，见 `looksSingleUse`）退化为仅记录，避免登录回调被重复提交而失败 |
| 停止加载 | `stopLoading()` 并在页面上提示，不继续加载 |

局限：这是止损而不是补救——被检测到的那一次加载，页面脚本可能已经执行过一部分；重载会把 POST 落地页变成 GET；
没有「转入系统浏览器」选项（把用户从隐私浏览器里扔进系统浏览器，通常比无保护加载更糟）。

### 9.3 跨源 iframe：清单是唯一可靠路径

- 被嵌入的 frame 脚本里内联了「允许它的跨源祖先」（只含与该 frame 站点有关的条目，不泄露清单里别的站点）。
  `location.ancestorOrigins` 里的每个祖先必须等于自身、命中自己的规则集合，或命中这份嵌入方列表，否则脚本退出。
- 同站点跨子域的 iframe（`www.example.com` 嵌 `m.example.com`）因为同属一个规则集合，现在也会得到保护，且与顶层页面同 seed。
- 可控的第三方嵌入内容，也可以自己在内部实现指纹保护并通过 `postMessage` 与主页面协作；这不属于本机制的覆盖范围，与原生注入解耦。

### 9.4 对外口径

> 对预注册 origin 集合内的框架（含同站点子域名、清单内的重定向落点与被嵌入的跨源框架）提供严格来源验证 + document-start 同步保护；
> 集合外的跨源框架不保护；未预注册的重定向落点按「兜底策略」止损，但那一次加载本身可能没有保护。

不要写成「自动覆盖所有跨源 iframe」或「所有重定向都受保护」。
