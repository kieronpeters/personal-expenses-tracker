package com.personalexpenses.tracker.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.personalexpenses.tracker.R
import com.personalexpenses.tracker.util.TrendPoint
import kotlin.math.ceil
import kotlin.math.min

/**
 * Lightweight bar chart used by the reports screen. It intentionally has no
 * external dependencies: the app draws daily (month view) or monthly (year
 * view) expense bars with rounded corners.
 */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = sp(1f)
    }

    private var points: List<TrendPoint> = emptyList()
    private val barRect = RectF()

    init {
        val fallbackBar = ContextCompat.getColor(context, R.color.brand_primary)
        val fallbackLabel = ContextCompat.getColor(context, R.color.brand_secondary)
        barPaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary,
            fallbackBar
        )
        labelPaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            fallbackLabel
        )
        gridPaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            fallbackLabel
        )
    }

    fun submitData(newPoints: List<TrendPoint>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val right = width - paddingRight.toFloat()
        val top = paddingTop.toFloat()
        val bottom = height - paddingBottom.toFloat()
        if (right <= left || bottom <= top || points.isEmpty()) return

        val labelSpace = labelPaint.textSize * 2.2f
        val chartBottom = bottom - labelSpace
        if (chartBottom <= top) return

        canvas.drawLine(left, chartBottom, right, chartBottom, gridPaint)

        val maxValue = points.maxOf { it.expenseCents }
        if (maxValue <= 0L) return

        val slotWidth = (right - left) / points.size
        val barWidth = (slotWidth * 0.55f).coerceAtLeast(2f)
        val chartHeight = chartBottom - top
        val step = ceil(points.size / 10.0).toInt().coerceAtLeast(1)
        val fontMetrics = labelPaint.fontMetrics
        val maxRadius = sp(6f)

        points.forEachIndexed { index, point ->
            val ratio = point.expenseCents.toDouble() / maxValue
            val barHeight = (chartHeight * ratio).toFloat()
            val barLeft = left + slotWidth * index + (slotWidth - barWidth) / 2f
            barRect.set(barLeft, chartBottom - barHeight, barLeft + barWidth, chartBottom)
            val radius = min(barWidth / 2f, maxRadius)
            canvas.drawRoundRect(barRect, radius, radius, barPaint)

            val drawLabel = points.size <= 10 || index % step == 0 || index == points.lastIndex
            if (drawLabel) {
                val centerX = barLeft + barWidth / 2f
                val baselineY = bottom - fontMetrics.descent
                canvas.drawText(point.label, centerX, baselineY, labelPaint)
            }
        }
    }

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )
}
