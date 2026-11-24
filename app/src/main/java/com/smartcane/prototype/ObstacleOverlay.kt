package com.smartcane.prototype

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

internal val Zone.labelId: Int
    get() = when (this) {
        Zone.FAR_LEFT -> R.string.far_left
        Zone.LEFT -> R.string.left
        Zone.CENTER -> R.string.center
        Zone.RIGHT -> R.string.right
        Zone.FAR_RIGHT -> R.string.far_right
        Zone.TOP -> R.string.top
        Zone.BOTTOM -> R.string.bottom
    }

internal data class Obstacle(val box: RectF, val zone: Zone)

internal class ObstacleOverlay(context: Context) : View(context) {
    var labelTopInset = 0
        set(value) {
            field = value
            invalidate()
        }

    var gridLayout = ObstacleGrid.ROWS
        set(value) {
            field = value
            invalidate()
        }

    var obstacles: List<Obstacle> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val labels = Zone.entries.associateWith { context.getString(it.labelId) }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        strokeWidth = 1.5f * density
        setShadowLayer(2f * density, 0f, 0f, Color.BLACK)
    }
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        setShadowLayer(2f * density, 0f, 0f, Color.BLACK)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelBackground = Paint().apply { color = 0xDD172025.toInt() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gridLayout == ObstacleGrid.COLUMNS) drawColumns(canvas) else drawRows(canvas)
        for (obstacle in obstacles) canvas.drawRect(obstacle.box, box)
    }

    private fun drawColumns(canvas: Canvas) {
        val zones = gridLayout.zones
        val columnWidth = width.toFloat() / zones.size
        val headerHeight = labelTopInset + 32f * density
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, labelBackground)
        val baseline = (headerHeight + labelTopInset - label.ascent() - label.descent()) / 2f
        zones.forEachIndexed { index, zone ->
            canvas.drawText(labels.getValue(zone), columnWidth * (index + 0.5f), baseline, label)
        }
        for (column in 1 until zones.size) {
            val x = column * columnWidth
            canvas.drawLine(x, 0f, x, height.toFloat(), grid)
        }
    }

    private fun drawRows(canvas: Canvas) {
        val zones = gridLayout.zones
        val rowHeight = height.toFloat() / zones.size
        val padding = 8f * density
        val labelHeight = 24f * density
        zones.forEachIndexed { index, zone ->
            val boundary = index * rowHeight
            if (index > 0) canvas.drawLine(0f, boundary, width.toFloat(), boundary, grid)
            val text = labels.getValue(zone)
            val top = maxOf(boundary, labelTopInset.toFloat()) + padding
            val labelWidth = label.measureText(text) + 2 * padding
            canvas.drawRect(padding, top, padding + labelWidth, top + labelHeight, labelBackground)
            val baseline = top + (labelHeight - label.ascent() - label.descent()) / 2f
            canvas.drawText(text, padding + labelWidth / 2f, baseline, label)
        }
    }
}
