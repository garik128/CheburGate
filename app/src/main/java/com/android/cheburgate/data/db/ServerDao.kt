package com.android.cheburgate.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.android.cheburgate.data.model.Server
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Server>>

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Server?

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    fun getActiveFlow(): Flow<Server?>

    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    suspend fun getAll(): List<Server>

    @Insert
    suspend fun insert(server: Server): Long

    @Update
    suspend fun update(server: Server)

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): Server?

    @Delete
    suspend fun delete(server: Server)

    @Query("UPDATE servers SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE servers SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM servers")
    suspend fun deleteAll()
}
