# Changelog

本文件记录 mafari 的用户可见变更。发版时在对应版本下追加条目；GitHub Release 正文优先读取本文件该版本小节。

## [1.5.10-dev]

### Removed
- 设置 → 高级 → DNS 管理（WebView 无法单独改写 DNS，保留无意义）

### Fixed
- 修复 v1.5.8+ 编译失败：`PermissionRequest.RESOURCE_NOTIFICATIONS` / `RESOURCE_GEOLLOCATION` 在 Android SDK 中不存在
  - 定位继续走 `onGeolocationPermissionsShowPrompt`
  - 摄像头 / 麦克风继续走 `PermissionRequest` 公开常量
  - 网站通知设置保留，拒绝时通过 JS `Notification.permission=denied` 生效

## [1.5.9-dev]

### Added
- 设置 → 高级 → **DNS 管理**：展示系统私人 DNS（DoT/自定义主机名）状态，一键跳转系统设置
- 说明 WebView 无法单独改写 DNS 的原因与可用公共方案（系统私人 DNS / VPN / 代理限制）

### Notes
- Android WebView 无公开 HostResolver/DoH API，应用内自定义 DNS、按标签 DoH 不可用

## [1.5.8-dev]

### Added
- 建议安装的脚本精简为：M3U8媒体下载器、万能网页限制解除
- 标签面板打开时自动定位当前标签卡片，并用主色高光描边
- 网页权限新增「网站通知」（询问 / 允许 / 拒绝），拒绝时固定 `Notification.permission=denied`
- 权限链路补全：摄像头、麦克风、定位、网站通知、剪贴板读写、网站媒体发声

### Changed
- 脚本卡片副标题优先显示元数据 `@description`
- 「内置脚本」分区改名为「建议安装的脚本」

## [1.5.7-dev]

### Changed
- 「内置脚本」标题改为「建议安装的脚本」

## [1.5.6-dev]

### Added
- 脚本列表副标题自动解析用户脚本 `@description`（无则回退代码预览）

## [1.5.5-dev]

### Added
- 建议安装脚本：M3U8媒体下载器、万能网页限制解除、深色模式切换器

## [1.5.4-dev]

### Fixed
- blob 下载 Failed to fetch：document-start 缓存 `URL.createObjectURL`，优先读缓存再 XHR/fetch
- WebView 深浅色同步：`setAlgorithmicDarkeningAllowed` + 背景色 + `Configuration.uiMode`

## [1.5.3-dev]

### Fixed
- 大文件 blob 改为 JS 分片经 `MafariBlob` 写入临时文件，避免 `evaluateJavascript` 超限
- HTML5 视频全屏强制横屏，退出恢复系统方向

## [1.5.2-dev]

### Release
- 版本号提升至 v1.5.2-dev

## [1.5.1-dev]

### Added
- 基于 1.5 主线补齐：文件上传、拍照/录像、视频全屏、blob 下载、网页权限询问弹窗

## [1.5.0-stable]

### Notes
- 主线基线（origin/main / mafari 1.5.0-stable）
