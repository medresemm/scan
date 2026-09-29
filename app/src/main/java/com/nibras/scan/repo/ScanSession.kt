package com.nibras.scan.repo

import com.nibras.scan.model.Page

/**
 * Holds the pages of the document the user is currently scanning/editing, in memory,
 * for as long as the app process is alive. Cleared once the document is saved as a PDF
 * (or explicitly discarded). Each [Page] only stores a reference to its image file on
 * disk (see [com.nibras.scan.util.FileUtils]), so this object itself stays lightweight.
 */
object ScanSession {

    private val pages = mutableListOf<Page>()

    fun getPages(): List<Page> = pages

    /** Direct reference to the backing list, for screens (ViewPager2/RecyclerView adapters)
     * that need to observe/reorder pages in place while editing. */
    fun pagesMutable(): MutableList<Page> = pages

    fun addPage(page: Page) {
        pages.add(page)
    }

    fun removePage(pageId: String) {
        pages.removeAll { it.id == pageId }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val page = pages.removeAt(fromIndex)
        pages.add(toIndex, page)
    }

    fun getPage(pageId: String): Page? = pages.find { it.id == pageId }

    fun isEmpty(): Boolean = pages.isEmpty()

    fun size(): Int = pages.size

    /** Called after a PDF has been successfully written, or when the user discards the session. */
    fun clear() {
        pages.clear()
    }
}
