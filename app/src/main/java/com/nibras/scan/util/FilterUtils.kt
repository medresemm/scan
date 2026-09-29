package com.nibras.scan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.nibras.scan.model.FilterType
import com.nibras.scan.model.Page
import kotlin.math.max
import kotlin.math.min

object FilterUtils {

    /** Downscales so the longest side is at most [maxDim] px (keeps aspect ratio). */
    fun limitSize(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true
        )
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun grayscale(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * "Auto" filter: stretches the brightness histogram (auto-levels) so the paper becomes
     * white and text gets darker. Levels are estimated from a ~400px sample of the image.
     */
    fun autoEnhance(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(1, max(w, h) / 400)
        val hist = IntArray(256)
        var total = 0
        val row = IntArray(w)
        var y = 0
        while (y < h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val p = row[x]
                val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                hist[lum]++
                total++
                x += step
            }
            y += step
        }
        var lo = 0
        var acc = 0
        while (lo < 255 && acc + hist[lo] < total * 0.01) { acc += hist[lo]; lo++ }
        var hi = 255
        acc = 0
        for (i in 0..255) { acc += hist[i]; if (acc >= total * 0.95) { hi = i; break } }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (hi - lo >= 20) {
            val s = (255f / (hi - lo)).coerceIn(1f, 2.5f)
            val o = -lo * s
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        s, 0f, 0f, 0f, o,
                        0f, s, 0f, 0f, o,
                        0f, 0f, s, 0f, o,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Black & white using an *adaptive* threshold: each pixel is compared with the local
     * average brightness (block means, blurred), so uneven lighting/shadows on a photographed
     * page do not turn into black blotches. Pixels darker than 90% of the local paper level
     * become black, the rest white.
     */
    fun blackAndWhite(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val block = 16
        val gw = (w + block - 1) / block
        val gh = (h + block - 1) / block
        val sums = FloatArray(gw * gh)
        val cnt = IntArray(gw * gh)
        val lum = ByteArray(w * h)
        for (y in 0 until h) {
            val gyRow = (y / block) * gw
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val l = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                lum[y * w + x] = l.toByte()
                sums[gyRow + x / block] += l
                cnt[gyRow + x / block]++
            }
        }
        var grid = FloatArray(gw * gh) { sums[it] / cnt[it] }
        repeat(2) { grid = blur3(grid, gw, gh) }

        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        for (y in 0 until h) {
            val gy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = gy.toInt()
            val y1 = min(y0 + 1, gh - 1)
            val fy = gy - y0
            for (x in 0 until w) {
                val gx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = gx.toInt()
                val x1 = min(x0 + 1, gw - 1)
                val fx = gx - x0
                val bg = grid[y0 * gw + x0] * (1 - fx) * (1 - fy) + grid[y0 * gw + x1] * fx * (1 - fy) +
                        grid[y1 * gw + x0] * (1 - fx) * fy + grid[y1 * gw + x1] * fx * fy
                val l = lum[y * w + x].toInt() and 0xFF
                pixels[y * w + x] = if (l >= bg * 0.90f) white else black
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blur3(g: FloatArray, gw: Int, gh: Int): FloatArray {
        val out = FloatArray(g.size)
        for (y in 0 until gh) for (x in 0 until gw) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val yy = (y + dy).coerceIn(0, gh - 1)
                val xx = (x + dx).coerceIn(0, gw - 1)
                sum += g[yy * gw + xx]
            }
            out[y * gw + x] = sum / 9f
        }
        return out
    }

    fun applyFilter(bitmap: Bitmap, filter: FilterType): Bitmap = when (filter) {
        FilterType.ORIGINAL -> bitmap
        FilterType.AUTO -> autoEnhance(bitmap)
        FilterType.GRAYSCALE -> grayscale(bitmap)
        FilterType.BLACK_WHITE -> blackAndWhite(bitmap)
    }

    /** Applies a page's stored rotation and filter together - used for preview and PDF export. */
    fun render(source: Bitmap, page: Page): Bitmap {
        val rotated = rotate(source, page.rotationDegrees)
        return applyFilter(rotated, page.filter)
    }
}
