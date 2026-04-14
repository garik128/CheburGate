package com.android.cheburgate.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.cheburgate.data.model.ServiceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {

    @Query("SELECT * FROM services WHERE isVisible = 1 ORDER BY sortOrder ASC")
    fun getVisibleFlow(): Flow<List<ServiceItem>>

    @Query("SELECT * FROM services ORDER BY sortOrder ASC")
    fun getAllFlow(): Flow<List<ServiceItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ServiceItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ServiceItem)

    @Query("UPDATE services SET isVisible = :visible WHERE id = :id")
    suspend fun setVisible(id: String, visible: Boolean)

    @Delete
    suspend fun delete(item: ServiceItem)

    @Query("UPDATE services SET name = :name, url = :url WHERE id = :id")
    suspend fun update(id: String, name: String, url: String)

    @Query("UPDATE services SET sortOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: String, order: Int)

    @Query("DELETE FROM services")
    suspend fun deleteAll()
}
