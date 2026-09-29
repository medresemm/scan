package com.nibras.scan.util

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    /**
     * Writes [pageCount] pages into [outputFile]. Bitmaps are produced one at a time by
     * [bitmapProvider] and recycled right after drawing, so memory stays flat even for
     * long documents. Each PDF page keeps its bitmap's own aspect ratio.
     */
    fun createPdf(pageCount: Int, outputFile: File, bitmapProvider: (Int) -> Bitmap) {
        val document = PdfDocument()
        try {
            for (index in 0 until pageCount) {
                val bitmap = bitmapProvider(index)
                val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                bitmap.recycle()
            }
            FileOutputStream(outputFile).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }
    }
}
