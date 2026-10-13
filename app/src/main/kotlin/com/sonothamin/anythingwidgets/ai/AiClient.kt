package com.sonothamin.anythingwidgets.ai

import android.content.Context
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** One key that was tried and didn't work, kept so the UI can explain what happened. */
class AiFailure(val keyLabel: String, val message: String)

class AiResult(val spec: WidgetSpec, val raw: String, val usedLabel: String, val failures: List<AiFailure>)

class AiException(message: String, val failures: List<AiFailure>) : Exception(message)

object AiClient {
    private const val MAX_RESPONSE_CHARS = 512 * 1024

    /**
     * Generates (or, with [current], revises) a widget. Walks the saved keys in priority order and
     * falls through to the next one on any failure: network, HTTP error, or an answer that isn't a valid spec.
     */
    suspend fun generate(context: Context, request: String, current: WidgetSpec? = null, currentRaw: String? = null): AiResult =
        withContext(Dispatchers.IO) {
            val keys = AiKeyStore(context).load().filter { it.usable }
            if (keys.isEmpty()) throw AiException("No AI keys are set up. Add one in Settings.", emptyList())

            val system = AiPrompt.system(context)
            val user = if (current != null && currentRaw != null) AiPrompt.refine(currentRaw, request) else AiPrompt.create(request)
            val failures = mutableListOf<AiFailure>()

            for (key in keys) {
                try {
                    val text = call(key, system, user)
                    val (spec, raw) = parse(context, text, forceId = current?.id, previousVersion = current?.version)
                    return@withContext AiResult(spec, raw, key.label, failures)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += AiFailure(key.label, e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "Failed")
                }
            }
            throw AiException("Every key failed.", failures)
        }

    // --- parsing -------------------------------------------------------------------------------

    private fun parse(context: Context, text: String, forceId: String?, previousVersion: Int?): Pair<WidgetSpec, String> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("Answer had no JSON in it")
        val first = SpecCodec.json.decodeFromString<WidgetSpec>(text.substring(start, end + 1))

        val id = forceId ?: uniqueId(context, first.name)
        val spec = first.copy(
            schema = 2,
            id = id,
            version = (previousVersion ?: 0) + 1,
            author = "AI",
            created = first.created?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ?: LocalDate.now().toString(),
        )
        val raw = SpecCodec.json.encodeToString(spec)
        // Full validation, exactly as an install from any other source would get.
        return SpecCodec.parseSpec(raw) to raw
    }

    private fun uniqueId(context: Context, name: String): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "widget" }
        val store = SpecStore(context)
        val base = "ai-$slug"
        var id = base
        var n = 2
        while (store.load(id) != null) id = "$base-${n++}"
        return id
    }

    // --- providers -----------------------------------------------------------------------------

    private fun call(key: AiKey, system: String, user: String): String {
        val base = key.baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || (key.preset.allowHttp && base.startsWith("http://"))) {
            if (key.preset.allowHttp) "Base URL must start with http:// or https://" else "Base URL must be https"
        }
        return when (key.preset.protocol) {
            AiProtocol.ANTHROPIC -> callAnthropic(key, base, system, user)
            AiProtocol.OPENAI_COMPAT -> callOpenAiCompat(key, base, system, user)
        }
    }

    private fun callOpenAiCompat(key: AiKey, base: String, system: String, user: String): String {
        val body = buildJsonObject {
            put("model", key.model.trim())
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
            if (key.jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
        }
        val headers = buildMap {
            put("Content-Type", "application/json")
            if (key.apiKey.isNotBlank()) put("Authorization", "Bearer ${key.apiKey.trim()}")
        }
        val reply = post("$base/chat/completions", headers, body.toString())
        val content = SpecCodec.json.parseToJsonElement(reply).jsonObject["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")
            ?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return content?.takeIf { it.isNotBlank() } ?: throw IOException("Empty answer")
    }

    private fun callAnthropic(key: AiKey, base: String, system: String, user: String): String {
        val body = buildJsonObject {
            put("model", key.model.trim())
            put("max_tokens", 4096)
            put("system", system)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to key.apiKey.trim(),
            "anthropic-version" to "2023-06-01",
        )
        val reply = post("$base/v1/messages", headers, body.toString())
        val text = SpecCodec.json.parseToJsonElement(reply).jsonObject["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject.takeIf { b -> b["type"]?.jsonPrimitive?.contentOrNull == "text" }?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
        return text?.takeIf { it.isNotBlank() } ?: throw IOException("Empty answer")
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000 // models can be slow, local ones especially
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(MAX_RESPONSE_CHARS)
            if (code !in 200..299) throw IOException("HTTP $code${errorDetail(text)?.let { ": $it" } ?: ""}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** Pulls a readable message out of the common `{"error":{"message":...}}` shapes. */
    private fun errorDetail(body: String): String? = runCatching {
        val root = SpecCodec.json.parseToJsonElement(body)
        val obj = (root as? JsonObject) ?: (root as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonObject
        val err = obj?.get("error")
        when (err) {
            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> err.contentOrNull
            else -> null
        }
    }.getOrNull()
}
