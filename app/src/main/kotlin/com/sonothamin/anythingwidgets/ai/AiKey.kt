package com.sonothamin.anythingwidgets.ai

import kotlinx.serialization.Serializable

/** Wire protocol a provider speaks. Everything except Claude is OpenAI-compatible. */
enum class AiProtocol { OPENAI_COMPAT, ANTHROPIC }

/** Presets just prefill the form; any of them can be edited afterwards. */
enum class AiPreset(
    val label: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    val model: String,
    /** Local servers (Ollama) often run without a key. */
    val keyOptional: Boolean = false,
    /** Hosted providers must use https; self-hosted ones may be plain http on a LAN. */
    val allowHttp: Boolean = false,
    val keyHint: String = "API key",
) {
    OPENAI("OpenAI", AiProtocol.OPENAI_COMPAT, "https://api.openai.com/v1", "gpt-4.1-mini", keyHint = "sk-..."),
    GEMINI(
        "Gemini", AiProtocol.OPENAI_COMPAT,
        "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", keyHint = "AIza...",
    ),
    CLAUDE("Claude", AiProtocol.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-5-5", keyHint = "sk-ant-..."),
    OLLAMA(
        "Ollama", AiProtocol.OPENAI_COMPAT, "http://192.168.1.10:11434/v1", "llama3.1",
        keyOptional = true, allowHttp = true, keyHint = "Optional",
    ),
    CUSTOM(
        "Custom", AiProtocol.OPENAI_COMPAT, "https://", "",
        keyOptional = true, allowHttp = true, keyHint = "Optional",
    ),
}

/** One saved API key. The list order in [AiKeyStore] is the priority order. */
@Serializable
data class AiKey(
    val id: String,
    val label: String,
    val preset: AiPreset,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val enabled: Boolean = true,
    /**
     * OpenAI-compatible only: ask for `response_format: json_object`. Turn off for servers that reject it.
     */
    val jsonMode: Boolean = true,
) {
    val usable: Boolean get() = enabled && model.isNotBlank() && (apiKey.isNotBlank() || preset.keyOptional)
}
