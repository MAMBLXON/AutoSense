package com.mamblxon.autosense.data

import com.mamblxon.autosense.data.remote.CarCatalog
import com.mamblxon.autosense.data.remote.CarRegulation
import com.mamblxon.autosense.domain.model.CarProfile
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.domain.model.ServiceRecordWithName
import kotlinx.coroutines.flow.Flow

interface ServiceRepository {

    // ---------- ServiceItem ----------
    fun observeItems(profileId: Long): Flow<List<ServiceItem>>
    suspend fun getItemsOnce(profileId: Long): List<ServiceItem>
    suspend fun getItemById(id: Long): ServiceItem?
    suspend fun countItems(profileId: Long): Int

    suspend fun insertItems(items: List<ServiceItem>)
    suspend fun updateItem(item: ServiceItem)
    suspend fun updateItems(items: List<ServiceItem>)
    suspend fun deleteItem(item: ServiceItem)
    suspend fun clearItems(profileId: Long)

    // ---------- История ----------
    fun observeHistory(itemId: Long): Flow<List<ServiceRecord>>
    fun observeRecordsByCar(catalogId: String): Flow<List<ServiceRecordWithName>>
    fun observeTotalCost(catalogId: String): Flow<Int?>
    suspend fun getAllRecords(): List<ServiceRecord>
    suspend fun insertRecord(record: ServiceRecord)

    // ---------- Регламенты (legacy) ----------
    suspend fun loadRegulations(): List<CarRegulation>

    // ---------- Каталог ----------
    suspend fun loadCatalog(): CarCatalog

    // ---------- Профили ----------
    fun observeProfiles(): Flow<List<CarProfile>>
    suspend fun getProfileById(id: Long): CarProfile?
    suspend fun getProfile(catalogId: String): CarProfile?
    suspend fun saveProfile(profile: CarProfile): Long
    suspend fun deleteProfile(profile: CarProfile)
    suspend fun countProfiles(): Int

    // ---------- Служебные ----------
    suspend fun snoozeItem(itemId: Long, untilMillis: Long)
    suspend fun resetAll()
}