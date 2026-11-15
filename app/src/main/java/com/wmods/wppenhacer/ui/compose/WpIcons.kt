package com.wmods.wppenhacer.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object WpIcons {
    val Home: ImageVector by lazy {
        icon("Home") {
            polygon(10f to 20f, 10f to 14f, 14f to 14f, 14f to 20f, 19f to 20f, 19f to 12f, 22f to 12f, 12f to 3f, 2f to 12f, 5f to 12f, 5f to 20f)
        }
    }

    val Chat: ImageVector by lazy {
        icon("Chat") {
            polygon(4f to 4f, 20f to 4f, 22f to 6f, 22f to 16f, 20f to 18f, 9f to 18f, 4f to 22f, 4f to 18f, 2f to 16f, 2f to 6f)
            polygon(6f to 8f, 18f to 8f, 18f to 9.5f, 6f to 9.5f)
            polygon(6f to 12f, 14f to 12f, 14f to 13.5f, 6f to 13.5f)
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            polygon(5f to 11f, 19f to 11f, 19f to 21f, 5f to 21f)
            polygon(7.5f to 11f, 7.5f to 8f, 9f to 5f, 12f to 4f, 15f to 5f, 16.5f to 8f, 16.5f to 11f, 14.5f to 11f, 14.5f to 8.5f, 13.5f to 6.5f, 12f to 6f, 10.5f to 6.5f, 9.5f to 8.5f, 9.5f to 11f)
        }
    }

    val Image: ImageVector by lazy {
        icon("Image") {
            polygon(3f to 5f, 21f to 5f, 21f to 19f, 3f to 19f)
            polygon(5f to 7f, 19f to 7f, 19f to 17f, 5f to 17f)
            polygon(6f to 16f, 10f to 11f, 13f to 15f, 15f to 13f, 18f to 16f)
            polygon(circle(8.5f, 9.5f, 1.6f))
        }
    }

    val Dashboard: ImageVector by lazy {
        icon("Dashboard") {
            polygon(3f to 3f, 11f to 3f, 11f to 11f, 3f to 11f)
            polygon(13f to 3f, 21f to 3f, 21f to 9f, 13f to 9f)
            polygon(13f to 11f, 21f to 11f, 21f to 21f, 13f to 21f)
            polygon(3f to 13f, 11f to 13f, 11f to 21f, 3f to 21f)
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            polygon(9f to 4f, 10f to 3f, 14f to 3f, 15f to 4f, 15f to 12f, 14f to 13f, 10f to 13f, 9f to 12f)
            polygon(6f to 11f, 8f to 11f, 8f to 12f, 10f to 14.5f, 14f to 14.5f, 16f to 12f, 16f to 11f, 18f to 11f, 18f to 12.5f, 15f to 16f, 13f to 17.2f, 13f to 20f, 15f to 20f, 15f to 21.5f, 9f to 21.5f, 9f to 20f, 11f to 20f, 11f to 17.2f, 9f to 16f, 6f to 12.5f)
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            val teeth = 8
            val step = 2 * PI / teeth
            val pts = ArrayList<Pair<Float, Float>>()
            for (t in 0 until teeth) {
                val a = step * t
                pts += point(12f, 12f, 7.2f, a - step * 0.30)
                pts += point(12f, 12f, 9.8f, a - step * 0.18)
                pts += point(12f, 12f, 9.8f, a + step * 0.18)
                pts += point(12f, 12f, 7.2f, a + step * 0.30)
            }
            polygon(pts)
            polygon(circle(12f, 12f, 3.2f))
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            polygon(circle(10f, 10f, 6.5f))
            polygon(circle(10f, 10f, 4.5f))
            polygon(14.2f to 15.6f, 15.6f to 14.2f, 21f to 19.6f, 19.6f to 21f)
        }
    }

    val Palette: ImageVector by lazy {
        icon("Palette") {
            polygon(circle(12f, 12f, 10f))
            polygon(circle(7.8f, 11f, 1.7f, 16))
            polygon(circle(10.5f, 7f, 1.7f, 16))
            polygon(circle(15.5f, 7f, 1.7f, 16))
            polygon(circle(18f, 11f, 1.7f, 16))
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            polygon(circle(12f, 12f, 10f))
            polygon(11f to 7f, 13f to 7f, 13f to 9f, 11f to 9f)
            polygon(11f to 11f, 13f to 11f, 13f to 17f, 11f to 17f)
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            polygon(circle(12f, 12f, 10f))
            polygon(10f to 17f, 5f to 12f, 6.41f to 10.59f, 10f to 14.17f, 17.59f to 6.59f, 19f to 8f)
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            polygon(12f to 2f, 23f to 21f, 1f to 21f)
            polygon(11f to 10f, 13f to 10f, 13f to 15f, 11f to 15f)
            polygon(11f to 17f, 13f to 17f, 13f to 19f, 11f to 19f)
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            val pts = ArrayList<Pair<Float, Float>>()
            val n = 22
            for (i in 0..n) pts += point(12f, 12f, 9f, (PI / 4) + (1.5 * PI) * i / n)
            for (i in n downTo 0) pts += point(12f, 12f, 6.6f, (PI / 4) + (1.5 * PI) * i / n)
            polygon(pts)
            polygon(14f to 3f, 21f to 3f, 21f to 10f)
        }
    }

    val Update: ImageVector by lazy {
        icon("Update") {
            polygon(12f to 3f, 19f to 11f, 14f to 11f, 14f to 20f, 10f to 20f, 10f to 11f, 5f to 11f)
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            val pts = ArrayList<Pair<Float, Float>>()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 10f else 4.2f
                pts += point(12f, 12.5f, r, -PI / 2 + i * PI / 5)
            }
            polygon(pts)
        }
    }

    val Back: ImageVector by lazy {
        icon("Back") {
            polygon(20f to 11f, 7.8f to 11f, 13.4f to 5.4f, 12f to 4f, 4f to 12f, 12f to 20f, 13.4f to 18.6f, 7.8f to 13f, 20f to 13f)
        }
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd, pathBuilder = block)
        }.build()

    private fun point(cx: Float, cy: Float, r: Float, angle: Double) =
        (cx + r * cos(angle)).toFloat() to (cy + r * sin(angle)).toFloat()

    private fun circle(cx: Float, cy: Float, r: Float, n: Int = 28): List<Pair<Float, Float>> =
        List(n) { point(cx, cy, r, 2 * PI * it / n) }

    private fun PathBuilder.polygon(vararg pts: Pair<Float, Float>) = polygon(pts.toList())

    private fun PathBuilder.polygon(pts: List<Pair<Float, Float>>) {
        moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second)
        close()
    }
}
