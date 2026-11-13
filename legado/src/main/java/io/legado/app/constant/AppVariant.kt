package io.legado.app.constant

/**
 * 应用构建变体标识（由签名判定，见 [AppConst.appInfo]）。
 * 原与 Legado 更新检查同居 help/update 包，exp.17 拆更新机制后迁至此独立保留。
 */
enum class AppVariant {
    OFFICIAL,
    BETA_RELEASE,
    ALL,
    UNKNOWN;

}
