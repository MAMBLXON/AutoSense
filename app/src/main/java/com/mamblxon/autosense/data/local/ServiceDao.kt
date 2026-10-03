package com.mamblxon.autosense.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamblxon.autosense.domain.model.ServiceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ServiceItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ServiceItem>)

    @Update
    suspend fun update(item: ServiceItem)

    @Update
    suspend fun updateAll(items: List<ServiceItem>)

    @Delete
    suspend fun delete(item: ServiceItem)

    @Query("SELECT * FROM service_items WHERE profileId = :profileId ORDER BY urgency DESC, name ASC")
    fun getAllByProfile(profileId: Long): Flow<List<ServiceItem>>

    @Query("SELECT * FROM service_items WHERE profileId = :profileId ORDER BY urgency DESC, name ASC")
    suspend fun getAllOnceByProfile(profileId: Long): List<ServiceItem>

    /**
     * Поиск по названию ТО (title/name) в рамках профиля.
     * Пустая строка запроса -> все записи (LIKE '%%' совпадает с любым названием).
     */
    @Query(
        "SELECT * FROM service_items WHERE profileId = :profileId AND name LIKE '%' || :query || '%' " +
            "ORDER BY lastServiceDateMillis DESC"
    )
    suspend fun searchByTitle(profileId: Long, query: String): List<ServiceItem>

    @Query("SELECT * FROM service_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServiceItem?

    @Query("SELECT COUNT(*) FROM service_items WHERE profileId = :profileId")
    suspend fun countByProfile(profileId: Long): Int

    @Query("DELETE FROM service_items WHERE profileId = :profileId")
    suspend fun clearByProfile(profileId: Long)
}