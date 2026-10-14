package com.sonothamin.anythingwidgets.ai

import android.content.Context
import com.sonothamin.anythingwidgets.debug.DebugLog
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** One key that was tried and didn't work, kept so the UI can explain what happened. */
class AiFailure(val keyLabel: String, val message: String)

class AiResult(
    val spec: WidgetSpec,
    val raw: String,
    /** The saved key's own name, e.g. "Work Gemini". */
    val usedLabel: String,
    /** Provider and model that actually answered, e.g. "Gemini" / "gemini-2.5-flash". */
    val provider: String,
    val model: String,
    val millis: Long,
    val failures: List<AiFailure>,
)

class AiException(message: String, val failures: List<AiFailure>) : Exception(message)

object AiClient {
    private const val MAX_RESPONSE_CHARS = 512 * 1024

    /**
     * Generates (or, with [current], revises) a widget. Walks the saved keys in priority order and
     * falls through to the next one on any failure: network, HTTP error, or an answer that isn't a valid spec.
     */
    suspend fun generate(
        context: Context,
        request: String,
        current: WidgetSpec? = null,
        currentRaw: String? = null,
        /** Called just before each key is tried, so the UI can say who is being asked. */
        onAttempt: (AiKey) -> Unit = {},
    ): AiResult =
        withContext(Dispatchers.IO) {
            val trace = Trace(newId())
            val begun = System.currentTimeMillis()
            val keys = AiKeyStore(context).load().filter { it.usable }
            if (keys.isEmpty()) {
                log(context, DebugLog.Level.ERROR, "ai", trace) { "Generate aborted: no usable AI keys" }
                throw AiException("No AI keys are set up. Add one in Settings.", emptyList())
            }

            val system = AiPrompt.system(context, request, currentRaw, AiPrompt.Tier.STANDARD)
            val user = if (current != null && currentRaw != null) AiPrompt.refine(currentRaw, request) else AiPrompt.create(request)
            val failures = mutableListOf<AiFailure>()

            log(context, DebugLog.Level.INFO, "ai", trace) {
                "Generate start: " + (if (current != null) "refine ${current.id} v${current.version}" else "create") +
                    ", ${keys.size} usable key(s) in order: " + keys.joinToString(" | ") { describe(it) }
            }
            log(context, DebugLog.Level.INFO, "ai", trace) { "Request text (${request.length} chars): $request" }
            log(context, DebugLog.Level.DEBUG, "ai", trace) {
                "Prompts built: system ${system.length} chars, user ${user.length} chars (full text is in the ai-http request body)"
            }

            for ((i, key) in keys.withIndex()) {
                onAttempt(key)
                trace.secret = key.apiKey.trim()
                log(context, DebugLog.Level.INFO, "ai", trace) { "Attempt ${i + 1}/${keys.size}: ${describe(key)}" }
                var prompt = system
                var minimal = false
                while (true) {
                    trace.enter("starting")
                    val started = System.currentTimeMillis()
                    try {
                        val text = withHeartbeat(context, trace, "ai") { call(context, trace, key, prompt, user) }
                        trace.enter("parsing the answer")
                        val (spec, raw) = parse(context, trace, text, forceId = current?.id, previousVersion = current?.version)
                        val took = System.currentTimeMillis() - started
                        log(context, DebugLog.Level.INFO, "ai", trace) {
                            "${key.preset.label} ${key.model} answered in ${took}ms; spec id=${spec.id} name=\"${spec.name}\" " +
                                "v${spec.version} (${raw.length} chars); ${failures.size} earlier key failure(s); total ${System.currentTimeMillis() - begun}ms"
                        }
                        return@withContext AiResult(spec, raw, key.label, key.preset.label, key.model.trim(), took, failures)
                    } catch (e: CancellationException) {
                        log(context, DebugLog.Level.WARN, "ai", trace) {
                            "Cancelled after ${System.currentTimeMillis() - started}ms while '${trace.phase}'"
                        }
                        throw e
                    } catch (e: Exception) {
                        log(context, DebugLog.Level.WARN, "ai", trace) {
                            "${key.preset.label} ${key.model} failed after ${System.currentTimeMillis() - started}ms " +
                                "while '${trace.phase}': ${e::class.java.simpleName}: ${e.message}\n${stack(e)}"
                        }
                        if (!minimal && isTooLong(e)) {
                            // Small context window or token rate limit: same key, much smaller prompt.
                            minimal = true
                            prompt = AiPrompt.system(context, request, currentRaw, AiPrompt.Tier.MINIMAL)
                            log(context, DebugLog.Level.INFO, "ai", trace) {
                                "Prompt too long for ${key.preset.label}; retrying this key with the minimal prompt (${prompt.length} chars)"
                            }
                            continue
                        }
                        failures += AiFailure(key.label, e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "Failed")
                        break
                    }
                }
            }
            log(context, DebugLog.Level.ERROR, "ai", trace) {
                "Every key failed (${failures.size}) after ${System.currentTimeMillis() - begun}ms: " +
                    failures.joinToString(" | ") { "${it.keyLabel}: ${it.message}" }
            }
            throw AiException("Every key failed.", failures)
        }

    /**
     * One short, buildable widget idea for the "surprise me" button. Same key order and fall-through
     * as [generate]; throws [AiException] when no key could answer.
     */
    suspend fun idea(context: Context, avoid: String? = null): String = withContext(Dispatchers.IO) {
        val trace = Trace(newId())
        val keys = AiKeyStore(context).load().filter { it.usable }
        if (keys.isEmpty()) {
            log(context, DebugLog.Level.ERROR, "ai", trace) { "Idea aborted: no usable AI keys" }
            throw AiException("No AI keys are set up. Add one in Settings.", emptyList())
        }
        val system = "You suggest ideas for Android home screen widgets. Reply with ONE idea in a single sentence " +
            "of at most 16 words. Make it concrete and visual, something that can be built from text, dot grids, " +
            "progress bars, counters, clocks and device data such as battery, date and time. " +
            "No quotes, no preamble, no list."
        val user = "Give me one fresh widget idea." + (avoid?.let { " Not similar to: $it." } ?: "") +
            " (variation ${kotlin.random.Random.nextInt(1000)})"
        val failures = mutableListOf<AiFailure>()
        log(context, DebugLog.Level.INFO, "ai", trace) {
            "Idea start (avoiding: ${avoid ?: "nothing"}), ${keys.size} usable key(s) in order: " + keys.joinToString(" | ") { describe(it) }
        }
        for ((i, key) in keys.withIndex()) {
            trace.secret = key.apiKey.trim()
            trace.enter("starting")
            log(context, DebugLog.Level.INFO, "ai", trace) { "Idea attempt ${i + 1}/${keys.size}: ${describe(key)}" }
            val started = System.currentTimeMillis()
            try {
                // Plain sentence wanted, so never force the server into JSON mode.
                val text = withHeartbeat(context, trace, "ai") { call(context, trace, key.copy(jsonMode = false), system, user) }
                val idea = text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().trim('"', '\'', '`').take(140)
                if (idea.isNotBlank()) {
                    log(context, DebugLog.Level.INFO, "ai", trace) { "Idea answered in ${System.currentTimeMillis() - started}ms: $idea" }
                    return@withContext idea
                }
                log(context, DebugLog.Level.WARN, "ai", trace) { "Idea answer was blank; trying the next key" }
            } catch (e: CancellationException) {
                log(context, DebugLog.Level.WARN, "ai", trace) {
                    "Idea cancelled after ${System.currentTimeMillis() - started}ms while '${trace.phase}'"
                }
                throw e
            } catch (e: Exception) {
                log(context, DebugLog.Level.WARN, "ai", trace) {
                    "Idea failed after ${System.currentTimeMillis() - started}ms while '${trace.phase}': " +
                        "${e::class.java.simpleName}: ${e.message}\n${stack(e)}"
                }
                failures += AiFailure(key.label, e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "Failed")
            }
        }
        log(context, DebugLog.Level.ERROR, "ai", trace) { "Idea: every key failed (${failures.size})" }
        throw AiException("Couldn't get an idea.", failures)
    }

    private val TOO_LONG = Regex(
        "context.{0,12}(length|window)|maximum context|too long|too large|too many tokens|token limit|" +
            "exceeds?.{0,40}(token|limit|context)|reduce the length|(input|prompt).{0,20}(length|size)|" +
            "request_too_large|tokens per min",
        RegexOption.IGNORE_CASE,
    )

    /** True when a provider rejected the request because the prompt was too big for it. */
    private fun isTooLong(e: Exception): Boolean {
        val m = e.message ?: return false
        return m.startsWith("HTTP 413") || (m.startsWith("HTTP 4") && TOO_LONG.containsMatchIn(m))
    }

    // --- parsing -------------------------------------------------------------------------------

    private fun parse(context: Context, trace: Trace, text: String, forceId: String?, previousVersion: Int?): Pair<WidgetSpec, String> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            log(context, DebugLog.Level.ERROR, "ai", trace) { "Parse: no JSON object in the ${text.length}-char answer" }
            throw IOException("Answer had no JSON in it")
        }
        log(context, DebugLog.Level.DEBUG, "ai", trace) {
            "Parse: JSON spans chars $start..$end of ${text.length}" +
                (if (start > 0 || end < text.length - 1) " (text around it was dropped)" else "")
        }
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
        log(context, DebugLog.Level.DEBUG, "ai", trace) { "Parse: decoded, normalised to id=$id v${spec.version}; running full validation" }
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

    private fun call(context: Context, trace: Trace, key: AiKey, system: String, user: String): String {
        val base = key.baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || (key.preset.allowHttp && base.startsWith("http://"))) {
            if (key.preset.allowHttp) "Base URL must start with http:// or https://" else "Base URL must be https"
        }
        return when (key.preset.protocol) {
            AiProtocol.ANTHROPIC -> callAnthropic(context, trace, key, base, system, user)
            AiProtocol.OPENAI_COMPAT -> callOpenAiCompat(context, trace, key, base, system, user)
        }
    }

    private fun callOpenAiCompat(context: Context, trace: Trace, key: AiKey, base: String, system: String, user: String): String {
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
        val reply = post(context, trace, "$base/chat/completions", headers, body.toString())
        trace.enter("extracting the answer")
        val root = SpecCodec.json.parseToJsonElement(reply).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val content = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        log(context, DebugLog.Level.INFO, "ai-http", trace) {
            "Answer: finish_reason=${choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull ?: "-"} " +
                "usage=${root["usage"] ?: "-"} text=${content?.length ?: 0} chars"
        }
        if (content.isNullOrBlank()) {
            log(context, DebugLog.Level.ERROR, "ai-http", trace) { "Answer text was empty or missing in the response" }
            throw IOException("Empty answer")
        }
        return content
    }

    private fun callAnthropic(context: Context, trace: Trace, key: AiKey, base: String, system: String, user: String): String {
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
        val reply = post(context, trace, "$base/v1/messages", headers, body.toString())
        trace.enter("extracting the answer")
        val root = SpecCodec.json.parseToJsonElement(reply).jsonObject
        val text = root["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject.takeIf { b -> b["type"]?.jsonPrimitive?.contentOrNull == "text" }?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
        log(context, DebugLog.Level.INFO, "ai-http", trace) {
            "Answer: stop_reason=${root["stop_reason"]?.jsonPrimitive?.contentOrNull ?: "-"} " +
                "usage=${root["usage"] ?: "-"} text=${text?.length ?: 0} chars"
        }
        if (text.isNullOrBlank()) {
            log(context, DebugLog.Level.ERROR, "ai-http", trace) { "Answer text was empty or missing in the response" }
            throw IOException("Empty answer")
        }
        return text
    }

    private fun post(context: Context, trace: Trace, url: String, headers: Map<String, String>, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        val t0 = System.currentTimeMillis()
        fun at() = System.currentTimeMillis() - t0
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000 // models can be slow, local ones especially
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            log(context, DebugLog.Level.INFO, "ai-http", trace) {
                "POST $url (connect timeout ${conn.connectTimeout}ms, read timeout ${conn.readTimeout}ms) " +
                    "headers {${headers.entries.joinToString(", ") { (k, v) -> "$k: ${maskHeader(k, v)}" }}} " +
                    "body ${body.length} chars: ${clip(body)}"
            }
            trace.enter("connecting and sending the request")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            log(context, DebugLog.Level.DEBUG, "ai-http", trace) { "Request sent ${at()}ms after starting" }

            trace.enter("waiting for the server to answer")
            val code = conn.responseCode
            log(context, DebugLog.Level.INFO, "ai-http", trace) {
                "HTTP $code ${conn.responseMessage.orEmpty()} after ${at()}ms; headers {" +
                    conn.headerFields.entries
                        .filter { it.key != null && !it.key.equals("set-cookie", ignoreCase = true) }
                        .joinToString("; ") { "${it.key}: ${it.value.joinToString(", ")}" } + "}"
            }

            trace.enter("reading the response body")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val full = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val text = full.take(MAX_RESPONSE_CHARS)
            log(context, if (code in 200..299) DebugLog.Level.INFO else DebugLog.Level.ERROR, "ai-http", trace) {
                "Body ${full.length} chars" + (if (full.length > text.length) " (cut to ${text.length})" else "") +
                    " read, ${at()}ms total: ${clip(text)}"
            }
            if (code !in 200..299) throw IOException("HTTP $code${errorDetail(text)?.let { ": $it" } ?: ""}")
            return text
        } catch (e: Exception) {
            if (e !is CancellationException) {
                log(context, DebugLog.Level.ERROR, "ai-http", trace) {
                    "Request failed after ${at()}ms while '${trace.phase}': ${e::class.java.simpleName}: ${e.message}"
                }
            }
            throw e
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

    // --- logging -------------------------------------------------------------------------------

    /** Ties together every log line of one request and tracks what it is doing, so a stall shows where. */
    private class Trace(val id: String) {
        val startedAt = System.currentTimeMillis()
        @Volatile var secret: String = ""
        @Volatile var phase: String = "starting"
        @Volatile var phaseSince: Long = startedAt

        fun enter(p: String) {
            phase = p
            phaseSince = System.currentTimeMillis()
        }

        /** Never let the key itself reach the log, even if a server echoes it back in an error. */
        fun scrub(text: String): String = if (secret.length >= 6) text.replace(secret, "<key>") else text
    }

    private fun newId(): String = kotlin.random.Random.nextInt(0x1000, 0x10000).toString(16)

    /** Builds the message only when logging is on, so large prompts and answers cost nothing otherwise. */
    private inline fun log(context: Context, level: DebugLog.Level, tag: String, trace: Trace, message: () -> String) {
        if (!DebugLog.enabled(context)) return
        DebugLog.log(context, level, tag, "[${trace.id}] ${trace.scrub(message())}")
    }

    /**
     * Runs a blocking call while writing a line every 10 seconds saying what it is waiting on, so a
     * request that never comes back still leaves a trail (connecting, waiting for the server, reading).
     */
    private suspend fun <T> withHeartbeat(context: Context, trace: Trace, tag: String, block: () -> T): T = coroutineScope {
        val beat = launch {
            while (true) {
                delay(10_000)
                log(context, DebugLog.Level.WARN, tag, trace) {
                    val now = System.currentTimeMillis()
                    "Still waiting: ${trace.phase} for ${(now - trace.phaseSince) / 1000}s (${(now - trace.startedAt) / 1000}s since the request began)"
                }
            }
        }
        try {
            block()
        } finally {
            beat.cancel()
        }
    }

    private fun describe(key: AiKey): String =
        "\"${key.label}\" ${key.preset.label}/${key.model.ifBlank { "default model" }} ${key.baseUrl.trim()} " +
            "protocol=${key.preset.protocol} jsonMode=${key.jsonMode}"

    private fun maskHeader(name: String, value: String): String =
        if (name.equals("Authorization", true) || name.equals("x-api-key", true) || name.contains("key", true))
            "<hidden, ${value.length} chars>"
        else value

    /** Keeps one huge answer from pushing everything else out of the log file. */
    private fun clip(text: String, max: Int = 60_000): String =
        if (text.length <= max) text else text.take(max) + "...[${text.length - max} more chars not logged]"

    private fun stack(t: Throwable): String =
        StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString().lineSequence().take(14).joinToString("\n")
}
