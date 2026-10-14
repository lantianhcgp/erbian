package com.lantianhcgp.erbian.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as UiSize
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * 超椭圆（squircle）形状——FlClash 设计语言核心（设计文档 §七）。
 * 参数方程: |x/a|^n + |y/b|^n = 1，n≈4.5，整圈采样成多边形逼近。
 * corner 等效为同外接圆角的超椭圆；full(>=100) 退化为胶囊。
 */
class SuperellipseShape(private val corner: Float) : Shape {

    override fun createOutline(size: UiSize, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val r = minOf(corner, w / 2f, h / 2f)
        if (r <= 0.5f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val n = 4.5
        val path = Path()
        // 用超椭圆整体轮廓：把矩形四角替换为超椭圆弧
        // 采样四个象限角部曲线
        fun cornerPts(cx: Float, cy: Float, sx: Float, sy: Float): List<Offset> {
            val pts = ArrayList<Offset>(12)
            for (i in 0..10) {
                val t = (Math.PI / 2) * (i / 10.0)
                val ct = kotlin.math.cos(t)
                val st = kotlin.math.sin(t)
                val x = cx + sx * r * sign(ct) * abs(ct).pow(2.0 / n)
                val y = cy + sy * r * sign(st) * abs(st).pow(2.0 / n)
                pts.add(Offset(x, y))
            }
            return pts
        }
        // 左上 → 右上 → 右下 → 左下
        val tl = cornerPts(r, r, -1f, -1f).reversed()
        val tr = cornerPts(w - r, r, 1f, -1f)
        val br = cornerPts(w - r, h - r, 1f, 1f)
        val bl = cornerPts(r, h - r, -1f, 1f).reversed()
        val all = tl + tr + br + bl
        path.moveTo(all[0].x, all[0].y)
        for (p in all.drop(1)) path.lineTo(p.x, p.y)
        path.close()
        return Outline.Generic(path)
    }
}

object ErbianShapes {
    val Card = SuperellipseShape(Corners.MD)
    val Dialog = SuperellipseShape(Corners.XXL)
    val Chip = SuperellipseShape(Corners.SM)
    val Pill = SuperellipseShape(Corners.FULL)
    val SheetTop = SuperellipseShape(Corners.XXL)
}
