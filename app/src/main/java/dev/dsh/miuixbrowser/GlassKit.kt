package dev.dsh.miuixbrowser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** 顶栏里的玻璃返回键。必须放在 GlassTopAppBar 的 navigationIcon 里，才会继承顶栏材质。 */
@Composable
fun GlassBackButton(onClick: () -> Unit) {
    GlassIconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronBackward,
            contentDescription = "返回",
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
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
    GlassIconButton(onClick = onClick, modifier = modifier) {
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
    val isContentScrolled by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.contentOffset < 0f }
    }
    // 内容滚到顶栏下方时，渐进模糊淡入；回到顶部时淡出。
    val blurAlpha by animateFloatAsState(
        targetValue = if (isContentScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "pageTopBarProgressiveBlur",
    )
    val overhang = GlassTopAppBarDefaults.BandOverhang
    val surface = MiuixTheme.colorScheme.surface
    Box {
        if (glass) {
            // 渐进模糊层：位于录制层之外，采样页面内容；高度 = 顶栏 + 下探 overhang。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val extra = overhang.roundToPx()
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = blurAlpha }
                    .progressiveTopBar(
                        backdrop = backdrop,
                        tint = surface.copy(alpha = 0.18f),
                        radius = 40f,
                        curve = 0.15f,
                    ),
            )
        }
        GlassTopAppBar(
            title = title,
            isContentScrolled = isContentScrolled,
            backdrop = if (glass) backdrop else null,
            scrollBehavior = scrollBehavior,
            // 玻璃开启时由上面的渐进模糊取代纯色渐变遮罩；关闭时保留原遮罩保证可读性。
            bandBrush = if (glass) SolidColor(Color.Transparent) else GlassTopAppBarDefaults.bandBrush(surface),
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
