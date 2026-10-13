package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import com.sonothamin.anythingwidgets.BuildConfig
import java.net.URI

/**
 * Which widget repository the app talks to: the official sonothamin.dev one, or a user-supplied URL.
 * Custom URLs must be https unless the user has switched on "Allow HTTP sources" in Advanced settings.
 */
object RegistrySettings {
    enum class Source { OFFICIAL, CUSTOM }

    val OFFICIAL_URL: String = BuildConfig.REGISTRY_URL

    private const val PREFS = "settings"
    private const val KEY_SOURCE = "registry_source"
    private const val KEY_CUSTOM = "registry_custom_url"
    private const val KEY_ALLOW_HTTP = "registry_allow_http"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether plain http:// sources are allowed. Off by default. */
    fun allowHttp(c: Context): Boolean = prefs(c).getBoolean(KEY_ALLOW_HTTP, false)

    /**
     * Turning this off also drops a saved http:// custom source and goes back to the official one,
     * so nothing keeps talking over an unencrypted link after the user said no.
     */
    fun setAllowHttp(c: Context, allowed: Boolean) {
        val edit = prefs(c).edit().putBoolean(KEY_ALLOW_HTTP, allowed)
        if (!allowed && customUrl(c)?.startsWith("http://", ignoreCase = true) == true) {
            edit.remove(KEY_CUSTOM).putString(KEY_SOURCE, Source.OFFICIAL.name)
        }
        edit.apply()
        Registry.clearCache(c)
    }

    /** The saved custom URL, if it is allowed under the current settings. */
    private fun usableCustom(c: Context): String? =
        customUrl(c)?.takeIf { it.startsWith("https://", ignoreCase = true) || allowHttp(c) }

    fun source(c: Context): Source =
        if (prefs(c).getString(KEY_SOURCE, null) == Source.CUSTOM.name && usableCustom(c) != null) Source.CUSTOM
        else Source.OFFICIAL

    fun customUrl(c: Context): String? = prefs(c).getString(KEY_CUSTOM, null)

    fun baseUrl(c: Context): String =
        if (source(c) == Source.CUSTOM) usableCustom(c) ?: OFFICIAL_URL else OFFICIAL_URL

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
     * Returns a canonical base URL (https, or http when [allowHttp]; host present, no
     * credentials/query/fragment, trailing "/"), or null if the input isn't acceptable.
     * A pasted ".../index.json" is trimmed to its folder.
     */
    fun normalize(input: String, allowHttp: Boolean = false): String? {
        var text = input.trim()
        if (text.isEmpty() || text.length > 200) return null
        if (text.endsWith("index.json")) text = text.removeSuffix("index.json")
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && !(allowHttp && scheme == "http")) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        val path = (uri.rawPath ?: "").let { if (it.endsWith("/")) it else "$it/" }
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "$scheme://${uri.host.lowercase()}$port$path"
    }
}
