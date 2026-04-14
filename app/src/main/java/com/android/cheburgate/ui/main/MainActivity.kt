package com.android.cheburgate.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.cheburgate.R
import com.android.cheburgate.core.ProxyService
import com.android.cheburgate.core.SingBoxManager
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.ServiceItem
import com.android.cheburgate.databinding.ActivityMainBinding
import com.android.cheburgate.ui.browser.BrowserActivity
import com.android.cheburgate.ui.history.HistoryActivity
import com.android.cheburgate.ui.servers.AddServerActivity
import com.android.cheburgate.ui.servers.ServersActivity
import com.android.cheburgate.ui.settings.SettingsActivity
import com.android.cheburgate.util.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var serviceAdapter: ServiceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupServiceGrid()
        setupServerSection()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Запускаем прокси если не работает — ProxyService сам остановится если нет активного сервера
        if (!SingBoxManager.isRunning() && viewModel.proxyStatus.value != ProxyStatus.STARTING) {
            viewModel.startProxy()
        }
    }

    private fun setupServiceGrid() {
        serviceAdapter = ServiceAdapter(
            onItemClick = { item -> openBrowser(item.url, item.name) },
            onItemLongClick = { item -> showServiceContextMenu(item) },
            onAddClick = { showServiceDialog(null) }
        )
        binding.rvServices.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = serviceAdapter
        }
    }

    private fun setupServerSection() {
        binding.viewProxyStatus.setOnClickListener {
            when (viewModel.proxyStatus.value) {
                ProxyStatus.STOPPED, ProxyStatus.ERROR -> viewModel.startProxy()
                ProxyStatus.RUNNING -> viewModel.stopProxy()
                else -> {}
            }
        }
        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.btnServers.setOnClickListener { startActivity(Intent(this, ServersActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.services.collectLatest { items -> serviceAdapter.submitList(items) }
        }
        lifecycleScope.launch {
            viewModel.activeServer.collectLatest { server ->
                updateServerButton(server, viewModel.hasServers.value)
            }
        }
        lifecycleScope.launch {
            viewModel.hasServers.collectLatest { hasServers ->
                updateServerButton(viewModel.activeServer.value, hasServers)
            }
        }
        lifecycleScope.launch {
            viewModel.proxyStatus.collectLatest { status ->
                val color = when (status) {
                    ProxyStatus.STOPPED  -> getColor(R.color.proxy_stopped)
                    ProxyStatus.STARTING -> getColor(R.color.proxy_starting)
                    ProxyStatus.RUNNING  -> getColor(R.color.proxy_running)
                    ProxyStatus.ERROR    -> getColor(R.color.proxy_error)
                }
                binding.viewProxyStatus.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(color)
            }
        }
        lifecycleScope.launch {
            viewModel.restartApp.collectLatest { restartApp() }
        }
    }

    private fun updateServerButton(server: com.android.cheburgate.data.model.Server?, hasServers: Boolean) {
        if (server != null) {
            binding.tvServerLabel.text = "${server.name} (${server.protocol.uppercase()})"
            binding.btnSwitchServer.text = getString(R.string.switch_server)
            binding.btnSwitchServer.setOnClickListener { showServerSwitchSheet() }
        } else {
            binding.tvServerLabel.text = getString(R.string.no_active_server)
            if (hasServers) {
                binding.btnSwitchServer.text = getString(R.string.switch_server)
                binding.btnSwitchServer.setOnClickListener { showServerSwitchSheet() }
            } else {
                binding.btnSwitchServer.text = getString(R.string.add_server)
                binding.btnSwitchServer.setOnClickListener {
                    startActivity(Intent(this, AddServerActivity::class.java))
                }
            }
        }
    }

    private fun openBrowser(url: String, name: String) {
        if (ProxyService.currentPort == 0) {
            showToast(getString(R.string.proxy_not_running))
            return
        }
        startActivity(Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_URL, url)
            putExtra(BrowserActivity.EXTRA_SERVICE_NAME, name)
        })
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        exitProcess(0)
    }

    private fun showServiceContextMenu(item: ServiceItem) {
        val options = arrayOf(getString(R.string.edit_service), getString(R.string.hide_service))
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showServiceDialog(item)
                    1 -> viewModel.hideService(item.id)
                }
            }
            .show()
    }

    private fun showServiceDialog(existing: ServiceItem?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_service, null)
        val etName = view.findViewById<EditText>(R.id.etServiceName)
        val etUrl  = view.findViewById<EditText>(R.id.etServiceUrl)

        if (existing != null) {
            etName.setText(existing.name)
            etUrl.setText(existing.url)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_service else R.string.edit_service)
            .setView(view)
            .setPositiveButton(if (existing == null) R.string.add else R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                val url  = normalizeUrl(etUrl.text.toString().trim())
                if (name.isEmpty() || url == null) {
                    showToast(getString(R.string.invalid_url))
                    return@setPositiveButton
                }
                if (existing == null) viewModel.addCustomService(name, url)
                else viewModel.updateService(existing, name, url)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun normalizeUrl(raw: String): String? {
        if (raw.isEmpty()) return null
        val withScheme = when {
            raw.startsWith("https://") -> raw
            raw.startsWith("http://")  -> "https://" + raw.removePrefix("http://")
            else                       -> "https://$raw"
        }
        val host = android.net.Uri.parse(withScheme).host
        return if (host.isNullOrEmpty()) null else withScheme
    }

    private fun showServerSwitchSheet() {
        val sheet = BottomSheetDialog(this)
        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val servers = AppDatabase.getInstance(this@MainActivity).serverDao().getAll()
            val adapter = ServerListAdapter(
                servers = servers,
                showDeactivate = viewModel.proxyStatus.value != ProxyStatus.STOPPED,
                onSelect = { server ->
                    sheet.dismiss()
                    viewModel.switchServer(server)
                },
                onDeactivate = {
                    sheet.dismiss()
                    viewModel.deactivateServer()
                }
            )
            rv.adapter = adapter
            sheet.setContentView(rv)
            sheet.show()
        }
    }
}
