package com.nibras.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nibras.scan.databinding.ActivityOcrBinding
import com.nibras.scan.util.InsetsUtil
import com.nibras.scan.util.SettingsManager
import com.nibras.scan.util.TesseractHelper
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OcrActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
    }

    private lateinit var binding: ActivityOcrBinding
    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOcrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root, binding.headerOcr)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath == null) {
            finish()
            return
        }

        binding.chipLangLatin.setOnClickListener {
            binding.chipLangLatin.isSelected = true
            binding.chipLangArabic.isSelected = false
            runLatinOcr()
        }
        binding.chipLangArabic.setOnClickListener {
            binding.chipLangLatin.isSelected = false
            binding.chipLangArabic.isSelected = true
            runArabicOcr()
        }
        binding.btnCopyText.setOnClickListener { copyResultToClipboard() }

        if (SettingsManager.getOcrLanguage(this) == SettingsManager.OcrLanguage.ARABIC) {
            binding.chipLangArabic.isSelected = true
            runArabicOcr()
        } else {
            binding.chipLangLatin.isSelected = true
            runLatinOcr()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressOcr.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvOcrStatus.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** ML Kit's on-device recognizer - fast, accurate for Latin-script languages
     * (Azerbaijani, English, etc.), works fully offline once the model is downloaded. */
    private fun runLatinOcr() {
        val path = imagePath ?: return
        showLoading(true)
        binding.tvOcrResult.text = ""

        val image = InputImage.fromFilePath(this, android.net.Uri.fromFile(java.io.File(path)))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                showLoading(false)
                binding.tvOcrResult.text = result.text.ifBlank { "(mətn tapılmadı)" }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                binding.tvOcrResult.text = "Xəta: ${e.message}"
            }
    }

    /** Tesseract - used for Arabic, which ML Kit's on-device recognizer does not support.
     * Requires ara.traineddata to be bundled under assets/tessdata/ (see TesseractHelper). */
    private fun runArabicOcr() {
        val path = imagePath ?: return
        showLoading(true)
        binding.tvOcrResult.text = ""

        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                TesseractHelper.ensureLanguageAvailable(this@OcrActivity, "ara")
            }
            if (!available) {
                showLoading(false)
                binding.tvOcrResult.text = "Ərəb dili üçün tessdata/ara.traineddata faylı " +
                    "tapılmadı. Onu app/src/main/assets/tessdata/ qovluğuna əlavə edin."
                return@launch
            }

            val text = withContext(Dispatchers.Default) {
                recognizeWithTesseract(path)
            }
            showLoading(false)
            binding.tvOcrResult.text = text.ifBlank { "(mətn tapılmadı)" }
        }
    }

    private fun recognizeWithTesseract(path: String): String {
        val dataParentDir = TesseractHelper.tessDataParentDir(this).absolutePath
        val tess = TessBaseAPI()
        return try {
            // Arabic + English together handles pages that mix both (numbers, Latin words).
            val langs = if (TesseractHelper.ensureLanguageAvailable(this, "eng")) "ara+eng" else "ara"
            if (!tess.init(dataParentDir, langs)) return "Tesseract başladıla bilmədi"
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            val bitmap = BitmapFactory.decodeFile(path)
            tess.setImage(bitmap)
            tess.getUTF8Text() ?: ""
        } catch (e: Exception) {
            "Xəta: ${e.message}"
        } finally {
            tess.recycle()
        }
    }

    private fun copyResultToClipboard() {
        val text = binding.tvOcrResult.text.toString()
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nibras Scan OCR", text))
        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show()
    }
}
