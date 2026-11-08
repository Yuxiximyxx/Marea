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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.effects.lens
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
 * 设置 →「模糊效果」选「liquid glass」时，下面这些组件改用 Kyant 的 drawBackdrop：
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

/** 液态玻璃面板：折射 + 模糊 + 提亮，再叠一层主题色。 */
@Composable
private fun Modifier.liquidPanel(
    backdrop: LiquidBackdrop,
    shape: Shape,
    blur: Dp = 12.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 24.dp,
    tintAlpha: Float = 0.5f,
    extraTint: Color = Color.Transparent,
): Modifier {
    val tint = MiuixTheme.colorScheme.surface.copy(alpha = tintAlpha)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            backdropBlur(blur.toPx())
            lens(refractionHeight.toPx(), refractionAmount.toPx())
        },
        onDrawSurface = {
            drawRect(tint)
            if (extraTint != Color.Transparent) drawRect(extraTint)
        },
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
    val surfaceColor = MiuixTheme.colorScheme.surface
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
                effects = {
                    vibrancy()
                    backdropBlur(16f.dp.toPx())
                    lens(14f.dp.toPx(), 28f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(scrim.value)
                    drawRect(surfaceColor.copy(alpha = 0.55f))
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
    val surfaceColor = MiuixTheme.colorScheme.surface
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
                effects = {
                    vibrancy()
                    backdropBlur(16f.dp.toPx())
                    if (!isOverscroll) lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(scrim.value)
                    drawRect(surfaceColor.copy(alpha = 0.55f))
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
                blur = 10.dp,
                refractionHeight = 10.dp,
                refractionAmount = 20.dp,
                tintAlpha = 0.5f,
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
 * 液态玻璃开关（对齐 Kyant catalog 的 LiquidToggle）：
 * 静止时是实心白色滑块；按下 / 拖动时滑块放大并变成透明的液态玻璃，折射下面的轨道颜色。
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackWidth = 58.dp
    val trackHeight = 30.dp
    val thumbWidth = 38.dp
    val thumbHeight = 24.dp
    val inset = 3.dp
    val travel = trackWidth - thumbWidth - inset * 2

    val trackBackdrop = rememberKyantBackdrop()
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "liquidSwitchFraction",
    )
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "liquidSwitchPress",
    )
    val colors = MiuixTheme.colorScheme
    val offColor = colors.onSurface.copy(alpha = 0.16f)
    val onColor = colors.primary
    var dragged by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Switch }
            .pointerInput(enabled, checked) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onCheckedChange(!checked) },
                )
            }
            .pointerInput(enabled, checked) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { pressed = true; dragged = 0f },
                    onDragEnd = {
                        pressed = false
                        if (dragged > 8f && !checked) onCheckedChange(true)
                        if (dragged < -8f && checked) onCheckedChange(false)
                    },
                    onDragCancel = { pressed = false },
                    onHorizontalDrag = { _, dx -> dragged += dx },
                )
            },
    ) {
        // 轨道：被 trackBackdrop 录制，供滑块折射
        Box(
            modifier = Modifier
                .fillMaxSize()
                .kyantLayerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind { drawRect(lerpColor(offColor, onColor, fraction)) },
        )
        // 滑块：放在录制层之外
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((inset + travel * fraction).toPx().roundToInt(), 0) }
                .size(thumbWidth, thumbHeight)
                .drawBackdrop(
                    backdrop = trackBackdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        backdropBlur(6f.dp.toPx() * press)
                        lens(5f.dp.toPx() * press, 10f.dp.toPx() * press)
                    },
                    layerBlock = {
                        val s = lerp(1f, 1.35f, press)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - 0.85f * press)) },
                ),
        )
    }
}
