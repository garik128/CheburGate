package com.android.cheburgate.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.android.cheburgate.R
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.security.SecureRandom

class ProxyService : Service() {

    companion object {
        const val ACTION_PROXY_STATUS_CHANGED = "com.android.cheburgate.PROXY_STATUS_CHANGED"
        const val EXTRA_IS_RUNNING = "isRunning"
        const val EXTRA_IS_ERROR = "isError"
        const val EXTRA_PROXY_PORT = "proxyPort"

        @Volatile
        var currentPort: Int = 0
            private set

        @Volatile
        var currentToken: String = ""
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var proxyJob: Job? = null
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification("CheburGate активен"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Отменяем предыдущий запуск и watchdog — гарантируем чистый рестарт
        proxyJob?.cancel()
        watchdogJob?.cancel()
        proxyJob = scope.launch { startProxy() }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    private suspend fun startProxy() {
        val port = findFreePort()
        val token = generateToken()
        currentPort = port
        currentToken = token

        val server = AppDatabase.getInstance(this).serverDao().getActive()
        if (server == null) {
            broadcastStatus(running = false, error = false, port = 0)
            stopSelf()
            return
        }

        try {
            val config = ConfigBuilder.build(server, port, token)
            val started = SingBoxManager.start(this, config)
            if (started) {
                broadcastStatus(running = true, error = false, port = port)
                startWatchdog(config)
            } else {
                broadcastStatus(running = false, error = true, port = port)
                stopSelf()
            }
        } catch (e: Exception) {
            broadcastStatus(running = false, error = true, port = port)
            stopSelf()
        }
    }

    private fun startWatchdog(config: String) {
        var restarts = 0
        watchdogJob = scope.launch {
            while (true) {
                delay(5_000)
                if (!SingBoxManager.isRunning()) {
                    if (restarts >= 3) {
                        broadcastStatus(running = false, error = true, port = currentPort)
                        stopSelf()
                        break
                    }
                    restarts++
                    SingBoxManager.start(this@ProxyService, config)
                    broadcastStatus(running = SingBoxManager.isRunning(), port = currentPort)
                }
            }
        }
    }

    private fun broadcastStatus(running: Boolean, error: Boolean = false, port: Int) {
        val intent = Intent(ACTION_PROXY_STATUS_CHANGED).apply {
            setPackage(packageName) // гарантирует доставку RECEIVER_NOT_EXPORTED
            putExtra(EXTRA_IS_RUNNING, running)
            putExtra(EXTRA_IS_ERROR, error)
            putExtra(EXTRA_PROXY_PORT, port)
        }
        sendBroadcast(intent)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun findFreePort(): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (_: Exception) {
            (10000..60000).random()
        }
    }

    override fun onDestroy() {
        proxyJob?.cancel()
        watchdogJob?.cancel()
        SingBoxManager.stop()
        broadcastStatus(running = false, port = 0)
        currentPort = 0
        currentToken = ""
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "proxy_channel", "Прокси статус", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Показывает статус работы прокси" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "proxy_channel")
            .setContentTitle("CheburGate")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
