package dev.dsh.glasskit

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Marea 材质档位，与 app 模块 AppSettings.blurMode 同值域：
 * 0 = MIUIX 磨砂、1 = Kyant 液态玻璃、2 = 关闭。
 * 落盘位置与 app 一致：SharedPreferences 文件 mafari_settings、键 blur_mode
 * （旧版布尔键 blur 在无 blur_mode 时按 app 的迁移口径折算）。
 */
const val MAREA_BLUR_MIUIX = 0
const val MAREA_BLUR_LIQUID = 1
const val MAREA_BLUR_OFF = 2

val LocalMareaBlurMode = compositionLocalOf { MAREA_BLUR_MIUIX }

private const val PREFS_NAME = "mafari_settings"
private const val KEY_BLUR_MODE = "blur_mode"
private const val KEY_BLUR_LEGACY = "blur"

fun readMareaBlurMode(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return if (prefs.contains(KEY_BLUR_MODE)) {
        prefs.getInt(KEY_BLUR_MODE, MAREA_BLUR_MIUIX)
            .coerceIn(MAREA_BLUR_MIUIX, MAREA_BLUR_OFF)
    } else if (prefs.getBoolean(KEY_BLUR_LEGACY, true)) {
        MAREA_BLUR_MIUIX
    } else {
        MAREA_BLUR_OFF
    }
}

/**
 * 把当前 Marea 材质档位注入组合树，并监听落盘变化实时跟随
 * （用户在 Marea 设置里切档，Legado 界面无需重启即可跟上）。
 */
@Composable
fun ProvideMareaBlurMode(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(readMareaBlurMode(context)) }
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BLUR_MODE || key == KEY_BLUR_LEGACY) {
                mode = readMareaBlurMode(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(LocalMareaBlurMode provides mode) {
        content()
    }
}
