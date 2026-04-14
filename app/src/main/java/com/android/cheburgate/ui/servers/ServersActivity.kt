package com.android.cheburgate.ui.servers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.cheburgate.R
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.Server
import com.android.cheburgate.databinding.ActivityServersBinding
import com.android.cheburgate.databinding.ItemServerBinding
import com.android.cheburgate.util.LinkParser
import com.android.cheburgate.util.copyToClipboard
import com.android.cheburgate.util.pasteFromClipboard
import com.android.cheburgate.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.exitProcess

class ServersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServersBinding
    private lateinit var adapter: ServerAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ServerAdapter(
            onMakeActive = { server -> makeActive(server) },
            onEdit = { server -> editServer(server) },
            onDelete = { server -> deleteServer(server) },
            onPingResult = { server, ms -> /* ping показывается в адаптере */ }
        )

        binding.rvServers.layoutManager = LinearLayoutManager(this)
        binding.rvServers.adapter = adapter

        lifecycleScope.launch {
            db.serverDao().getAllFlow().collectLatest { servers ->
                adapter.submitList(servers)
            }
        }

        binding.fab.setOnClickListener {
            startActivity(Intent(this, AddServerActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_servers, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_check_all -> { pingAll(); true }
            R.id.action_export -> { exportServers(); true }
            R.id.action_import -> { showImportDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun makeActive(server: Server) {
        lifecycleScope.launch {
            db.serverDao().clearActive()
            db.serverDao().setActive(server.id)
            restartApp()
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        exitProcess(0)
    }

    private fun editServer(server: Server) {
        startActivity(Intent(this, AddServerActivity::class.java).apply {
            putExtra(AddServerActivity.EXTRA_SERVER_ID, server.id)
        })
    }

    private fun deleteServer(server: Server) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete) + " \"${server.name}\"?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch { db.serverDao().delete(server) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pingAll() {
        adapter.currentList.forEach { server ->
            lifecycleScope.launch {
                val ms = pingServer(server)
                adapter.updatePing(server.id, ms)
            }
        }
    }

    private suspend fun pingServer(server: Server): Long? = withContext(Dispatchers.IO) {
        // Hysteria2 работает по QUIC (UDP) — TCP-сокет не подходит
        if (server.protocol == "hysteria2") return@withContext null
        try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(server.address, server.port), 3000)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            null
        }
    }

    private fun exportServers() {
        lifecycleScope.launch {
            val links = adapter.currentList.joinToString("\n") { server ->
                try { LinkParser.toLink(server) } catch (_: Exception) { "" }
            }.trim()
            if (links.isEmpty()) return@launch
            copyToClipboard(links)
            showToast(getString(R.string.copied))
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, links)
                },
                getString(R.string.export_all)
            ))
        }
    }

    private fun showImportDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "vless://... или hy2://..."
            minLines = 3
            maxLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.import_servers)
            .setView(editText)
            .setNeutralButton(R.string.from_clipboard) { _, _ ->
                editText.setText(pasteFromClipboard() ?: "")
            }
            .setPositiveButton(R.string.add) { _, _ ->
                val text = editText.text.toString()
                importServers(text)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importServers(text: String) {
        lifecycleScope.launch {
            val servers = LinkParser.parseMultiple(text)
            var count = 0
            servers.forEach { server ->
                db.serverDao().insert(server)
                count++
            }
            showToast(getString(R.string.servers_imported, count))
        }
    }
}

class ServerAdapter(
    private val onMakeActive: (Server) -> Unit,
    private val onEdit: (Server) -> Unit,
    private val onDelete: (Server) -> Unit,
    private val onPingResult: (Server, Long?) -> Unit
) : ListAdapter<Server, ServerAdapter.VH>(DIFF) {

    private val pingMap = mutableMapOf<Long, Long?>()

    fun updatePing(serverId: Long, ms: Long?) {
        pingMap[serverId] = ms
        val pos = currentList.indexOfFirst { it.id == serverId }
        if (pos >= 0) notifyItemChanged(pos)
    }

    inner class VH(private val binding: ItemServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(server: Server) {
            binding.tvName.text = server.name
            binding.tvDetails.text = buildString {
                append(server.protocol.uppercase())
                if (server.security?.isNotEmpty() == true) append("+${server.security}")
                append(" · ")
                append("${server.address}:${server.port}")
            }

            val activeColor = if (server.isActive)
                itemView.context.getColor(R.color.proxy_running)
            else
                itemView.context.getColor(R.color.proxy_stopped)
            binding.viewActive.backgroundTintList =
                android.content.res.ColorStateList.valueOf(activeColor)

            val ping = pingMap[server.id]
            binding.tvPing.text = when {
                ping == null -> "—"
                ping < 200  -> "${ping}ms"
                ping < 500  -> "${ping}ms"
                else        -> "${ping}ms"
            }
            val pingColor = when {
                ping == null -> itemView.context.getColor(R.color.ping_none)
                ping < 200  -> itemView.context.getColor(R.color.ping_good)
                ping < 500  -> itemView.context.getColor(R.color.ping_ok)
                else        -> itemView.context.getColor(R.color.ping_bad)
            }
            binding.tvPing.setTextColor(pingColor)

            binding.root.setOnClickListener { onMakeActive(server) }
            binding.btnMenu.setOnClickListener { showMenu(it, server) }
        }

        private fun showMenu(anchor: View, server: Server) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.add(0, 0, 0, R.string.make_active)
            popup.menu.add(0, 1, 1, R.string.edit_service)
            popup.menu.add(0, 2, 2, R.string.delete)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    0 -> onMakeActive(server)
                    1 -> onEdit(server)
                    2 -> onDelete(server)
                }
                true
            }
            popup.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Server>() {
            override fun areItemsTheSame(a: Server, b: Server) = a.id == b.id
            override fun areContentsTheSame(a: Server, b: Server) = a == b
        }
    }
}
