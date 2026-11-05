# Changelog

发版时在对应版本下追加条目；GitHub Release 正文读取本文件该版本小节。

## [1.5.13-dev]

- 修复：`onReceivedHttpAuthRequest` 参数顺序与 `HttpAuthHandler.proceed/cancel` 方法名，消除 1.5.12 编译失败

## [1.5.12-dev]

- 代理弹窗：去掉副标题说明；主机/端口同行；新增认证开关（展开用户名/密码，HTTP 挑战自动提交）

## [1.5.11-dev]

- 新增：设置 → 高级 → 代理（HTTP / SOCKS5，官方 ProxyController）
- 精简本更新日志

## [1.5.10-dev]

- 移除：DNS 管理（WebView 无法单独改写 DNS）
- 修复：`PermissionRequest` 不存在的常量导致 1.5.8+ 编译失败

## [1.5.9-dev]

- 新增：DNS 管理说明与系统私人 DNS 跳转（后于 1.5.10 移除）

## [1.5.8-dev]

- 建议安装脚本精简；标签面板定位并高亮当前页；网页权限补全（含网站通知）
- 脚本卡片副标题显示 `@description`

## [1.5.5-dev] ~ [1.5.7-dev]

- 建议安装脚本、分区改名、副标题 `@description`

## [1.5.1-dev] ~ [1.5.4-dev]

- 补齐 1.5：文件上传、拍照/录像、视频全屏、blob 下载、权限询问
- blob 分片与 createObjectURL 缓存；深浅色同步；全屏横屏

## [1.5.0-stable]

- 主线基线
