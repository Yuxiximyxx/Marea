package dev.dsh.miuixbrowser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop as LiquidBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as backdropBlur
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ======================================================================
 * 液态玻璃（Kyant0/AndroidLiquidGlass）应用范围
 *
 * 设置 →「高级材质」选「liquid glass」时，下面这些组件改用 Kyant 的 drawBackdrop：
 * 弹出菜单（dropdown / popup）、底部 sheet、对话框、开关（Switch）；顶栏按钮 / FAB / 胶囊按钮见 LiquidGlassKit.kt。
 * 其它模式（miuix-glass / 关闭模糊）原样走 miuix 组件。
 *
 * - [LocalBlurMode]：当前模糊模式，由 MainActivity 提供。
 * - [LocalLiquidBackdrop]：当前页面的 Kyant 采样层，由各页面在 Scaffold 外提供；为 null 时（网页态 / 不支持）退回 miuix。
 * ====================================================================== */

val LocalBlurMode = compositionLocalOf { BLUR_MIUIX }
val LocalLiquidBackdrop = compositionLocalOf<LiquidBackdrop?> { null }

/** 液态玻璃模式下返回当前页面的 Kyant 采样层，否则 null。 */
@Composable
private fun activeLiquidBackdrop(miuixBackdrop: Backdrop?): LiquidBackdrop? =
    if (LocalBlurMode.current == BLUR_LIQUID && miuixBackdrop != null) LocalLiquidBackdrop.current else null

/** 演示项目（catalog）的容器色：浅色 #FAFAFA@60%，深色 #121212@40%。 */
private fun demoContainerColor(dark: Boolean): Color =
    if (dark) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.6f)

/** 液态玻璃面板（下拉菜单 / 弹出菜单）：与 catalog 的玻璃卡片一致——vibrancy + 模糊 + 折射 + 纯白高光。 */
@Composable
private fun Modifier.liquidPanel(
    backdrop: LiquidBackdrop,
    shape: Shape,
    blur: Dp = 4.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
): Modifier {
    val container = demoContainerColor(isAppDarkTheme())
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            backdropBlur(blur.toPx())
            lens(refractionHeight.toPx(), refractionAmount.toPx(), depthEffect = true, chromaticAberration = true)
        },
        highlight = { Highlight.Plain },
        onDrawSurface = { drawRect(container) },
    )
}

/* ------------------------------ 对话框 ------------------------------ */

/** 与 GlassDialog 同参的对话框；液态玻璃模式下面板改为 Kyant 液态玻璃。 */
@Composable
fun AppGlassDialog(
    onDismissRequest: () -> Unit,
    show: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val liquid = activeLiquidBackdrop(backdrop)
    if (liquid == null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = backdrop,
            modifier = modifier,
            title = title,
            summary = summary,
            onDismissFinished = onDismissFinished,
            content = content,
        )
        return
    }
    val containerColor = GlassDialogDefaults.containerColor()
    val dark = isAppDarkTheme()
    val panelColor = demoContainerColor(dark)
    OverlayDialog(
        show = show,
        modifier = modifier,
        title = title,
        titleColor = DialogDefaults.titleColor(),
        summary = summary,
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = containerColor,
        enableWindowDim = true,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        dimColor = GlassDialogDefaults.ScrimColor,
        surface = {
            val shape = RoundedCornerShape(cornerRadius)
            val scrim = scrimColor
            Modifier.drawBackdrop(
                backdrop = liquid,
                shape = { shape },
                // 对齐 catalog 的 DialogContent：提亮 + 加饱和 + 模糊 + 带深度的折射 + 纯白高光
                effects = {
                    colorControls(
                        brightness = if (dark) 0f else 0.2f,
                        saturation = 1.5f,
                    )
                    backdropBlur(if (dark) 8f.dp.toPx() else 16f.dp.toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                },
                highlight = { Highlight.Plain },
                onDrawSurface = {
                    drawRect(scrim.value)
                    drawRect(panelColor)
                },
            )
        },
        content = content,
    )
}

/* ------------------------------ 底部 sheet ------------------------------ */

/** 与 GlassBottomSheet 同参的底部 sheet；液态玻璃模式下面板改为 Kyant 液态玻璃。 */
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    show: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enableWindowDim: Boolean = true,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val liquid = activeLiquidBackdrop(backdrop)
    if (liquid == null) {
        GlassBottomSheet(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = backdrop,
            modifier = modifier,
            enableWindowDim = enableWindowDim,
            title = title,
            startAction = startAction,
            endAction = endAction,
            onDismissFinished = onDismissFinished,
            content = content,
        )
        return
    }
    val panelColor = demoContainerColor(isAppDarkTheme())
    OverlayBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = GlassBottomSheetDefaults.containerColor(),
        enableWindowDim = enableWindowDim,
        cornerRadius = BottomSheetDefaults.cornerRadius,
        sheetMaxWidth = BottomSheetDefaults.maxWidth,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = BottomSheetDefaults.outsideMargin,
        insideMargin = BottomSheetDefaults.insideMargin,
        dragHandleColor = BottomSheetDefaults.dragHandleColor(),
        dimColor = GlassBottomSheetDefaults.ScrimColor,
        surface = {
            val shape = if (isOverscroll) {
                RectangleShape
            } else {
                RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
            }
            val scrim = scrimColor
            Modifier.drawBackdrop(
                backdrop = liquid,
                shape = { shape },
                // 对齐 catalog 的玻璃面板（sheet）：vibrancy + 模糊 + 折射 + 纯白高光
                effects = {
                    vibrancy()
                    backdropBlur(8f.dp.toPx())
                    if (!isOverscroll) lens(16f.dp.toPx(), 32f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                },
                highlight = { Highlight.Plain },
                onDrawSurface = {
                    drawRect(scrim.value)
                    drawRect(panelColor)
                },
            )
        },
        content = content,
    )
}

/* ------------------------------ 下拉菜单 ------------------------------ */

/** 与 GlassDropdownPreference 同参的下拉选择行；液态玻璃模式下弹出面板改为 Kyant 液态玻璃。 */
@Composable
fun AppDropdownPreference(
    onSelectedIndexChange: (Int) -> Unit,
    items: List<String>,
    selectedIndex: Int,
    title: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showValue: Boolean = true,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin,
    visuals: GlassPopupVisuals = GlassPopupDefaults.visuals(),
    marqueeItems: Boolean = false,
) {
    val liquid = activeLiquidBackdrop(backdrop)
    GlassDropdownPreference(
        onSelectedIndexChange = onSelectedIndexChange,
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        showValue = showValue,
        summary = summary,
        startAction = startAction,
        insideMargin = insideMargin,
        visuals = visuals,
        marqueeItems = marqueeItems,
        surface = liquid?.let { liquidPopupSurface(it) },
    )
}

/** 与 GlassPopup 同参的弹出菜单；液态玻璃模式下面板改为 Kyant 液态玻璃。 */
@Composable
fun BoxScope.AppGlassPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: androidx.compose.ui.geometry.Rect,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val liquid = activeLiquidBackdrop(backdrop)
    GlassPopup(
        show = show,
        onDismissRequest = onDismissRequest,
        anchorBounds = anchorBounds,
        backdrop = backdrop,
        modifier = modifier,
        surface = liquid?.let { liquidPopupSurface(it) },
        content = content,
    )
}

private fun liquidPopupSurface(liquid: LiquidBackdrop): @Composable BoxScope.(GlassShape) -> Unit = { _ ->
    LiquidPopupSurface(liquid)
}

/** 弹出面板的背景层：面板本身已按轮廓裁剪，这里只负责填满。 */
@Composable
private fun BoxScope.LiquidPopupSurface(liquid: LiquidBackdrop) {
    Box(
        Modifier
            .matchParentSize()
            .liquidPanel(
                backdrop = liquid,
                shape = RoundedCornerShape(GlassDropdownDefaults.CornerRadius),
            ),
    )
}

/* ------------------------------ 开关 ------------------------------ */

/** 与 SwitchPreference 同参；液态玻璃模式下开关换成 [LiquidSwitch]。 */
@Composable
fun AppSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    if (LocalBlurMode.current != BLUR_LIQUID) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            modifier = modifier,
            summary = summary,
            enabled = enabled,
        )
        return
    }
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        endActions = {
            LiquidSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
    )
}

/**
 * 液态玻璃开关：直接移植自 Kyant0/AndroidLiquidGlass catalog 的 LiquidToggle。
 * 静止时是实心白色滑块；按下 / 拖动时滑块放大并变成透明的液态玻璃，折射下面的轨道（和页面背景）。
 * [backdrop] 为页面采样层，缺省取 [LocalLiquidBackdrop]；为 null 时只折射轨道。
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: LiquidBackdrop? = LocalLiquidBackdrop.current,
) {
    val dark = isAppDarkTheme()
    val accentColor = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val trackColor = if (dark) Color(0xFF787880).copy(0.36f) else Color(0xFF787878).copy(0.2f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    // 回调里要读到最新的 checked / onCheckedChange
    val latestChecked by rememberUpdatedState(checked)
    val latestOnChange by rememberUpdatedState(onCheckedChange)
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    latestOnChange(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (latestChecked) 0f else 1f
                    latestOnChange(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberKyantBackdrop()
    // 滑块折射：页面背景 + 轨道（轨道按按压进度缩放，对应 catalog 的写法）
    val trackSampler = rememberBackdrop(trackBackdrop) { drawBackdrop ->
        val progress = dampedDragAnimation.pressProgress
        val scaleX = lerp(2f / 3f, 0.75f, progress)
        val scaleY = lerp(0f, 0.75f, progress)
        scale(scaleX, scaleY) {
            drawBackdrop()
        }
    }
    val thumbBackdrop = if (backdrop != null) rememberCombinedBackdrop(backdrop, trackSampler) else trackSampler

    Box(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .kyantLayerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind {
                    val f = dampedDragAnimation.value
                    drawRect(lerpColor(trackColor, accentColor, f))
                }
                .size(64f.dp, 28f.dp),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val f = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, f)
                        else lerp(-padding, -(padding + dragWidth), f)
                }
                .semantics { role = Role.Switch }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        backdropBlur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f),
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(40f.dp, 24f.dp),
        )
    }
}
