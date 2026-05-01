package dev.akhilnarang.smsforwarder.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DestinationEntity::class, ForwardingRuleEntity::class, ForwardRecordEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun destinationDao(): DestinationDao
    abstract fun forwardingRuleDao(): ForwardingRuleDao
    abstract fun forwardRecordDao(): ForwardRecordDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE forward_records ADD COLUMN isTestRecord INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE forward_records ADD COLUMN destinationId INTEGER DEFAULT NULL",
                    )
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Create destinations table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `destinations` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `label` TEXT NOT NULL, 
                            `type` TEXT NOT NULL, 
                            `endpointUrl` TEXT NOT NULL, 
                            `authHeaderName` TEXT, 
                            `authHeaderValue` TEXT, 
                            `payloadTemplate` TEXT, 
                            `configJson` TEXT, 
                            `enabled` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // Create forwarding_rules table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `forwarding_rules` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `priority` INTEGER NOT NULL, 
                            `label` TEXT NOT NULL, 
                            `senderPattern` TEXT NOT NULL, 
                            `bodyContains` TEXT, 
                            `destinationId` INTEGER NOT NULL, 
                            `customPayloadKeys` TEXT, 
                            `enabled` INTEGER NOT NULL, 
                            FOREIGN KEY(`destinationId`) REFERENCES `destinations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_forwarding_rules_destinationId` ON `forwarding_rules` (`destinationId`)")

                    // We need a default destination if none exists, but Room migrations shouldn't rely on SharedPreferences directly.
                    // The AppSettings to default destination migration will happen at app startup or when accessed.

                    // Drop old table
                    db.execSQL("DROP TABLE IF EXISTS `configured_senders`")
                }
            }
    }
}

class AppDatabaseConverters {
    @TypeConverter
    fun fromDeliveryStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)

    @TypeConverter
    fun fromDestinationType(value: DestinationType): String = value.name

    @TypeConverter
    fun toDestinationType(value: String): DestinationType = DestinationType.valueOf(value)
}
