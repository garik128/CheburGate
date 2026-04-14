package com.android.cheburgate.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.cheburgate.R
import com.android.cheburgate.data.model.ServiceItem
import com.android.cheburgate.databinding.ItemServiceBinding
import com.android.cheburgate.util.IconLoader

private const val TYPE_SERVICE = 0
private const val TYPE_ADD = 1

class ServiceAdapter(
    private val onItemClick: (ServiceItem) -> Unit,
    private val onItemLongClick: (ServiceItem) -> Unit,
    private val onAddClick: () -> Unit,
    private val onItemEdit: ((ServiceItem) -> Unit)? = null,
    private val showAddButton: Boolean = true
) : ListAdapter<ServiceItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemCount() = super.getItemCount() + if (showAddButton) 1 else 0

    override fun getItemViewType(position: Int) =
        if (position < super.getItemCount()) TYPE_SERVICE else TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SERVICE) {
            val binding = ItemServiceBinding.inflate(inflater, parent, false)
            ServiceViewHolder(binding)
        } else {
            val view = inflater.inflate(R.layout.item_service_add, parent, false)
            AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ServiceViewHolder) {
            val item = getItem(position)
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        } else if (holder is AddViewHolder) {
            holder.itemView.setOnClickListener { onAddClick() }
        }
    }

    class ServiceViewHolder(private val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ServiceItem) {
            binding.tvName.text = item.name
            IconLoader.load(binding.ivIcon, item.url)
        }
    }

    class AddViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view)

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ServiceItem>() {
            override fun areItemsTheSame(old: ServiceItem, new: ServiceItem) = old.id == new.id
            override fun areContentsTheSame(old: ServiceItem, new: ServiceItem) = old == new
        }
    }
}
