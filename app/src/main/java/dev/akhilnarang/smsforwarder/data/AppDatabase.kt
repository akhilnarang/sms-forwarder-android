package dev.akhilnarang.smsforwarder.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConfiguredSenderEntity::class, ForwardRecordEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(AppDatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configuredSenderDao(): ConfiguredSenderDao
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
    }
}

class AppDatabaseConverters {
    @TypeConverter
    fun fromDeliveryStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)
}
