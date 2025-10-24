package com.example.bloodcelldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var results: List<YoloDetection>? = null
    private var sourceImageWidth = 0
    private var sourceImageHeight = 0

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    fun setDetections(results: List<YoloDetection>, sourceW: Int, sourceH: Int) {
        this.results = results
        this.sourceImageWidth = sourceW
        this.sourceImageHeight = sourceH
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val detections = results ?: return
        if (sourceImageWidth == 0 || sourceImageHeight == 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val scale = min(viewW / sourceImageWidth, viewH / sourceImageHeight)
        val xOffset = (viewW - sourceImageWidth * scale) / 2f
        val yOffset = (viewH - sourceImageHeight * scale) / 2f

        val colors = listOf(
            Color.RED,
            Color.GREEN,
            Color.CYAN,
            Color.YELLOW,
            Color.MAGENTA,
            Color.BLUE
        )

        detections.forEachIndexed { idx, detection ->
            val box = detection.box
            val left = box.left * scale + xOffset
            val top = box.top * scale + yOffset
            val right = box.right * scale + xOffset
            val bottom = box.bottom * scale + yOffset
            val rect = RectF(left, top, right, bottom)

            boxPaint.color = colors[idx % colors.size]
            canvas.drawRect(rect, boxPaint)

            val labelText = "${detection.label} ${(detection.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize

            val bgRect = RectF(
                left,
                top - textHeight - 8,
                left + textWidth + 16,
                top
            )
            canvas.drawRect(bgRect, textBackgroundPaint)
            canvas.drawText(labelText, left + 8, top - 10, textPaint)
        }
    }
}
