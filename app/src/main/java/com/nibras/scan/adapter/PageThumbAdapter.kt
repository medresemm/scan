package com.nibras.scan.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nibras.scan.databinding.ItemPageThumbBinding
import com.nibras.scan.model.Page
import com.nibras.scan.util.FilterUtils

class PageThumbAdapter(
    private val pages: MutableList<Page>,
    private val onThumbClick: (Int) -> Unit
) : RecyclerView.Adapter<PageThumbAdapter.ThumbViewHolder>() {

    var selectedPosition = 0
        set(value) {
            val old = field
            field = value
            if (old != value) {
                if (old in 0 until itemCount) notifyItemChanged(old)
                if (value in 0 until itemCount) notifyItemChanged(value)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbViewHolder {
        val binding = ItemPageThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ThumbViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbViewHolder, position: Int) {
        val page = pages[position]
        val bitmap = com.nibras.scan.util.FileUtils.decodeSampled(page.originalFile.absolutePath, 240)
        val rendered = FilterUtils.render(bitmap, page)
        holder.binding.imgThumbPage.setImageBitmap(rendered)
        holder.binding.viewThumbSelected.visibility =
            if (position == selectedPosition) android.view.View.VISIBLE else android.view.View.INVISIBLE
        holder.binding.root.setOnClickListener { onThumbClick(position) }
    }

    override fun getItemCount(): Int = pages.size

    fun moveItem(from: Int, to: Int) {
        val page = pages.removeAt(from)
        pages.add(to, page)
        notifyItemMoved(from, to)
    }

    class ThumbViewHolder(val binding: ItemPageThumbBinding) : RecyclerView.ViewHolder(binding.root)
}
