package com.android.cheburgate.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.cheburgate.BuildConfig
import com.android.cheburgate.R
import com.android.cheburgate.core.SingBoxManager
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.util.UpdateChecker
import com.android.cheburgate.util.copyToClipboard
import com.android.cheburgate.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Версия приложения
        findPreference<Preference>("app_version")?.summary = BuildConfig.VERSION_NAME

        // Проверка обновлений
        findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
            checkForUpdate(it)
            true
        }

        // Версия sing-box
        lifecycleScope.launch {
            val version = withContext(Dispatchers.IO) {
                SingBoxManager.getSingBoxVersion(requireContext())
            }
            findPreference<Preference>("singbox_version")?.summary = version
        }

        // Тема
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            when (newValue as String) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            true
        }

        // Очистить историю
        findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.clear_history)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        AppDatabase.getInstance(requireContext()).historyDao().clearAll()
                        context?.let {
                            android.widget.Toast.makeText(it, R.string.clear_history_done, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        // Очистить все данные
        findPreference<Preference>("clear_all_data")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Очистить все данные")
                .setMessage("Будут удалены все серверы, сервисы и история посещений. Продолжить?")
                .setPositiveButton("Удалить") { _, _ ->
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(requireContext())
                        db.serverDao().deleteAll()
                        db.serviceDao().deleteAll()
                        db.historyDao().clearAll()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        // Ссылка на репозиторий
        findPreference<Preference>("github_link")?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/garik128/CheburGate")))
            true
        }

        // Логи sing-box
        findPreference<Preference>("singbox_logs")?.setOnPreferenceClickListener {
            val logs = SingBoxManager.getLogSnapshot().joinToString("\n").ifEmpty { "Нет логов" }
            val tv = TextView(requireContext()).apply {
                text = logs
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(32, 24, 32, 24)
                setTextIsSelectable(true)
            }
            val scrollView = ScrollView(requireContext()).apply { addView(tv) }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.singbox_logs)
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton("Скопировать") { _, _ ->
                    requireContext().copyToClipboard(logs)
                    requireContext().showToast(getString(R.string.copied))
                }
                .show()
            true
        }
    }

    private fun checkForUpdate(pref: Preference) {
        pref.isEnabled = false
        pref.summary = "Проверка..."
        lifecycleScope.launch {
            val release = UpdateChecker.fetchLatestRelease()
            pref.isEnabled = true
            pref.summary = null
            if (release == null) {
                requireContext().showToast("Не удалось проверить обновления")
                return@launch
            }
            if (UpdateChecker.isNewer(release.tagName, BuildConfig.VERSION_NAME)) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Доступно обновление")
                    .setMessage("Текущая версия: ${BuildConfig.VERSION_NAME}\nНовая версия: ${release.tagName}")
                    .setPositiveButton("Открыть") { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                requireContext().showToast("Установлена актуальная версия")
            }
        }
    }
}
