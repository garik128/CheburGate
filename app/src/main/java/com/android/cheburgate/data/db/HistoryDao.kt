package com.android.cheburgate.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.android.cheburgate.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun getAllFlow(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE host = :host ORDER BY visitedAt DESC")
    fun getByHostFlow(host: String): Flow<List<HistoryItem>>

    @Insert
    suspend fun insert(item: HistoryItem)

    @Query("SELECT COUNT(*) FROM history WHERE url = :url AND visitedAt > :since")
    suspend fun recentVisitCount(url: String, since: Long): Int

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
