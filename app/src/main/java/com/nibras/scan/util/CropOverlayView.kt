package com.nibras.scan.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Overlays a draggable quadrilateral (4 corner handles) on top of a captured document
 * photo, so the user can align the crop to the page's real edges. Corners are stored and
 * reported in this view's own coordinate space; [CropActivity] converts them into the
 * source bitmap's pixel space using the ImageView's displayed image rect.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Clockwise starting top-left: TL, TR, BR, BL
    private var corners = FloatArray(8)
    private var imageRect = RectF()
    private var activeHandle = -1
    private val handleRadius = 28f
    private val touchSlop = 70f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2FB0FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#332FB0FF")
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2FB0FF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    /** Sets the rectangle (in this view's coordinates) where the image is actually drawn,
     * and resets the crop quad to a small inset within it. */
    fun setImageRect(rect: RectF) {
        imageRect = rect
        val insetX = rect.width() * 0.08f
        val insetY = rect.height() * 0.08f
        corners = floatArrayOf(
            rect.left + insetX, rect.top + insetY,
            rect.right - insetX, rect.top + insetY,
            rect.right - insetX, rect.bottom - insetY,
            rect.left + insetX, rect.bottom - insetY
        )
        invalidate()
    }

    fun getCorners(): FloatArray = corners.copyOf()

    /** Overrides the current quad directly (used to apply an auto-detected result). */
    fun setCorners(points: FloatArray) {
        require(points.size == 8)
        corners = points.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (corners.all { it == 0f }) return

        val path = Path().apply {
            moveTo(corners[0], corners[1])
            lineTo(corners[2], corners[3])
            lineTo(corners[4], corners[5])
            lineTo(corners[6], corners[7])
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)

        for (i in 0 until 4) {
            val x = corners[i * 2]
            val y = corners[i * 2 + 1]
            canvas.drawCircle(x, y, handleRadius, handlePaint)
            canvas.drawCircle(x, y, handleRadius, handleStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = findNearestHandle(event.x, event.y)
                return activeHandle != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != -1) {
                    val minX = if (imageRect.isEmpty) 0f else imageRect.left
                    val maxX = if (imageRect.isEmpty) width.toFloat() else imageRect.right
                    val minY = if (imageRect.isEmpty) 0f else imageRect.top
                    val maxY = if (imageRect.isEmpty) height.toFloat() else imageRect.bottom
                    corners[activeHandle * 2] = event.x.coerceIn(minX, maxX)
                    corners[activeHandle * 2 + 1] = event.y.coerceIn(minY, maxY)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = -1
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findNearestHandle(x: Float, y: Float): Int {
        var closest = -1
        var closestDist = touchSlop
        for (i in 0 until 4) {
            val dx = corners[i * 2] - x
            val dy = corners[i * 2 + 1] - y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < closestDist) {
                closestDist = dist
                closest = i
            }
        }
        return closest
    }
}
