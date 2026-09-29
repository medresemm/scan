package com.nibras.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.nibras.scan.databinding.ActivityScanCameraBinding
import com.nibras.scan.repo.ScanSession
import com.nibras.scan.util.FileUtils
import com.nibras.scan.util.InsetsUtil
import java.io.File

class ScanCameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanCameraBinding
    private var imageCapture: ImageCapture? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root)

        binding.btnClose.setOnClickListener { confirmExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmExit() }
        })
        binding.btnCapture.setOnClickListener { capturePhoto() }
        binding.btnDone.setOnClickListener {
            startActivity(Intent(this, EditPagesActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePageCount()
    }

    /** Leaving the camera with unsaved pages would silently keep them for the next scan, so ask. */
    private fun confirmExit() {
        if (ScanSession.isEmpty()) { finish(); return }
        AlertDialog.Builder(this)
            .setMessage(R.string.discard_scan_confirm)
            .setPositiveButton(R.string.discard) { _, _ ->
                ScanSession.clear()
                FileUtils.clearPagesDir(this)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updatePageCount() {
        val count = ScanSession.size()
        binding.tvPageCount.text = count.toString()
        binding.btnDone.visibility = if (count > 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnDone.text = getString(R.string.finish_scan_format, count)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            imageCapture = capture

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera açıla bilmədi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val rawFile = File(cacheDir, "raw_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val intent = Intent(this@ScanCameraActivity, CropActivity::class.java)
                    intent.putExtra(CropActivity.EXTRA_RAW_PATH, rawFile.absolutePath)
                    startActivity(intent)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@ScanCameraActivity,
                        "Şəkil çəkilmədi: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}
