package com.nibras.scan.util

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Automatically finds a document-like quadrilateral in a photo: grayscale -> blur -> Canny
 * edges -> contours -> pick the largest 4-sided shape that looks like a page.
 *
 * This is a best-effort heuristic, not perfect detection - it works well for a page with
 * decent contrast against its background (e.g. paper on a table) and can fail on cluttered
 * backgrounds or very low contrast. The caller should always fall back to a sensible default
 * quad when this returns null, and let the user drag the corners regardless.
 *
 * NOTE: OpenCV's Java/Kotlin API (Point.x/y, Size.width/height, contourArea, etc.) works in
 * Double throughout - this whole function stays in Double and only converts to Float for the
 * final FloatArray result, to avoid Kotlin type-mismatch compile errors.
 */
object DocumentDetector {

    /** Returns 4 points (TL, TR, BR, BL - 8 floats) in [bitmap] pixel coordinates, or null. */
    fun detect(bitmap: Bitmap): FloatArray? {
        val src = Mat()
        val small = Mat()
        val gray = Mat()
        val edges = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            val longSide: Double = max(src.width(), src.height()).toDouble()
            val scale: Double = if (longSide > 800.0) 800.0 / longSide else 1.0
            Imgproc.resize(src, small, Size(), scale, scale)

            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            Imgproc.dilate(edges, edges, Mat.ones(3, 3, CvType.CV_8U))

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(
                edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea: Double = (small.width() * small.height()).toDouble()
            var bestQuad: Array<Point>? = null
            var bestArea = 0.0

            for (c in contours) {
                val c2f = MatOfPoint2f(*c.toArray())
                val peri: Double = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                if (approx.total() == 4L) {
                    val area: Double = Imgproc.contourArea(approx)
                    // Require it to cover a meaningful chunk of the frame, so we don't lock
                    // onto a small rectangle in the background (a book, a phone, etc).
                    if (area > bestArea && area > imageArea * 0.15) {
                        bestArea = area
                        bestQuad = approx.toArray()
                    }
                }
                c2f.release()
                approx.release()
                c.release()
            }

            val scaleBack: Double = 1.0 / scale
            return bestQuad?.let { orderCorners(it, scaleBack) }
        } catch (t: Throwable) {
            return null
        } finally {
            src.release(); small.release(); gray.release(); edges.release()
        }
    }

    /** Sorts 4 arbitrary points into TL, TR, BR, BL order and scales back to full-size pixels. */
    private fun orderCorners(pts: Array<Point>, scaleBack: Double): FloatArray {
        val bySum = pts.sortedBy { it.x + it.y }
        val tl = bySum.first()
        val br = bySum.last()
        val byDiff = pts.sortedBy { it.y - it.x }
        val tr = byDiff.first()
        val bl = byDiff.last()
        return floatArrayOf(
            (tl.x * scaleBack).toFloat(), (tl.y * scaleBack).toFloat(),
            (tr.x * scaleBack).toFloat(), (tr.y * scaleBack).toFloat(),
            (br.x * scaleBack).toFloat(), (br.y * scaleBack).toFloat(),
            (bl.x * scaleBack).toFloat(), (bl.y * scaleBack).toFloat()
        )
    }
}
