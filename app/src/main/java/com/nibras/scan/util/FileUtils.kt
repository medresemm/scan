package com.nibras.scan.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FileUtils {

    /** Where finished PDF documents live, visible to the user via Files apps / sharing. */
    fun documentsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "documents")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Scratch space for pages that belong to the document currently being edited. */
    fun pagesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "pages")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newPageFile(context: Context): File {
        return File(pagesDir(context), "page_${UUID.randomUUID()}.jpg")
    }

    fun saveBitmap(bitmap: Bitmap, target: File, quality: Int = 92) {
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    /** Decodes an image scaled down (power-of-2) so its longest side is roughly <= [maxDim]. */
    fun decodeSampled(path: String, maxDim: Int): Bitmap {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxDim) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(path, opts)!!
    }

    fun defaultDocumentName(): String {
        val fmt = SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault())
        return "Senad_${fmt.format(Date())}"
    }

    fun listDocuments(context: Context): List<File> {
        val dir = documentsDir(context)
        return dir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Clears the scratch page files once a document has been finalized or discarded. */
    fun clearPagesDir(context: Context) {
        pagesDir(context).listFiles()?.forEach { it.delete() }
    }

    fun shareUriFor(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun buildShareIntent(context: Context, file: File): Intent {
        val uri = shareUriFor(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildViewIntent(context: Context, file: File): Intent {
        val uri = shareUriFor(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
