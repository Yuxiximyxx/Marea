package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassMaterial
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassShadows
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassStroke
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 玻璃材质是否真正生效：用户开关打开，且设备支持 RuntimeShader（minSdk 33 基本恒为 true）。
 * 关闭时所有玻璃组件传 backdrop = null，由 miuix-glass 自己退回不透明底色。
 */
@Composable
fun rememberGlassActive(userEnabled: Boolean): Boolean = userEnabled && isRuntimeShaderSupported()

/** 统一点击震动 */
@Composable
fun rememberHaptic(): androidx.compose.ui.hapticfeedback.HapticFeedback =
    LocalHapticFeedback.current

/** 顶栏里的玻璃返回键（液态玻璃，设置等子页面统一）。 */
@Composable
fun GlassBackButton(onClick: () -> Unit) {
    LiquidIconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronBackward,
            description = "返回",
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 玻璃图标按钮 + 点击震动（液态玻璃）。 */
@Composable
fun HapticGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LiquidIconButton(onClick = onClick, modifier = modifier) {
        content()
    }
}

/** 顶栏里的玻璃图标按钮（actions 槽）。 */
@Composable
fun GlassBarAction(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    HapticGlassIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 子页面统一的 OS4 玻璃大标题顶栏（随滚动折叠）。
 *
 * - [backdrop] 必须是「只录制页面内容」的 LayerBackdrop，顶栏本身要放在这个录制层之外
 *   （Scaffold 的 topBar 槽天然满足），否则顶栏会采样到自己，收缩重采样时 RuntimeShader 会崩溃。
 * - [glass] 为 false 时传 null backdrop，退回不透明按钮底。
 */
@Composable
fun PageGlassTopBar(
    title: String,
    backdrop: Backdrop,
    glass: Boolean,
    scrollBehavior: ScrollBehavior,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val blurActive = glass && isRuntimeShaderSupported()
    val fadeDistancePx = with(LocalDensity.current) { 48.dp.toPx() }
    Box {
        // 渐进模糊带：真正对页面内容做自上而下的模糊，色彩取当前主题 surface，
        // 随滚动淡入。顶栏在录制层之外，不会采样到自己。
        if (blurActive) {
            TopProgressiveBlurBand(
                backdrop = backdrop,
                alpha = { -scrollBehavior.state.contentOffset / fadeDistancePx },
                modifier = Modifier.matchParentSize(),
            )
        }
        GlassTopAppBar(
            title = title,
            backdrop = if (glass) backdrop else null,
            scrollBehavior = scrollBehavior,
            // 之前沿用默认 bandBrush：一条不透明的 surface 渐变，看起来就是"一直白色渐变"，
            // 且根本没有模糊。模糊生效时让位给上面的渐进模糊带；
            // 关闭模糊时才退回主题色渐变遮罩。
            bandBrush = if (blurActive) {
                SolidColor(Color.Transparent)
            } else {
                GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
            },
            navigationIcon = { GlassBackButton(onClick = onBack) },
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

/**
 * 玻璃对话框。基于 miuix 的 OverlayDialog，所以参数与原来的 OverlayDialog 基本一致。
 *
 * [backdrop] 传「页面 + 顶栏」的录制层；网页内容（WebView）无法被采样，此时传 null，
 * 对话框会使用不透明底色。
 */
@Composable
fun AppDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop?,
    glass: Boolean,
    title: String? = null,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismissRequest,
        show = show,
        backdrop = if (glass) backdrop else null,
        title = title,
        summary = summary,
        content = content,
    )
}

/** 对话框里的按钮：取代 TextButton，沿用玻璃对话框的按钮样式。 */
@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
) {
    GlassDialogButton(
        onClick = onClick,
        modifier = modifier,
        role = when {
            destructive -> GlassDialogButtonRole.Destructive
            primary -> GlassDialogButtonRole.Primary
            else -> GlassDialogButtonRole.Normal
        },
    ) {
        Text(text = text, style = MiuixTheme.textStyles.button)
    }
}

/**
 * 浮动玻璃面板（底栏 / 弹出面板 / 收藏卡片的底板）。
 *
 * 只能画在「空 Box」上，交互内容作为兄弟层绘制，避免内容进入自己的采样。
 * [backdrop] 为 null 或设备不支持时，退回 [fallbackColor] 实色。
 */
@Composable
fun Modifier.glassSurfacePanel(
    backdrop: Backdrop?,
    cornerRadius: Dp,
    material: GlassMaterial = GlassNavigationBarDefaults.material(),
    stroke: GlassStroke? = GlassNavigationBarDefaults.stroke(),
    fallbackColor: Color = MiuixTheme.colorScheme.surfaceContainer,
): Modifier {
    val shape = GlassShape(cornerRadius)
    return glassPanel(
        backdrop = backdrop,
        shape = shape,
        style = GlassDefaults.Style,
        material = material,
        stroke = stroke,
        shadow = GlassShadows.Float,
        shading = false,
        fallback = Modifier.clip(RoundedCornerShape(cornerRadius)).background(fallbackColor),
    )
}
