package com.nibras.scan.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nibras.scan.databinding.ItemPageFullBinding
import com.nibras.scan.model.Page
import com.nibras.scan.util.FilterUtils

class PagePagerAdapter(private val pages: MutableList<Page>) :
    RecyclerView.Adapter<PagePagerAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPageFullBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        val bitmap = com.nibras.scan.util.FileUtils.decodeSampled(page.originalFile.absolutePath, 1600)
        val rendered = FilterUtils.render(bitmap, page)
        holder.binding.imgPageFull.setImageBitmap(rendered)
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(val binding: ItemPageFullBinding) : RecyclerView.ViewHolder(binding.root)
}
