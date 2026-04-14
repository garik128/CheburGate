package com.android.cheburgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.android.cheburgate.core.ProxyService
import com.android.cheburgate.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autostart = prefs.getBoolean("autostart_proxy", true)
        if (!autostart) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val server = db.serverDao().getActive()
            if (server != null) {
                val serviceIntent = Intent(context, ProxyService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
