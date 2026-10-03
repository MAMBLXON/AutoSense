package com.mamblxon.autosense.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.domain.model.ServiceRecordWithName
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ServiceRecord): Long

    @Query("SELECT * FROM service_records WHERE serviceItemId = :itemId ORDER BY dateMillis DESC")
    fun getForItem(itemId: Long): Flow<List<ServiceRecord>>

    @Query("""
        SELECT r.id AS id, r.serviceItemId AS serviceItemId, r.km AS km,
               r.dateMillis AS dateMillis, r.note AS note, r.cost AS cost,
               i.name AS serviceName, i.category AS serviceCategory
        FROM service_records r
        INNER JOIN service_items i ON i.id = r.serviceItemId
        WHERE i.car = :catalogId
        ORDER BY r.dateMillis DESC
    """)
    fun observeRecordsByCar(catalogId: String): Flow<List<ServiceRecordWithName>>

    @Query("""
        SELECT SUM(r.cost) FROM service_records r
        INNER JOIN service_items i ON i.id = r.serviceItemId
        WHERE i.car = :catalogId
    """)
    fun observeTotalCost(catalogId: String): Flow<Int?>

    @Query("SELECT * FROM service_records ORDER BY dateMillis DESC")
    suspend fun getAllRecords(): List<ServiceRecord>

    @Query("SELECT * FROM service_records WHERE serviceItemId = :itemId ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLastForItem(itemId: Long): ServiceRecord?

    @Query("SELECT COUNT(*) FROM service_records WHERE serviceItemId = :itemId")
    suspend fun countForItem(itemId: Long): Int
}
data class ServiceRecordWithName(
    val id: Long,
    val serviceItemId: Long,
    val km: Int,
    val dateMillis: Long,
    val note: String,
    val cost: Int,
    val serviceName: String,
    val serviceCategory: String
)