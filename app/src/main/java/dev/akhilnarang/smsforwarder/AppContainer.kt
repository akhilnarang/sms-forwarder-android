package dev.akhilnarang.smsforwarder

import android.content.Context
import androidx.room.Room
import dev.akhilnarang.smsforwarder.data.AppDatabase
import dev.akhilnarang.smsforwarder.data.DatabaseKeyProvider
import dev.akhilnarang.smsforwarder.data.DestinationRepository
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.data.ForwardingRuleRepository
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.network.SmsForwardClient
import dev.akhilnarang.smsforwarder.sms.DeviceSmsScanner
import dev.akhilnarang.smsforwarder.sms.SmsProcessor
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database: AppDatabase

    init {
        SQLiteDatabase.loadLibs(appContext)

        val dbKeyProvider = DatabaseKeyProvider(appContext)
        val passphrase = dbKeyProvider.getOrCreatePassphrase()
        val supportFactory = SupportFactory(passphrase)

        database =
            Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(supportFactory)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()
    }

    val destinationRepository = DestinationRepository(database.destinationDao())
    val ruleRepository = ForwardingRuleRepository(database.forwardingRuleDao())
    val forwardRecordRepository = ForwardRecordRepository(database.forwardRecordDao())
    val workScheduler = ForwardWorkScheduler(appContext)
    val forwardClient = SmsForwardClient()
    val payloadFactory = ForwardPayloadFactory()
    val deviceSmsScanner = DeviceSmsScanner(appContext)

    val smsProcessor =
        SmsProcessor(
            ruleDao = database.forwardingRuleDao(),
            destinationDao = database.destinationDao(),
            forwardRecordRepository = forwardRecordRepository,
            payloadFactory = payloadFactory,
            workScheduler = workScheduler,
        )

    private companion object {
        const val DB_NAME = "sms-forwarder.db"
    }
}
