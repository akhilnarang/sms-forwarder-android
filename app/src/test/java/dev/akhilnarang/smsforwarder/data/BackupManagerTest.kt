package dev.akhilnarang.smsforwarder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {
    private lateinit var db: AppDatabase
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupManager = BackupManager(
            database = db,
            destinationDao = db.destinationDao(),
            ruleDao = db.forwardingRuleDao(),
            appVersionName = "test",
            clock = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedSample(): Pair<Long, Long> {
        val webhookId = db.destinationDao().insert(
            DestinationEntity(
                label = "Webhook A",
                type = DestinationType.CUSTOM_WEBHOOK,
                endpointUrl = "https://example.com/hook",
                authHeaderName = "X-Auth",
                authHeaderValue = "secret",
                payloadTemplate = "{\"text\":\"{{body}}\"}",
                configJson = null,
                enabled = true,
            )
        )
        val telegramId = db.destinationDao().insert(
            DestinationEntity(
                label = "Telegram",
                type = DestinationType.TELEGRAM_PRESET,
                endpointUrl = "https://api.telegram.org/bot123/sendMessage",
                authHeaderName = null,
                authHeaderValue = null,
                payloadTemplate = null,
                configJson = "{\"chatId\":\"42\"}",
                enabled = false,
            )
        )
        db.forwardingRuleDao().insert(
            ForwardingRuleEntity(
                priority = 1,
                label = "Bank alerts",
                senderPattern = "BANK*",
                bodyContains = "credited",
                destinationId = webhookId,
                customPayloadKeys = null,
                enabled = true,
            )
        )
        db.forwardingRuleDao().insert(
            ForwardingRuleEntity(
                priority = 2,
                label = "OTP",
                senderPattern = "*",
                bodyContains = "OTP",
                destinationId = telegramId,
                customPayloadKeys = "{\"tag\":\"otp\"}",
                enabled = false,
            )
        )
        return webhookId to telegramId
    }

    @Test
    fun `export contains all destinations and rules`() = runTest {
        seedSample()

        val jsonStr = backupManager.exportToJson()
        val backup = Json.decodeFromString(BackupFile.serializer(), jsonStr)

        assertEquals(BACKUP_SCHEMA_VERSION, backup.schemaVersion)
        assertEquals(1_700_000_000_000L, backup.exportedAtEpochMs)
        assertEquals("test", backup.appVersionName)
        assertEquals(2, backup.destinations.size)
        assertEquals(2, backup.rules.size)

        val webhook = backup.destinations.first { it.label == "Webhook A" }
        assertEquals("CUSTOM_WEBHOOK", webhook.type)
        assertEquals("X-Auth", webhook.authHeaderName)
        assertEquals("secret", webhook.authHeaderValue)
        assertTrue(webhook.enabled)

        val telegram = backup.destinations.first { it.label == "Telegram" }
        assertEquals("TELEGRAM_PRESET", telegram.type)
        assertEquals("{\"chatId\":\"42\"}", telegram.configJson)
        assertEquals(false, telegram.enabled)

        // Each rule references a valid destination index.
        backup.rules.forEach { rule ->
            assertTrue(rule.destinationIndex in backup.destinations.indices)
        }

        val otpRule = backup.rules.first { it.label == "OTP" }
        assertEquals("Telegram", backup.destinations[otpRule.destinationIndex].label)
        assertEquals("{\"tag\":\"otp\"}", otpRule.customPayloadKeys)
        assertEquals(false, otpRule.enabled)
    }

    @Test
    fun `import REPLACE wipes existing data and remaps rule destination ids`() = runTest {
        // Seed initial data, export, then add unrelated data we expect to be wiped.
        seedSample()
        val originalJson = backupManager.exportToJson()

        db.destinationDao().insert(
            DestinationEntity(
                label = "Stale",
                type = DestinationType.CUSTOM_WEBHOOK,
                endpointUrl = "https://stale.example",
            )
        )

        val result = backupManager.importFromJson(originalJson, ImportMode.REPLACE)
        assertEquals(2, result.destinationsImported)
        assertEquals(2, result.rulesImported)
        assertEquals(0, result.rulesSkipped)

        val destinations = db.destinationDao().getAll().first()
        assertEquals(2, destinations.size)
        assertEquals(setOf("Webhook A", "Telegram"), destinations.map { it.label }.toSet())

        val rules = db.forwardingRuleDao().getAllSuspend()
        assertEquals(2, rules.size)

        // Rules must still reference correct destinations after the IDs were regenerated.
        val otp = rules.first { it.label == "OTP" }
        val telegram = destinations.first { it.label == "Telegram" }
        assertEquals(telegram.id, otp.destinationId)

        val bank = rules.first { it.label == "Bank alerts" }
        val webhook = destinations.first { it.label == "Webhook A" }
        assertEquals(webhook.id, bank.destinationId)
    }

    @Test
    fun `import MERGE keeps existing data and appends imported entries`() = runTest {
        // Pre-existing destination + rule that should survive the merge.
        val existingDestId = db.destinationDao().insert(
            DestinationEntity(
                label = "Existing",
                type = DestinationType.CUSTOM_WEBHOOK,
                endpointUrl = "https://existing.example",
            )
        )
        db.forwardingRuleDao().insert(
            ForwardingRuleEntity(
                priority = 5,
                label = "Existing rule",
                senderPattern = "EXISTING",
                destinationId = existingDestId,
            )
        )

        // Build a backup payload independently so we don't have to round-trip through the DB.
        val exported = """
            {
              "schemaVersion": $BACKUP_SCHEMA_VERSION,
              "exportedAtEpochMs": 1,
              "destinations": [
                {"label":"Webhook A","type":"CUSTOM_WEBHOOK","endpointUrl":"https://example.com/hook"},
                {"label":"Telegram","type":"TELEGRAM_PRESET","endpointUrl":"https://api.telegram.org/bot123/sendMessage","configJson":"{\"chatId\":\"42\"}","enabled":false}
              ],
              "rules": [
                {"priority":1,"label":"Bank alerts","senderPattern":"BANK*","bodyContains":"credited","destinationIndex":0},
                {"priority":2,"label":"OTP","senderPattern":"*","bodyContains":"OTP","destinationIndex":1,"customPayloadKeys":"{\"tag\":\"otp\"}","enabled":false}
              ]
            }
        """.trimIndent()

        val result = backupManager.importFromJson(exported, ImportMode.MERGE)
        assertEquals(2, result.destinationsImported)
        assertEquals(2, result.rulesImported)

        val destinationLabels = db.destinationDao().getAll().first().map { it.label }
        assertTrue(destinationLabels.contains("Existing"))
        assertTrue(destinationLabels.contains("Webhook A"))
        assertTrue(destinationLabels.contains("Telegram"))

        val ruleLabels = db.forwardingRuleDao().getAllSuspend().map { it.label }
        assertTrue(ruleLabels.contains("Existing rule"))
        assertTrue(ruleLabels.contains("Bank alerts"))
        assertTrue(ruleLabels.contains("OTP"))
    }

    @Test
    fun `import rejects malformed JSON`() = runTest {
        try {
            backupManager.importFromJson("not json at all", ImportMode.REPLACE)
            fail("Expected BackupValidationException")
        } catch (e: BackupValidationException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `import rejects future schema version`() = runTest {
        val futureBackup = """
            {
              "schemaVersion": ${BACKUP_SCHEMA_VERSION + 1},
              "exportedAtEpochMs": 1,
              "destinations": [],
              "rules": []
            }
        """.trimIndent()
        try {
            backupManager.importFromJson(futureBackup, ImportMode.REPLACE)
            fail("Expected BackupValidationException")
        } catch (e: BackupValidationException) {
            assertTrue(e.message!!.contains("schema version"))
        }
    }

    @Test
    fun `import rejects rule with out-of-range destinationIndex`() = runTest {
        val badBackup = """
            {
              "schemaVersion": $BACKUP_SCHEMA_VERSION,
              "exportedAtEpochMs": 1,
              "destinations": [
                {"label":"A","type":"CUSTOM_WEBHOOK","endpointUrl":"https://x"}
              ],
              "rules": [
                {"priority":1,"label":"R","senderPattern":"*","destinationIndex":7}
              ]
            }
        """.trimIndent()
        try {
            backupManager.importFromJson(badBackup, ImportMode.REPLACE)
            fail("Expected BackupValidationException")
        } catch (e: BackupValidationException) {
            assertTrue(e.message!!.contains("destination"))
        }
    }

    @Test
    fun `import rejects unknown destination type`() = runTest {
        val badBackup = """
            {
              "schemaVersion": $BACKUP_SCHEMA_VERSION,
              "exportedAtEpochMs": 1,
              "destinations": [
                {"label":"A","type":"NOT_A_REAL_TYPE","endpointUrl":"https://x"}
              ],
              "rules": []
            }
        """.trimIndent()
        try {
            backupManager.importFromJson(badBackup, ImportMode.REPLACE)
            fail("Expected BackupValidationException")
        } catch (e: BackupValidationException) {
            assertTrue(e.message!!.contains("destination type"))
        }
    }

    @Test
    fun `import REPLACE failure does not partially clear data`() = runTest {
        seedSample()
        val originalSize = db.destinationDao().getAll().first().size
        val originalRules = db.forwardingRuleDao().getAllSuspend().size

        val invalidBackup = """
            {
              "schemaVersion": $BACKUP_SCHEMA_VERSION,
              "exportedAtEpochMs": 1,
              "destinations": [
                {"label":"A","type":"CUSTOM_WEBHOOK","endpointUrl":"https://x"}
              ],
              "rules": [
                {"priority":1,"label":"R","senderPattern":"*","destinationIndex":99}
              ]
            }
        """.trimIndent()

        try {
            backupManager.importFromJson(invalidBackup, ImportMode.REPLACE)
            fail("Expected BackupValidationException")
        } catch (_: BackupValidationException) {
        }

        // Validation runs before the transaction, so existing data is intact.
        assertEquals(originalSize, db.destinationDao().getAll().first().size)
        assertEquals(originalRules, db.forwardingRuleDao().getAllSuspend().size)
    }

    @Test
    fun `roundtrip preserves all destination and rule fields`() = runTest {
        seedSample()
        val exported = backupManager.exportToJson()

        backupManager.importFromJson(exported, ImportMode.REPLACE)

        val destinations = db.destinationDao().getAll().first().sortedBy { it.label }
        assertEquals(2, destinations.size)
        val telegram = destinations.first { it.label == "Telegram" }
        assertEquals(DestinationType.TELEGRAM_PRESET, telegram.type)
        assertEquals("https://api.telegram.org/bot123/sendMessage", telegram.endpointUrl)
        assertEquals("{\"chatId\":\"42\"}", telegram.configJson)
        assertNull(telegram.authHeaderName)
        assertEquals(false, telegram.enabled)

        val webhook = destinations.first { it.label == "Webhook A" }
        assertEquals(DestinationType.CUSTOM_WEBHOOK, webhook.type)
        assertEquals("X-Auth", webhook.authHeaderName)
        assertEquals("secret", webhook.authHeaderValue)
        assertEquals("{\"text\":\"{{body}}\"}", webhook.payloadTemplate)

        val rules = db.forwardingRuleDao().getAllSuspend()
        val otp = rules.first { it.label == "OTP" }
        assertEquals(2, otp.priority)
        assertEquals("OTP", otp.bodyContains)
        assertEquals("{\"tag\":\"otp\"}", otp.customPayloadKeys)
        assertEquals(false, otp.enabled)
        assertEquals(telegram.id, otp.destinationId)
        assertNotEquals(0L, otp.id)
    }
}
