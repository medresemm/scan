package com.nibras.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.nibras.scan.databinding.ActivityCropBinding
import com.nibras.scan.model.Page
import com.nibras.scan.repo.ScanSession
import com.nibras.scan.util.DocumentDetector
import com.nibras.scan.util.FileUtils
import com.nibras.scan.util.InsetsUtil
import com.nibras.scan.util.PerspectiveUtils
import com.nibras.scan.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RAW_PATH = "extra_raw_path"
        const val EXTRA_EXISTING_PAGE_ID = "extra_existing_page_id"
        // Keeps the on-screen bitmap under the ~4096px GPU texture limit (result < 2 * this).
        private const val DISPLAY_MAX_DIM = 2048
    }

    private lateinit var binding: ActivityCropBinding
    private var sourceBitmap: Bitmap? = null
    private var existingPageId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root)

        val rawPath = intent.getStringExtra(EXTRA_RAW_PATH)
        existingPageId = intent.getStringExtra(EXTRA_EXISTING_PAGE_ID)

        val sourcePath = rawPath
            ?: existingPageId?.let { ScanSession.getPage(it)?.originalFile?.absolutePath }
        if (sourcePath == null || !File(sourcePath).exists()) {
            finish()
            return
        }

        val bitmap = if (rawPath != null) loadRotatedBitmap(rawPath)
        else FileUtils.decodeSampled(sourcePath, DISPLAY_MAX_DIM)
        sourceBitmap = bitmap
        binding.imgToCrop.setImageBitmap(bitmap)

        binding.imgToCrop.post {
            val rect = computeDisplayedImageRect(bitmap)
            // Show a sensible default quad immediately, then replace it with the
            // auto-detected page edges (if any) a moment later - detection runs on a
            // background thread so it never blocks the UI.
            binding.cropOverlay.setImageRect(rect)
            detectEdgesAsync(bitmap, rect)
        }

        binding.btnRetake.setOnClickListener {
            rawPath?.let { File(it).delete() }
            finish()
        }
        binding.btnConfirmCrop.setOnClickListener { confirmCrop(rawPath) }
    }

    /** Loads the captured JPEG (downscaled) and applies its EXIF rotation so pixels are upright. */
    private fun loadRotatedBitmap(path: String): Bitmap {
        val bitmap = FileUtils.decodeSampled(path, DISPLAY_MAX_DIM)
        val orientation = try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Runs automatic edge detection off the UI thread and, if it finds a plausible page
     * quad, moves the crop handles onto it (converting bitmap-pixel coords to this view's
     * on-screen coords using [rect], the image's displayed area). */
    private fun detectEdgesAsync(bitmap: Bitmap, rect: RectF) {
        lifecycleScope.launch {
            val quad = withContext(Dispatchers.Default) { DocumentDetector.detect(bitmap) }
            if (quad == null || isFinishing) return@launch
            val scale = rect.width() / bitmap.width
            val viewQuad = FloatArray(8)
            for (i in 0 until 4) {
                viewQuad[i * 2] = rect.left + quad[i * 2] * scale
                viewQuad[i * 2 + 1] = rect.top + quad[i * 2 + 1] * scale
            }
            binding.cropOverlay.setCorners(viewQuad)
        }
    }

    /** Where [bitmap] is actually drawn inside imgToCrop (scaleType=fitCenter). */
    private fun computeDisplayedImageRect(bitmap: Bitmap): RectF {
        val viewWidth = binding.imgToCrop.width.toFloat()
        val viewHeight = binding.imgToCrop.height.toFloat()
        val scale = min(viewWidth / bitmap.width, viewHeight / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (viewWidth - dw) / 2f
        val top = (viewHeight - dh) / 2f
        return RectF(left, top, left + dw, top + dh)
    }

    private fun confirmCrop(rawPath: String?) {
        val bitmap = sourceBitmap ?: return
        val viewCorners = binding.cropOverlay.getCorners()
        val rect = computeDisplayedImageRect(bitmap)
        val scale = rect.width() / bitmap.width

        // View-space corner points -> bitmap pixel space.
        val bitmapCorners = FloatArray(8)
        for (i in 0 until 4) {
            bitmapCorners[i * 2] = (viewCorners[i * 2] - rect.left) / scale
            bitmapCorners[i * 2 + 1] = (viewCorners[i * 2 + 1] - rect.top) / scale
        }

        binding.btnConfirmCrop.isEnabled = false
        binding.btnRetake.isEnabled = false

        lifecycleScope.launch {
            // The warp is CPU heavy: keep it off the UI thread. Catch Throwable to survive OOM.
            val outputFile: File? = withContext(Dispatchers.Default) {
                try {
                    val warped = PerspectiveUtils.warpToRectangle(bitmap, bitmapCorners)
                    val file = FileUtils.newPageFile(this@CropActivity)
                    FileUtils.saveBitmap(warped, file)
                    warped.recycle()
                    file
                } catch (t: Throwable) {
                    null
                }
            }

            if (outputFile == null) {
                Toast.makeText(this@CropActivity, R.string.crop_failed, Toast.LENGTH_LONG).show()
                binding.btnConfirmCrop.isEnabled = true
                binding.btnRetake.isEnabled = true
                return@launch
            }

            val existingId = existingPageId
            if (existingId != null) {
                // Re-cropping a page already in the session: swap its image file.
                ScanSession.getPage(existingId)?.let { page ->
                    val old = page.originalFile
                    page.originalFile = outputFile
                    old.delete()
                }
            } else {
                ScanSession.addPage(
                    Page(originalFile = outputFile, filter = SettingsManager.getDefaultFilter(this@CropActivity))
                )
            }
            rawPath?.let { File(it).delete() }
            finish()
        }
    }
}
