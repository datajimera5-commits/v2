package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: GenerationType): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE orderUid = :orderUid LIMIT 1")
    suspend fun getByOrderUid(orderUid: String): GenerationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GenerationEntity): Long

    @Update
    suspend fun update(entity: GenerationEntity)

    @Query("UPDATE generations SET status = :status, adminMessage = :adminMessage, remoteMediaUrl = :remoteUrl WHERE orderUid = :orderUid")
    suspend fun updateOrderStatus(
        orderUid: String,
        status: FireOrderStatus,
        adminMessage: String,
        remoteUrl: String
    )

    @Query("UPDATE generations SET isUpscaled = 1 WHERE id = :id")
    suspend fun markUpscaled(id: Long)

    @Query("UPDATE generations SET status = :status, localFilePath = :localPath WHERE id = :id")
    suspend fun markDownloaded(id: Long, status: FireOrderStatus, localPath: String)

    @Query("DELETE FROM generations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
