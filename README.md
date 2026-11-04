# Mafari

基于 **MIUIX Glass**（Compose Multiplatform）+ **系统 WebView** 的 Android 浏览器。不内嵌 Chromium 内核，安装包约 1.5 MB（R8 精简后）。

本仓库内嵌 `miuix/`（miuix-glass 分支源码），通过 Gradle `includeBuild` 依赖替换接入，无需先 `publishToMavenLocal`。

## 界面

| 主页 | 更多面板 | 设置 |
| --- | --- | --- |
| ![主页](docs/screenshot-home.jpg) | ![更多](docs/screenshot-menu.jpg) | ![设置](docs/screenshot-settings.jpg) |

- 单条悬浮玻璃底栏：`glassPanel` 材质 + 地址胶囊 + 标签页 + 更多
- 主页液态玻璃字标、收藏（带 favicon、可编辑、模糊底 + 高光描边）
- 全部弹层按 miuix-glass demo 规范：`GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` / `GlassTopAppBar`
- 状态栏：初始留白，向下滚动顶满，回顶回落

## Glass 迁移说明

| 旧（miuix-ui） | 新（miuix-glass） |
| --- | --- |
| `OverlayDialog` + `TextButton` | `GlassDialog` + `GlassDialogButton` |
| `OverlayBottomSheet` | `GlassBottomSheet` |
| 自绘 `SheetScaffold` | `GlassBottomSheet` |
| `SmallTopAppBar` + 渐进模糊 | `GlassTopAppBar` + `GlassIconButton` + `MiuixScrollBehavior` |
| `OverlayDropdownPreference` | `GlassDropdownPreference`（带 backdrop） |
| 自绘 `glassBar` 纹理模糊 | `Modifier.glassPanel` |

背景采样遵循 [glass 指南](miuix/docs/zh_CN/guide/glass.md)：页面内容包在 `layerBackdrop` 内，浮层放在采样层之外、`Scaffold` 之内。

### 书签 / 历史

- 设置 → 浏览 → 书签 / 历史；底栏「更多」→ 历史
- 库页面顶部分「书签 | 历史」两栏，可左右滑动切换
- 书签按分组展示（默认「个人收藏」）；右下角 FAB：新建 / 导入 / 导出
- 导入导出兼容 Chrome、Firefox 的 Netscape Bookmark HTML（导出到 `Download/mafari/`）

### 底栏手势

- 网页向下累计约 100px 才隐藏；隐藏后须向上累计约 120px 才恢复（降低误触）
- 底栏上竖滑阈值提高到 56px；**左右滑动**缩小为圆形搜索钮，点按或再滑展开

### WebView 与 glass 采样

`layerBackdrop` 只记录 Compose 图层；`AndroidView`/`WebView` 不在采样范围内（SurfaceView 与 RuntimeShader 组合易闪烁）。因此：

- **主页**（Compose 内容）：`glassPanel` / `GlassDialog` 用真 backdrop 模糊
- **网页态**：底栏 `backdrop = null`，走 `glassPanel` 的 `fallback` 渐变底板，不采样 WebView，避免页面闪烁

若要在网页态做真模糊，需先把 WebView 位图拷到 ImageBitmap 再作 backdrop（滚动时逐帧拷贝成本高、仍可能与硬件层冲突），当前实现有意不做。


### 去广告（设置 → 高级 → 去广告）

去广告已从脚本页独立，自带引擎（`AdBlockEngine.kt` / `AdBlock.kt`）：

- **网络拦截**：WebView `shouldInterceptRequest` 按规则拦截子资源请求（`||域名^`、通配、`@@` 例外、`$third-party / $domain= / 资源类型 / $important`）
- **元素隐藏**：`##选择器`、站点规则、`~站点` 排除、`#@#` 例外；document-start 注入到所有框架
- **订阅源**：URL 导入 / 本地文件导入，支持 Adblock Plus、uBlock、AdGuard 与 hosts 格式；可启停、手动或自动更新（每天 / 3 天 / 每周）
- **自定义规则**、**站点白名单**；长按网页元素「标记为广告」会写入自定义规则
- 不支持：正则规则、`$redirect / $removeparam / $csp / $replace`、`#?#` 扩展选择器、`+js()`（解析时跳过）
- 订阅源是第三方内容，元素隐藏选择器注入前会校验，拒绝含 `{ } ;` 的选择器
- 旧版本放在脚本列表里的去广告订阅，首次启动会自动迁移并重新下载

## 功能

**浏览**
- 多标签（文字列表 / 卡片两种样式）、前进后退刷新、加载进度
- 地址栏可选显示：网站标题 / 主域名 / 完整域名
- 下拉刷新（4cm 触发，顶部有刷新动画）
- 页面内查找、翻译、分享、复制链接、外部浏览器打开、长按链接菜单
- 首页打开网页后，后退可回到主页

**脚本（油猴）**
- 支持 `@name` / `@match` / `@include` / `@run-at` 元数据
- `document-start` 脚本在页面可见时注入，其余在加载完成注入
- GM API 垫片：`GM_setValue/getValue/deleteValue/listValues/addStyle/xmlhttprequest/notification/openInTab/registerMenuCommand/info`、`unsafeWindow`
- 从 URL 导入 `.user.js`、一键更新；自动识别 `.user.js` 链接并弹导入确认

**下载**
- 内置下载器（不走系统下载器），先写缓存再入库到 `/sdcard/Download/mafari`
- 4 线程分段下载（服务器支持 Range 时）、断点续传、暂停/继续、失败重试、删除记录与文件
- 前台服务 + 进度通知；按小米官方文档接入**超级岛**（`miui.focus.param`，仅在开始下载时上岛）

**其他**
- 收藏 / 历史（最多 300 条）/ 下载管理 / 脚本管理
- 设置：显示模式（跟随系统/日间/夜间）、主页壁纸、柔光玻璃、搜索引擎、自定义 UA、检查更新
- 网页弹窗（`window.open`）在当前标签打开

## 部署

### 本地构建

```bash
# 需要 JDK 17+（推荐 21）、Android SDK（platforms;android-37.0、build-tools;36.0.0）
# 根工程 Gradle 9.8.2；miuix 源码在 ./miuix，由 includeBuild 参与构建
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```


### GitHub Actions 自动发布

推 tag 即自动构建并创建 Release：

```bash
git tag v3.3.0 && git push origin v3.3.0
```

可选签名 Secrets（不配也能出 debug 包）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 your.jks` |
| `STORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | key 别名 |
| `KEY_PASSWORD` | key 口令 |

## 项目地址

https://github.com/rosenii/mafari
