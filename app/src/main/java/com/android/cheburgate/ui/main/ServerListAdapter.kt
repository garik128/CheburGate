package com.android.cheburgate.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.cheburgate.R
import com.android.cheburgate.data.model.Server

class ServerListAdapter(
    private val servers: List<Server>,
    private val showDeactivate: Boolean,
    private val onSelect: (Server) -> Unit,
    private val onDeactivate: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DEACTIVATE = 0
        private const val TYPE_SERVER = 1
    }

    // Если showDeactivate, первый элемент — "Отключить прокси"
    private val offset get() = if (showDeactivate) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (showDeactivate && position == 0) TYPE_DEACTIVATE else TYPE_SERVER

    override fun getItemCount() = servers.size + offset

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DEACTIVATE) {
            val view = inflater.inflate(R.layout.item_server_compact, parent, false)
            DeactivateVH(view)
        } else {
            val view = inflater.inflate(R.layout.item_server_compact, parent, false)
            ServerVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DeactivateVH) {
            holder.tvName.text = holder.itemView.context.getString(R.string.stop_proxy)
            holder.tvDetails.text = ""
            holder.itemView.setOnClickListener { onDeactivate() }
        } else if (holder is ServerVH) {
            val server = servers[position - offset]
            holder.tvName.text = server.name
            holder.tvDetails.text = "${server.protocol.uppercase()} · ${server.address}:${server.port}"
            holder.itemView.setOnClickListener { onSelect(server) }
        }
    }

    inner class ServerVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvServerName)
        val tvDetails: TextView = view.findViewById(R.id.tvServerDetails)
    }

    inner class DeactivateVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvServerName)
        val tvDetails: TextView = view.findViewById(R.id.tvServerDetails)
    }
}
