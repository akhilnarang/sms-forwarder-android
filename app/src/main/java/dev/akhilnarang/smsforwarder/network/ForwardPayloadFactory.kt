package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.sms.IncomingSms
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant

@Serializable
data class ForwardPayload(
    val sender: String,
    val body: String,
    @SerialName("received_at")
    val receivedAt: String
)

class ForwardPayloadFactory {
    private val json = Json { encodeDefaults = true }

    fun createJson(incomingSms: IncomingSms, customKeysMap: Map<String, String> = emptyMap()): String {
        val baseString = json.encodeToString(
            ForwardPayload(
                sender = incomingSms.senderRaw,
                body = incomingSms.body,
                receivedAt = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
            ),
        )
        if (customKeysMap.isEmpty()) return baseString

        val obj = JSONObject(baseString)
        for ((key, value) in customKeysMap) {
            obj.put(key, value)
        }
        return obj.toString()
    }
        
    fun createCustomJson(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        val receivedAtIso = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
        val replacements = buildMap<String, String> {
            put("sender", incomingSms.senderRaw)
            put("body", incomingSms.body)
            put("received_at", receivedAtIso)
            putAll(customKeysMap)
        }

        val root: Any = try {
            JSONTokener(template).nextValue()
        } catch (e: Exception) {
            throw IllegalArgumentException("Custom payload template is not valid JSON", e)
        }
        if (root !is JSONObject && root !is JSONArray) {
            throw IllegalArgumentException("Custom payload template must be a JSON object or array, got: ${root::class.simpleName}")
        }

        val placeholderRegex = Regex("\\{\\{([^{}]+)\\}\\}")
        fun resolveLeaf(s: String): String {
            return placeholderRegex.replace(s) { m -> replacements[m.groupValues[1]] ?: m.value }
        }

        fun walk(node: Any?): Any? = when (node) {
            is JSONObject -> JSONObject().also { out ->
                node.keys().forEach { k -> out.put(k, walk(node.get(k))) }
            }
            is JSONArray -> JSONArray().also { out ->
                for (i in 0 until node.length()) out.put(walk(node.get(i)))
            }
            is String -> resolveLeaf(node)
            else -> node
        }

        val walked = walk(root)
        if (walked is JSONObject) {
            for ((key, value) in customKeysMap) {
                if (!walked.has(key)) {
                    walked.put(key, try { JSONTokener(value).nextValue() } catch (_: Exception) { value })
                }
            }
        }
        return walked.toString()
    }

    fun createTextTemplate(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        val receivedAtIso = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
        val replacements = mutableMapOf(
            "sender" to incomingSms.senderRaw,
            "body" to incomingSms.body,
            "received_at" to receivedAtIso
        )
        for ((key, value) in customKeysMap) {
            replacements[key] = value
        }

        val regex = Regex("\\{\\{([^{}]+)\\}\\}")
        return regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            replacements[key] ?: matchResult.value
        }
    }

    fun createTelegramText(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val receivedAtIso = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
        val replacements = mutableMapOf(
            "sender" to esc(incomingSms.senderRaw),
            "body" to esc(incomingSms.body),
            "received_at" to receivedAtIso
        )
        for ((key, value) in customKeysMap) {
            replacements[key] = esc(value)
        }

        val regex = Regex("\\{\\{([^{}]+)\\}\\}")
        return regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            replacements[key] ?: matchResult.value
        }
    }
}
