package com.android.cheburgate

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.ServiceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CheburGateApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        applyTheme()
        initCoil()

        appScope.launch {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("first_run", true)) {
                val db = AppDatabase.getInstance(this@CheburGateApp)
                db.serviceDao().insertAll(ServiceItem.BUILT_IN)
                prefs.edit().putBoolean("first_run", false).apply()
            }
        }
    }

    private fun applyTheme() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        when (pref.getString("theme", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun initCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("icon_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.1)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
