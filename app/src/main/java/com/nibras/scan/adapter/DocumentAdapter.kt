package com.nibras.scan.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nibras.scan.databinding.ItemDocumentBinding
import com.nibras.scan.model.Document
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentAdapter(
    private val onClick: (Document) -> Unit,
    private val onMoreClick: (Document, android.view.View) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    private val items = mutableListOf<Document>()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun submitList(newItems: List<Document>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DocumentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val doc = items[position]
        holder.binding.tvDocName.text = doc.name
        holder.binding.tvDocMeta.text = "${doc.pageCount} səhifə · ${dateFormat.format(doc.createdAt)}"
        holder.binding.root.setOnClickListener { onClick(doc) }
        holder.binding.btnMore.setOnClickListener { onMoreClick(doc, it) }
    }

    override fun getItemCount(): Int = items.size

    class DocumentViewHolder(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)
}
