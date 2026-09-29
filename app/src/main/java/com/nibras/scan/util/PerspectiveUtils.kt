package com.nibras.scan.util

import android.graphics.Bitmap
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PerspectiveUtils {

    private const val MAX_OUTPUT_SIDE = 3000

    /**
     * Straightens the quadrilateral [corners] (8 floats: TL, TR, BR, BL as x,y pairs, in
     * [src] pixel coordinates) into a flat rectangular bitmap.
     *
     * Pure-Kotlin projective (homography) warp with bilinear sampling - no OpenCV, no GPU
     * canvas tricks. Runs on the CPU, so call it from a background thread.
     */
    fun warpToRectangle(src: Bitmap, corners: FloatArray): Bitmap {
        require(corners.size == 8) { "corners must contain 4 points (8 floats)" }

        var outW = max(dist(corners, 0, 1), dist(corners, 3, 2))
        var outH = max(dist(corners, 0, 3), dist(corners, 1, 2))
        val longest = max(outW, outH)
        if (longest > MAX_OUTPUT_SIDE) {
            val k = MAX_OUTPUT_SIDE / longest
            outW *= k; outH *= k
        }
        val w = outW.roundToInt().coerceAtLeast(1)
        val h = outH.roundToInt().coerceAtLeast(1)

        val dst = doubleArrayOf(
            0.0, 0.0,
            w.toDouble(), 0.0,
            w.toDouble(), h.toDouble(),
            0.0, h.toDouble()
        )
        val srcPts = DoubleArray(8) { corners[it].toDouble() }
        val m = solveHomography(dst, srcPts)
            ?: return src.copy(Bitmap.Config.ARGB_8888, false) // degenerate quad (e.g. collinear corners)

        val sw = src.width
        val sh = src.height
        val srcPixels = IntArray(sw * sh)
        src.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)
        val out = IntArray(w * h)
        val maxX = (sw - 1).toFloat()
        val maxY = (sh - 1).toFloat()

        for (y in 0 until h) {
            val py = y + 0.5
            for (x in 0 until w) {
                val px = x + 0.5
                val wgt = m[6] * px + m[7] * py + 1.0
                val u = ((m[0] * px + m[1] * py + m[2]) / wgt).toFloat() - 0.5f
                val v = ((m[3] * px + m[4] * py + m[5]) / wgt).toFloat() - 0.5f
                val fx = if (u < 0f) 0f else if (u > maxX) maxX else u
                val fy = if (v < 0f) 0f else if (v > maxY) maxY else v
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val x1 = min(x0 + 1, sw - 1)
                val y1 = min(y0 + 1, sh - 1)
                val ax = fx - x0
                val ay = fy - y0
                val p00 = srcPixels[y0 * sw + x0]
                val p10 = srcPixels[y0 * sw + x1]
                val p01 = srcPixels[y1 * sw + x0]
                val p11 = srcPixels[y1 * sw + x1]
                val w00 = (1 - ax) * (1 - ay)
                val w10 = ax * (1 - ay)
                val w01 = (1 - ax) * ay
                val w11 = ax * ay
                val r = (((p00 shr 16) and 0xFF) * w00 + ((p10 shr 16) and 0xFF) * w10 +
                        ((p01 shr 16) and 0xFF) * w01 + ((p11 shr 16) and 0xFF) * w11).toInt()
                val g = (((p00 shr 8) and 0xFF) * w00 + ((p10 shr 8) and 0xFF) * w10 +
                        ((p01 shr 8) and 0xFF) * w01 + ((p11 shr 8) and 0xFF) * w11).toInt()
                val b = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 +
                        (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11).toInt()
                out[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun dist(c: FloatArray, a: Int, b: Int): Double =
        hypot((c[a * 2] - c[b * 2]).toDouble(), (c[a * 2 + 1] - c[b * 2 + 1]).toDouble())

    /**
     * Solves for h0..h7 of the projective map  x' = (h0x+h1y+h2)/(h6x+h7y+1),
     * y' = (h3x+h4y+h5)/(h6x+h7y+1)  that sends the 4 [from] points to the 4 [to] points.
     * Returns null if the system is singular.
     */
    private fun solveHomography(from: DoubleArray, to: DoubleArray): DoubleArray? {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[i * 2]; val y = from[i * 2 + 1]
            val u = to[i * 2]; val v = to[i * 2 + 1]
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
        }
        val n = 8
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (kotlin.math.abs(a[r][c]) > kotlin.math.abs(a[p][c])) p = r
            if (kotlin.math.abs(a[p][c]) < 1e-12) return null
            val tmp = a[c]; a[c] = a[p]; a[p] = tmp
            for (r in c + 1 until n) {
                val f = a[r][c] / a[c][c]
                for (k in c..n) a[r][k] -= f * a[c][k]
            }
        }
        val h = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = a[r][n]
            for (k in r + 1 until n) s -= a[r][k] * h[k]
            h[r] = s / a[r][r]
        }
        return h
    }
}
