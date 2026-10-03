package com.mamblxon.autosense.data

import android.content.Context
import com.mamblxon.autosense.data.local.AppDatabase
import com.mamblxon.autosense.data.local.CarProfileDao
import com.mamblxon.autosense.data.local.ServiceDao
import com.mamblxon.autosense.data.local.ServiceRecordDao
import com.mamblxon.autosense.data.remote.CarCatalog
import com.mamblxon.autosense.data.remote.CarCatalogLoader
import com.mamblxon.autosense.data.remote.CarRegulation
import com.mamblxon.autosense.data.remote.RegulationsLoader
import com.mamblxon.autosense.domain.model.CarProfile
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.model.ServiceRecord
import com.mamblxon.autosense.domain.model.ServiceRecordWithName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DefaultServiceRepository(
    private val serviceDao: ServiceDao,
    private val recordDao: ServiceRecordDao,
    private val carProfileDao: CarProfileDao,
    private val regulationsLoader: suspend () -> List<CarRegulation>,
    private val catalogLoader: suspend () -> CarCatalog,
    private val clearAllTables: suspend () -> Unit
) : ServiceRepository {

    // ---------- ServiceItem ----------

    override fun observeItems(profileId: Long): Flow<List<ServiceItem>> =
        serviceDao.getAllByProfile(profileId)

    override suspend fun getItemsOnce(profileId: Long): List<ServiceItem> =
        serviceDao.getAllOnceByProfile(profileId)

    override suspend fun searchItems(profileId: Long, query: String): List<ServiceItem> =
        serviceDao.searchByTitle(profileId, escapeLike(query.trim()))

    /**
     * Экранирование спецсимволов LIKE (% и _), чтобы пользовательский ввод
     * («100% масло», «_filter») искал дословно, а не как шаблон.
     * Совместимо с ESCAPE '\' в запросе ServiceDao.searchByTitle.
     */
    private fun escapeLike(raw: String): String = buildString(raw.length + 4) {
        for (ch in raw) {
            when (ch) {
                '%', '_', '\\' -> append('\\').append(ch)
                else -> append(ch)
            }
        }
    }

    override suspend fun getItemById(id: Long): ServiceItem? =
        serviceDao.getById(id)

    override suspend fun countItems(profileId: Long): Int =
        serviceDao.countByProfile(profileId)

    override suspend fun insertItems(items: List<ServiceItem>) =
        serviceDao.insertAll(items)

    override suspend fun insertItem(item: ServiceItem): Long =
        serviceDao.insert(item)

    override suspend fun updateItem(item: ServiceItem) =
        serviceDao.update(item)

    override suspend fun updateItems(items: List<ServiceItem>) =
        serviceDao.updateAll(items)

    override suspend fun deleteItem(item: ServiceItem) =
        serviceDao.delete(item)

    override suspend fun clearItems(profileId: Long) =
        serviceDao.clearByProfile(profileId)

    // ---------- История ----------

    override fun observeHistory(itemId: Long): Flow<List<ServiceRecord>> =
        recordDao.getForItem(itemId)

    override fun observeRecordsByCar(catalogId: String): Flow<List<ServiceRecordWithName>> =
        recordDao.observeRecordsByCar(catalogId)

    override fun observeTotalCost(catalogId: String): Flow<Int?> =
        recordDao.observeTotalCost(catalogId)

    override suspend fun getAllRecords(): List<ServiceRecord> =
        recordDao.getAllRecords()

    override suspend fun insertRecord(record: ServiceRecord) {
        recordDao.insert(record)
    }

    // ---------- Регламенты ----------

    override suspend fun loadRegulations(): List<CarRegulation> =
        regulationsLoader()

    // ---------- Каталог ----------

    override suspend fun loadCatalog(): CarCatalog = catalogLoader()

    // ---------- Профили ----------

    override fun observeProfiles(): Flow<List<CarProfile>> =
        carProfileDao.observeAll()

    override suspend fun getProfileById(id: Long): CarProfile? =
        carProfileDao.getById(id)

    override suspend fun getProfile(catalogId: String): CarProfile? =
        carProfileDao.getByCatalogId(catalogId)

    override suspend fun saveProfile(profile: CarProfile): Long =
        carProfileDao.insert(profile)

    override suspend fun deleteProfile(profile: CarProfile) =
        carProfileDao.delete(profile)

    override suspend fun countProfiles(): Int =
        carProfileDao.count()

    // ---------- Служебные ----------

    override suspend fun snoozeItem(itemId: Long, untilMillis: Long) {
        val item = serviceDao.getById(itemId) ?: return
        serviceDao.update(item.copy(snoozedUntilMillis = untilMillis))
    }

    override suspend fun resetAll() {
        clearAllTables()
    }

    companion object {
        @Volatile
        private var INSTANCE: ServiceRepository? = null

        fun get(context: Context): ServiceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(appContext: Context): ServiceRepository {
            val db = AppDatabase.get(appContext)
            return DefaultServiceRepository(
                serviceDao = db.serviceDao(),
                recordDao = db.serviceRecordDao(),
                carProfileDao = db.carProfileDao(),
                regulationsLoader = {
                    withContext(Dispatchers.IO) {
                        RegulationsLoader.loadRegulations(appContext)
                    }
                },
                catalogLoader = {
                    withContext(Dispatchers.IO) {
                        CarCatalogLoader.loadCatalog(appContext)
                    }
                },
                clearAllTables = {
                    withContext(Dispatchers.IO) {
                        db.clearAllTables()
                    }
                }
            )
        }

        internal fun resetForTests() {
            INSTANCE = null
        }
    }
}