package io.legado.app.help

import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx

/**
 * exp.10：Marea 空间在 Legado 侧的当前状态。
 *
 * Marea 有普通 / 隐私两个空间，Legado 书架按空间分开（books.space 列过滤），
 * Legado 自身的 WebDAV 配置也按空间各存一份。空间由 Marea 入口经 Intent extra
 * 传入（MainActivity 的 EXTRA_MAREA_SPACE、阅读宿主的 EXTRA_SPACE），无 extra 的
 * 入口（外部 VIEW 直达、进程内其它拉起）回落到这里持久化的上次空间，默认普通。
 *
 * 与 Legado 自身的私密书概念（books.isPrivate / 私密分组 + PrivateReadGate）是两套
 * 语义，互不借用：space 只表达「这本书归哪个 Marea 空间」，不参与 Legado 私密判定。
 */
object MareaSpace {

    const val NORMAL = 0
    const val PRIVATE = 1

    private const val PREF_KEY = "marea_legado_space"

    private val _spaceFlow = MutableStateFlow(loadPersisted())
    val spaceFlow: StateFlow<Int> = _spaceFlow.asStateFlow()

    /** 当前空间（0 普通 / 1 隐私），同步取值，供 DAO 参数与配置映射等非协程场景用 */
    val currentSpace: Int get() = _spaceFlow.value

    val isPrivate: Boolean get() = _spaceFlow.value == PRIVATE

    private fun loadPersisted(): Int = runCatching {
        if (appCtx.getPrefInt(PREF_KEY, NORMAL) == PRIVATE) PRIVATE else NORMAL
    }.getOrDefault(NORMAL)

    /**
     * 入口应用空间。值没变时不做任何事（避免 MainActivity.onResume 反复触发
     * WebDAV 配置失效）；变了才落盘、发流，并让 AppWebDav 丢弃旧空间的授权状态，
     * 下次 ensureConfigured 会按新空间的配置重新初始化。
     */
    fun applySpace(space: Int) {
        val normalized = if (space == PRIVATE) PRIVATE else NORMAL
        if (_spaceFlow.value == normalized) return
        _spaceFlow.value = normalized
        runCatching { appCtx.putPrefInt(PREF_KEY, normalized) }
        AppWebDav.invalidateConfig()
    }
}
