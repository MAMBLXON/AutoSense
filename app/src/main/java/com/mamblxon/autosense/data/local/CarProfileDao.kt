package com.mamblxon.autosense.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamblxon.autosense.domain.model.CarProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface CarProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: CarProfile): Long

    @Update
    suspend fun update(profile: CarProfile)

    @Delete
    suspend fun delete(profile: CarProfile)

    @Query("SELECT * FROM car_profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CarProfile>>

    @Query("SELECT * FROM car_profiles WHERE catalogId = :catalogId LIMIT 1")
    suspend fun getByCatalogId(catalogId: String): CarProfile?

    @Query("SELECT * FROM car_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CarProfile?

    @Query("SELECT COUNT(*) FROM car_profiles")
    suspend fun count(): Int

    @Query("DELETE FROM car_profiles")
    suspend fun clear()
}