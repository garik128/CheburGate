package com.android.cheburgate.ui.main

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.cheburgate.core.ProxyService
import com.android.cheburgate.core.SingBoxManager
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.Server
import com.android.cheburgate.data.model.ServiceItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ProxyStatus { STOPPED, STARTING, RUNNING, ERROR }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val services: StateFlow<List<ServiceItem>> = db.serviceDao()
        .getVisibleFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeServer: StateFlow<Server?> = db.serverDao()
        .getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasServers: StateFlow<Boolean> = db.serverDao()
        .getAllFlow()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _proxyStatus = MutableStateFlow(
        if (SingBoxManager.isRunning()) ProxyStatus.RUNNING else ProxyStatus.STOPPED
    )
    val proxyStatus: StateFlow<ProxyStatus> = _proxyStatus

    // MainActivity подписывается и делает exitProcess(0)
    private val _restartApp = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restartApp = _restartApp.asSharedFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val running = intent.getBooleanExtra(ProxyService.EXTRA_IS_RUNNING, false)
            val error   = intent.getBooleanExtra(ProxyService.EXTRA_IS_ERROR, false)
            _proxyStatus.value = when {
                running -> ProxyStatus.RUNNING
                error   -> ProxyStatus.ERROR
                else    -> ProxyStatus.STOPPED
            }
        }
    }

    init {
        app.registerReceiver(
            statusReceiver,
            IntentFilter(ProxyService.ACTION_PROXY_STATUS_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    fun startProxy() {
        val ctx = getApplication<Application>()
        _proxyStatus.value = ProxyStatus.STARTING
        ctx.startForegroundService(Intent(ctx, ProxyService::class.java))
    }

    fun stopProxy() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, ProxyService::class.java))
        _proxyStatus.value = ProxyStatus.STOPPED
    }

    fun switchServer(server: Server) {
        viewModelScope.launch {
            // Явно останавливаем sing-box до перезапуска, иначе дочерний процесс
            // становится сиротой после exitProcess(0)
            SingBoxManager.stop()
            db.serverDao().clearActive()
            db.serverDao().setActive(server.id)
            _restartApp.emit(Unit)
        }
    }

    fun deactivateServer() {
        viewModelScope.launch {
            db.serverDao().clearActive()
            stopProxy()
        }
    }

    fun hideService(id: String) {
        viewModelScope.launch { db.serviceDao().setVisible(id, false) }
    }

    fun updateService(item: ServiceItem, newName: String, newUrl: String) {
        viewModelScope.launch { db.serviceDao().update(item.id, newName, newUrl) }
    }

    fun addCustomService(name: String, url: String) {
        viewModelScope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            val maxOrder = services.value.maxOfOrNull { it.sortOrder } ?: 0
            db.serviceDao().insert(
                ServiceItem(id = id, name = name, url = url, isBuiltIn = false, sortOrder = maxOrder + 1)
            )
        }
    }

    fun reorderServices(fromId: String, newOrder: Int) {
        viewModelScope.launch { db.serviceDao().updateOrder(fromId, newOrder) }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(statusReceiver)
        super.onCleared()
    }
}
