package dev.dsh.miuixbrowser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 自绘 SVG 图标：24×24 视口，描边风格，与 miuix 图标同尺寸，颜色由 Icon 的 tint 决定。 */
object AppIcons {

    private fun stroked(name: String, vararg paths: String, width: Float = 1.9f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** iOS 风格标签页：前方圆角方块 + 后方露出一个 L 形轮廓。 */
    val Tabs: ImageVector by lazy {
        stroked(
            "Tabs",
            "M11.8,8.8H17.8A3,3 0 0 1 20.8,11.8V17.8A3,3 0 0 1 17.8,20.8H11.8A3,3 0 0 1 8.8,17.8V11.8A3,3 0 0 1 11.8,8.8Z",
            "M6.2,15.2A3,3 0 0 1 3.2,12.2V6.2A3,3 0 0 1 6.2,3.2H12.2A3,3 0 0 1 15.2,6.2V6.9",
        )
    }

    /** 脚本：尖括号 + 斜杠。 */
    val Script: ImageVector by lazy {
        stroked(
            "Script",
            "M8.5,7.5L3.5,12L8.5,16.5",
            "M15.5,7.5L20.5,12L15.5,16.5",
            "M13.6,5L10.4,19",
        )
    }

    /** 发送到桌面：圆角屏幕 + 向下的箭头。 */
    val SendToDesktop: ImageVector by lazy {
        stroked(
            "SendToDesktop",
            "M4,6.5A2.5,2.5 0 0 1 6.5,4H17.5A2.5,2.5 0 0 1 20,6.5V17.5A2.5,2.5 0 0 1 17.5,20H6.5A2.5,2.5 0 0 1 4,17.5Z",
            "M12,8.2V15",
            "M8.9,12L12,15.1L15.1,12",
        )
    }
}
