package com.android.cheburgate.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.HistoryItem
import kotlinx.coroutines.launch

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val historyDao = AppDatabase.getInstance(app).historyDao()

    fun recordVisit(url: String, title: String?, host: String) {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - 60_000
            val recentCount = historyDao.recentVisitCount(url, since)
            if (recentCount == 0) {
                historyDao.insert(HistoryItem(url = url, title = title, host = host))
            }
        }
    }
}
