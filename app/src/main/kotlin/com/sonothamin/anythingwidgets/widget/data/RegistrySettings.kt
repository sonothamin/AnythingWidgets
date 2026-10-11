package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import com.sonothamin.anythingwidgets.BuildConfig
import java.net.URI

/** Which widget repository the app talks to: the official sonothamin.dev one, or a user-supplied HTTPS URL. */
object RegistrySettings {
    enum class Source { OFFICIAL, CUSTOM }

    val OFFICIAL_URL: String = BuildConfig.REGISTRY_URL

    private const val PREFS = "settings"
    private const val KEY_SOURCE = "registry_source"
    private const val KEY_CUSTOM = "registry_custom_url"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun source(c: Context): Source =
        if (prefs(c).getString(KEY_SOURCE, null) == Source.CUSTOM.name && customUrl(c) != null) Source.CUSTOM
        else Source.OFFICIAL

    fun customUrl(c: Context): String? = prefs(c).getString(KEY_CUSTOM, null)

    fun baseUrl(c: Context): String =
        if (source(c) == Source.CUSTOM) customUrl(c) ?: OFFICIAL_URL else OFFICIAL_URL

    fun useOfficial(c: Context) {
        prefs(c).edit().putString(KEY_SOURCE, Source.OFFICIAL.name).apply()
        Registry.clearCache(c)
    }

    /** [url] must come from [normalize]. */
    fun useCustom(c: Context, url: String) {
        prefs(c).edit().putString(KEY_CUSTOM, url).putString(KEY_SOURCE, Source.CUSTOM.name).apply()
        Registry.clearCache(c)
    }

    /**
     * Returns a canonical base URL (https, host present, no credentials/query/fragment, trailing "/"),
     * or null if the input isn't acceptable. A pasted ".../index.json" is trimmed to its folder.
     */
    fun normalize(input: String): String? {
        var text = input.trim()
        if (text.isEmpty() || text.length > 200) return null
        if (text.endsWith("index.json")) text = text.removeSuffix("index.json")
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        val path = (uri.rawPath ?: "").let { if (it.endsWith("/")) it else "$it/" }
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "https://${uri.host.lowercase()}$port$path"
    }
}
