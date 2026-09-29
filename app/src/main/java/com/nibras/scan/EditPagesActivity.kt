package com.nibras.scan

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nibras.scan.adapter.PagePagerAdapter
import com.nibras.scan.adapter.PageThumbAdapter
import com.nibras.scan.databinding.ActivityEditPagesBinding
import com.nibras.scan.databinding.DialogSaveDocumentBinding
import com.nibras.scan.model.FilterType
import com.nibras.scan.repo.ScanSession
import com.nibras.scan.util.FileUtils
import com.nibras.scan.util.FilterUtils
import com.nibras.scan.util.InsetsUtil
import com.nibras.scan.util.PdfUtils
import com.nibras.scan.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditPagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditPagesBinding
    private lateinit var pagerAdapter: PagePagerAdapter
    private lateinit var thumbAdapter: PageThumbAdapter
    private val pages get() = ScanSession.pagesMutable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPagesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.apply(binding.root)

        if (pages.isEmpty()) {
            finish()
            return
        }

        pagerAdapter = PagePagerAdapter(pages)
        binding.pagerPages.adapter = pagerAdapter

        thumbAdapter = PageThumbAdapter(pages) { position ->
            binding.pagerPages.currentItem = position
        }
        binding.rvThumbnails.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.rvThumbnails.adapter = thumbAdapter
        attachDragToReorder()

        binding.pagerPages.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                thumbAdapter.selectedPosition = position
                binding.rvThumbnails.scrollToPosition(position)
            }
        })

        binding.btnPrevPage.setOnClickListener {
            val current = binding.pagerPages.currentItem
            if (current > 0) binding.pagerPages.currentItem = current - 1
        }
        binding.btnNextPage.setOnClickListener {
            val current = binding.pagerPages.currentItem
            if (current < pages.size - 1) binding.pagerPages.currentItem = current + 1
        }

        binding.chipOriginal.setOnClickListener { applyFilterToCurrent(FilterType.ORIGINAL) }
        binding.chipAuto.setOnClickListener { applyFilterToCurrent(FilterType.AUTO) }
        binding.chipBw.setOnClickListener { applyFilterToCurrent(FilterType.BLACK_WHITE) }
        binding.chipGrayscale.setOnClickListener { applyFilterToCurrent(FilterType.GRAYSCALE) }

        binding.btnRotatePage.setOnClickListener { rotateCurrent() }
        binding.btnCropPage.setOnClickListener { cropCurrentAgain() }
        binding.btnDeletePage.setOnClickListener { deleteCurrent() }
        binding.btnExtractText.setOnClickListener { extractTextFromCurrent() }

        binding.btnAddPage.setOnClickListener { finish() } // camera screen is right below
        binding.btnCreatePdf.setOnClickListener { showSaveDialog() }

        updateIndicator(0)
        updateFilterChips(pages[0].filter)
    }

    override fun onResume() {
        super.onResume()
        if (pages.isEmpty()) {
            finish()
            return
        }
        pagerAdapter.notifyDataSetChanged()
        thumbAdapter.notifyDataSetChanged()
    }

    private fun currentPosition() = binding.pagerPages.currentItem

    private fun updateIndicator(position: Int) {
        binding.tvPageIndicator.text = "${position + 1} / ${pages.size}"
        updateFilterChips(pages.getOrNull(position)?.filter ?: FilterType.ORIGINAL)
    }

    private fun updateFilterChips(active: FilterType) {
        binding.chipOriginal.isSelected = active == FilterType.ORIGINAL
        binding.chipAuto.isSelected = active == FilterType.AUTO
        binding.chipBw.isSelected = active == FilterType.BLACK_WHITE
        binding.chipGrayscale.isSelected = active == FilterType.GRAYSCALE
    }

    private fun applyFilterToCurrent(filter: FilterType) {
        val pos = currentPosition()
        if (pos !in pages.indices) return
        pages[pos].filter = filter
        pagerAdapter.notifyItemChanged(pos)
        thumbAdapter.notifyItemChanged(pos)
        updateFilterChips(filter)
    }

    private fun rotateCurrent() {
        val pos = currentPosition()
        if (pos !in pages.indices) return
        val page = pages[pos]
        page.rotationDegrees = (page.rotationDegrees + 90) % 360
        pagerAdapter.notifyItemChanged(pos)
        thumbAdapter.notifyItemChanged(pos)
    }

    private fun cropCurrentAgain() {
        val pos = currentPosition()
        if (pos !in pages.indices) return
        val page = pages[pos]
        val intent = Intent(this, CropActivity::class.java)
        intent.putExtra(CropActivity.EXTRA_EXISTING_PAGE_ID, page.id)
        startActivity(intent)
    }

    private fun deleteCurrent() {
        val pos = currentPosition()
        if (pos !in pages.indices) return
        val page = pages[pos]
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_page_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                page.originalFile.delete()
                ScanSession.removePage(page.id)
                if (pages.isEmpty()) {
                    finish()
                    return@setPositiveButton
                }
                pagerAdapter.notifyDataSetChanged()
                thumbAdapter.notifyDataSetChanged()
                val newPos = pos.coerceAtMost(pages.size - 1)
                binding.pagerPages.currentItem = newPos
                updateIndicator(newPos)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun extractTextFromCurrent() {
        val pos = currentPosition()
        if (pos !in pages.indices) return
        val page = pages[pos]
        val bitmap = BitmapFactory.decodeFile(page.originalFile.absolutePath)
        val rendered = FilterUtils.render(bitmap, page)
        val tempFile = File(cacheDir, "ocr_temp_${System.currentTimeMillis()}.jpg")
        FileUtils.saveBitmap(rendered, tempFile)

        val intent = Intent(this, OcrActivity::class.java)
        intent.putExtra(OcrActivity.EXTRA_IMAGE_PATH, tempFile.absolutePath)
        startActivity(intent)
    }

    private fun attachDragToReorder() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == -1 || to == -1) return false
                thumbAdapter.moveItem(from, to)
                pagerAdapter.notifyDataSetChanged()
                if (currentPosition() == from) {
                    binding.pagerPages.setCurrentItem(to, false)
                }
                thumbAdapter.selectedPosition = binding.pagerPages.currentItem
                updateIndicator(binding.pagerPages.currentItem)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Reordering only - swiping off does not delete a page.
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvThumbnails)
    }

    private fun showSaveDialog() {
        val dialogBinding = DialogSaveDocumentBinding.inflate(layoutInflater)
        dialogBinding.etDocName.setText(FileUtils.defaultDocumentName())
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etDocName.text.toString().trim()
                    .ifEmpty { FileUtils.defaultDocumentName() }
                createPdf(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createPdf(name: String) {
        val outputFile = File(FileUtils.documentsDir(this), "$name.pdf")
        val pagesSnapshot = pages.toList()
        val maxDim = SettingsManager.getPdfQuality(this).maxDimensionPx

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    PdfUtils.createPdf(pagesSnapshot.size, outputFile) { i ->
                        val page = pagesSnapshot[i]
                        val src = FileUtils.decodeSampled(page.originalFile.absolutePath, maxDim)
                        FilterUtils.render(FilterUtils.limitSize(src, maxDim), page)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                ScanSession.clear()
                FileUtils.clearPagesDir(this@EditPagesActivity)
                Toast.makeText(this@EditPagesActivity, R.string.document_saved, Toast.LENGTH_SHORT).show()
                val intent = Intent(this@EditPagesActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@EditPagesActivity, "PDF yaradıla bilmədi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
