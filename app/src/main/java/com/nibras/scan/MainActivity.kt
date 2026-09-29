package com.nibras.scan

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nibras.scan.adapter.DocumentAdapter
import com.nibras.scan.databinding.ActivityMainBinding
import com.nibras.scan.databinding.DialogSaveDocumentBinding
import com.nibras.scan.model.Document
import com.nibras.scan.util.FileUtils
import com.nibras.scan.util.InsetsUtil
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DocumentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root, binding.headerMain)

        adapter = DocumentAdapter(
            onClick = { doc -> openDocument(doc) },
            onMoreClick = { doc, anchor -> showDocumentMenu(doc, anchor) }
        )
        binding.rvDocuments.layoutManager = LinearLayoutManager(this)
        binding.rvDocuments.adapter = adapter

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.cardScan.setOnClickListener { startScan() }
        binding.btnScan.setOnClickListener { startScan() }
    }

    override fun onResume() {
        super.onResume()
        refreshDocuments()
    }

    private fun startScan() {
        startActivity(Intent(this, ScanCameraActivity::class.java))
    }

    private fun refreshDocuments() {
        val files = FileUtils.listDocuments(this)
        val documents = files.map { file -> Document(
            name = file.nameWithoutExtension,
            file = file,
            pageCount = pageCountOf(file),
            createdAt = file.lastModified()
        ) }
        adapter.submitList(documents)
        binding.tvEmpty.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDocuments.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun pageCountOf(file: File): Int {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun openDocument(doc: Document) {
        try {
            startActivity(FileUtils.buildViewIntent(this, doc.file))
        } catch (e: Exception) {
            Toast.makeText(this, "PDF açan tətbiq tapılmadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDocumentMenu(doc: Document, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(getString(R.string.share))
        popup.menu.add(getString(R.string.rename))
        popup.menu.add(getString(R.string.delete))
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                getString(R.string.share) -> shareDocument(doc)
                getString(R.string.rename) -> renameDocument(doc)
                getString(R.string.delete) -> confirmDeleteDocument(doc)
            }
            true
        }
        popup.show()
    }

    private fun shareDocument(doc: Document) {
        startActivity(Intent.createChooser(FileUtils.buildShareIntent(this, doc.file), getString(R.string.share)))
    }

    private fun renameDocument(doc: Document) {
        val dialogBinding = DialogSaveDocumentBinding.inflate(layoutInflater)
        dialogBinding.etDocName.setText(doc.name)
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = dialogBinding.etDocName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(doc.file.parentFile, "$newName.pdf")
                    doc.file.renameTo(newFile)
                    refreshDocuments()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteDocument(doc: Document) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_document_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                doc.file.delete()
                refreshDocuments()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
