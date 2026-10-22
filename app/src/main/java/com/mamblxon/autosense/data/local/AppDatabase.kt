package com.mamblxon.autosense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mamblxon.autosense.domain.model.CarProfile
import com.mamblxon.autosense.domain.model.ServiceItem
import com.mamblxon.autosense.domain.model.ServiceRecord

@Database(
    entities = [ServiceItem::class, ServiceRecord::class, CarProfile::class],
    version = 8,
    exportSchema = true
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

        /**
         * v7 → v8: связь целостности на уровне БД:
         * service_items.profileId → car_profiles.id ON DELETE CASCADE (до этого сироты
         * удалялись только ручным clearItems в ProfilesViewModel). Индексы остаются
         * обычными (совпадают с декларациями @Entity — иначе Room не пройдёт проверку схемы).
         * Таблица service_items пересоздаётся с копированием данных; сироты (profileId без
         * профиля, значение 0 из старых версий) удаляются, чтобы FK-проверки не падали.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Пересоздаём service_items с FK на car_profiles.
                // PRAGMA foreign_keys здесь не трогаем: Room выполняет миграцию внутри
                // транзакции, где этот pragma всё равно игнорируется SQLite; проверка FK
                // включена постоянно через addCallback(onCreate/onOpen) ниже.
                db.execSQL("ALTER TABLE `service_items` RENAME TO `service_items_old7`")
                db.execSQL(
                    """
                    CREATE TABLE `service_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` INTEGER NOT NULL,
                        `car` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL DEFAULT 'Прочее',
                        `intervalKm` INTEGER NOT NULL,
                        `intervalMonths` INTEGER NOT NULL,
                        `lastServiceKm` INTEGER NOT NULL,
                        `lastServiceDateMillis` INTEGER NOT NULL,
                        `currentKm` INTEGER NOT NULL DEFAULT 0,
                        `urgency` INTEGER NOT NULL DEFAULT 0,
                        `snoozedUntilMillis` INTEGER NOT NULL DEFAULT 0,
                        `fluidSpec` TEXT NOT NULL DEFAULT '',
                        `volume` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`profileId`) REFERENCES `car_profiles`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `service_items`
                    (id, profileId, car, name, category, intervalKm, intervalMonths, lastServiceKm,
                     lastServiceDateMillis, currentKm, urgency, snoozedUntilMillis, fluidSpec, volume, notes)
                    SELECT id, profileId, car, name, category, intervalKm, intervalMonths, lastServiceKm,
                           lastServiceDateMillis, currentKm, urgency, snoozedUntilMillis, fluidSpec, volume, notes
                    FROM `service_items_old7`
                    """.trimIndent()
                )
                // Сначала чистим сирот (profileId без профиля, значение 0 из старых версий),
                // пока индексов на новой таблице ещё нет — иначе CREATE UNIQUE INDEX упадёт.
                db.execSQL("DELETE FROM `service_items` WHERE `profileId` NOT IN (SELECT `id` FROM `car_profiles`)")
                db.execSQL("DROP TABLE `service_items_old7`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_items_profileId_name` " +
                            "ON `service_items` (`profileId`, `name`)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_items_car` ON `service_items` (`car`)")
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
                    // SQLite по умолчанию НЕ проверяет внешние ключи на каждое соединение;
                    // без этого pragma ON DELETE CASCADE для service_items/service_records не работает.
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("PRAGMA foreign_keys = ON")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            db.execSQL("PRAGMA foreign_keys = ON")
                        }
                    })
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}