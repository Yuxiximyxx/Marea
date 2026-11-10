# Gecko 分支非内核功能同步说明

基底：mafari-main.zip
来源：mafari-gecko (6).zip

本次仅合并经过隔离确认的通用 UI / 书签历史功能：
- Favorites.kt：个人收藏首页条目、分组管理、排序、HTML/JSON 书签导入及去重。
- HistoryScreen.kt：书签与历史页面的通用交互、导入体验和对话框调整。
- AppIcons.kt：新增通用图标资源。
- AppMaterial.kt、LiquidGlassComponents.kt：新增通用小型玻璃按钮材质能力。

明确未合并 Gecko 内核依赖及其耦合改动：
- GeckoEngine / GeckoView / Gecko 插件与专属页面、配置、生命周期。
- BrowserScreen.kt、BrowserState.kt、SettingsScreen.kt 等含有内核耦合的混合文件。
- Gecko 专用 Gradle / Maven / ProGuard / workflow / release 配置。
- Gecko 专用更新通道、代理桥接、诊断和扩展管理。
- Cookie 管理等虽然可能跨内核，但在来源分支中与 Gecko 设置路由耦合的改动。

注意：此包是源码级选择性同步，不包含 Git 历史，也未在此环境执行 Android Gradle 构建。请在 main 分支 CI 验证后再合并发布。
