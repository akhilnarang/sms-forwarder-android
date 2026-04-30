package dev.akhilnarang.smsforwarder.data

import android.database.sqlite.SQLiteConstraintException
import dev.akhilnarang.smsforwarder.sms.normalizeSender
import kotlinx.coroutines.flow.Flow

class ConfiguredSenderRepository(
    private val dao: ConfiguredSenderDao,
) {
    fun observeAll(): Flow<List<ConfiguredSenderEntity>> = dao.observeAll()

    suspend fun getMatchingEnabledSender(normalizedSender: String): ConfiguredSenderEntity? =
        dao.getEnabledSenders().firstOrNull { configured ->
            if (configured.normalizedSender.contains("*")) {
                val escapedString = Regex.escape(configured.normalizedSender)
                val regexPattern = escapedString.replace("\\*", ".*").toRegex()
                normalizedSender.matches(regexPattern)
            } else {
                configured.normalizedSender == normalizedSender
            }
        }

    suspend fun addSender(label: String, rawSender: String) {
        val normalizedSender = normalizeSender(rawSender)
        require(normalizedSender.isNotBlank()) { "Sender value cannot be blank." }

        val displayLabel = label.trim().ifBlank { rawSender.trim() }
        try {
            dao.insert(
                ConfiguredSenderEntity(
                    label = displayLabel,
                    rawSender = rawSender.trim(),
                    normalizedSender = normalizedSender,
                ),
            )
        } catch (_: SQLiteConstraintException) {
            throw IllegalArgumentException("That sender is already configured.")
        }
    }

    suspend fun setEnabled(sender: ConfiguredSenderEntity, enabled: Boolean) {
        dao.update(sender.copy(enabled = enabled))
    }

    suspend fun deleteSender(id: Long) {
        dao.deleteById(id)
    }
}

