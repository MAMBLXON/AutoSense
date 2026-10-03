package com.mamblxon.autosense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mamblxon.autosense.domain.model.CarProfile
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.model.ServiceRecord

@Database(
    entities = [ServiceItem::class, ServiceRecord::class, CarProfile::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun carProfileDao(): CarProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `serviceItemId` INTEGER NOT NULL,
                        `km` INTEGER NOT NULL,
                        `dateMillis` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        FOREIGN KEY(`serviceItemId`) REFERENCES `service_items`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_service_records_serviceItemId` " +
                            "ON `service_records` (`serviceItemId`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `service_items` ADD COLUMN `snoozedUntilMillis` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_items_car` ON `service_items` (`car`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_records_dateMillis` ON `service_records` (`dateMillis`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `service_items` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'Прочее'")
                db.execSQL("ALTER TABLE `service_items` ADD COLUMN `fluidSpec` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `service_items` ADD COLUMN `volume` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `service_items` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `car_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `catalogId` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `engineCode` TEXT NOT NULL,
                        `transmission` TEXT NOT NULL,
                        `vin` TEXT NOT NULL DEFAULT '',
                        `currentKm` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_car_profiles_catalogId` ON `car_profiles` (`catalogId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `service_records` ADD COLUMN `cost` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Пересоздаём car_profiles без unique-индекса на catalogId
                db.execSQL("ALTER TABLE `car_profiles` RENAME TO `car_profiles_old`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `car_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `catalogId` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `engineCode` TEXT NOT NULL,
                        `transmission` TEXT NOT NULL,
                        `vin` TEXT NOT NULL DEFAULT '',
                        `currentKm` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `car_profiles`
                    (id, catalogId, year, engineCode, transmission, vin, currentKm, createdAt)
                    SELECT id, catalogId, year, engineCode, transmission, vin, currentKm, createdAt
                    FROM `car_profiles_old`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `car_profiles_old`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_car_profiles_catalogId` ON `car_profiles` (`catalogId`)")

                // 2. Добавляем profileId в service_items
                db.execSQL("ALTER TABLE `service_items` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_items_profileId` ON `service_items` (`profileId`)")

                // 3. Проставляем profileId по совпадению catalogId
                db.execSQL(
                    """
                    UPDATE `service_items`
                    SET `profileId` = COALESCE(
                        (SELECT `id` FROM `car_profiles`
                         WHERE `car_profiles`.`catalogId` = `service_items`.`car`
                         LIMIT 1),
                        0
                    )
                    WHERE `profileId` = 0
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autosense.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}