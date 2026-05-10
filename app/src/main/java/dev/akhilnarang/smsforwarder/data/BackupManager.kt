package dev.akhilnarang.smsforwarder.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupManager(
    private val database: AppDatabase,
    private val destinationDao: DestinationDao,
    private val ruleDao: ForwardingRuleDao,
    private val appVersionName: String? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportToJson(): String {
        val destinations = destinationDao.getAll().first()
        val rules = ruleDao.getAllSuspend()
        val destinationIndexById = destinations
            .mapIndexed { index, dest -> dest.id to index }
            .toMap()

        val backupDestinations = destinations.map { it.toBackup() }
        val backupRules = rules.mapNotNull { rule ->
            val idx = destinationIndexById[rule.destinationId] ?: return@mapNotNull null
            rule.toBackup(idx)
        }

        val backup = BackupFile(
            exportedAtEpochMs = clock(),
            appVersionName = appVersionName,
            destinations = backupDestinations,
            rules = backupRules,
        )
        return withContext(Dispatchers.Default) { json.encodeToString(backup) }
    }

    suspend fun importFromJson(jsonString: String, mode: ImportMode): ImportResult {
        val backup = parseAndValidate(jsonString)

        return database.withTransaction {
            if (mode == ImportMode.REPLACE) {
                // Cascade delete on destinations also wipes rules, but be explicit.
                ruleDao.deleteAll()
                destinationDao.deleteAll()
            }

            val newIdsByIndex = HashMap<Int, Long>(backup.destinations.size)
            backup.destinations.forEachIndexed { index, dest ->
                val type = parseDestinationType(dest.type)
                    ?: throw BackupValidationException("Unknown destination type '${dest.type}' at index $index")
                val id = destinationDao.insert(dest.toEntity(type))
                newIdsByIndex[index] = id
            }

            var skipped = 0
            backup.rules.forEach { rule ->
                val destId = newIdsByIndex[rule.destinationIndex]
                if (destId == null) {
                    skipped++
                    return@forEach
                }
                ruleDao.insert(rule.toEntity(destId))
            }

            ImportResult(
                destinationsImported = backup.destinations.size,
                rulesImported = backup.rules.size - skipped,
                rulesSkipped = skipped,
            )
        }
    }

    private suspend fun parseAndValidate(jsonString: String): BackupFile {
        val backup = try {
            withContext(Dispatchers.Default) { json.decodeFromString<BackupFile>(jsonString) }
        } catch (e: SerializationException) {
            throw BackupValidationException("Backup file is not valid JSON or has the wrong shape", e)
        } catch (e: IllegalArgumentException) {
            throw BackupValidationException("Backup file is malformed", e)
        }
        if (backup.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw BackupValidationException(
                "Backup schema version ${backup.schemaVersion} is newer than supported ($BACKUP_SCHEMA_VERSION)"
            )
        }
        backup.rules.forEachIndexed { i, rule ->
            if (rule.destinationIndex !in backup.destinations.indices) {
                throw BackupValidationException(
                    "Rule at index $i references unknown destination ${rule.destinationIndex}"
                )
            }
        }
        return backup
    }

    private fun parseDestinationType(raw: String): DestinationType? =
        runCatching { DestinationType.valueOf(raw) }.getOrNull()
}

class BackupValidationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

private fun DestinationEntity.toBackup(): BackupDestination = BackupDestination(
    label = label,
    type = type.name,
    endpointUrl = endpointUrl,
    authHeaderName = authHeaderName,
    authHeaderValue = authHeaderValue,
    payloadTemplate = payloadTemplate,
    configJson = configJson,
    enabled = enabled,
)

private fun BackupDestination.toEntity(type: DestinationType): DestinationEntity = DestinationEntity(
    label = label,
    type = type,
    endpointUrl = endpointUrl,
    authHeaderName = authHeaderName,
    authHeaderValue = authHeaderValue,
    payloadTemplate = payloadTemplate,
    configJson = configJson,
    enabled = enabled,
)

private fun ForwardingRuleEntity.toBackup(destinationIndex: Int): BackupRule = BackupRule(
    priority = priority,
    label = label,
    senderPattern = senderPattern,
    bodyContains = bodyContains,
    destinationIndex = destinationIndex,
    customPayloadKeys = customPayloadKeys,
    enabled = enabled,
)

private fun BackupRule.toEntity(destinationId: Long): ForwardingRuleEntity = ForwardingRuleEntity(
    priority = priority,
    label = label,
    senderPattern = senderPattern,
    bodyContains = bodyContains,
    destinationId = destinationId,
    customPayloadKeys = customPayloadKeys,
    enabled = enabled,
)
