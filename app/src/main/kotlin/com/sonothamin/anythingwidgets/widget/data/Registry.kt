package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import com.sonothamin.anythingwidgets.widget.spec.IndexEntry
import com.sonothamin.anythingwidgets.widget.spec.RegistryIndex
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Talks to the central registry. Only data is downloaded, never code. */
class Registry(
    private val context: Context,
    val baseUrl: String = RegistrySettings.baseUrl(context),
) {
    suspend fun fetchIndex(): RegistryIndex = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, CACHE_INDEX)
        val etagFile = File(context.cacheDir, CACHE_ETAG)
        val etag = if (cache.exists() && etagFile.exists()) etagFile.readText() else null
        val res = Http.get(baseUrl + "index.json", etag)
        val raw = if (res.code == 304) {
            cache.readText()
        } else {
            val text = res.bytes.decodeToString()
            SpecCodec.parseIndex(text) // validate before caching
            cache.writeText(text)
            res.etag?.let { etagFile.writeText(it) } ?: etagFile.delete()
            text
        }
        SpecCodec.parseIndex(raw)
    }

    /** Download, verify checksum, validate, then store. */
    suspend fun install(entry: IndexEntry): WidgetSpec = withContext(Dispatchers.IO) {
        val bytes = Http.get(baseUrl + entry.spec).bytes
        check(sha256(bytes).equals(entry.sha256, ignoreCase = true)) { "Checksum mismatch" }
        val raw = bytes.decodeToString()
        val spec = SpecCodec.parseSpec(raw)
        check(spec.id == entry.id && spec.version == entry.version) { "Spec does not match index" }
        SpecStore(context).save(spec, raw, baseUrl)
        spec
    }

    /** Upgrades installed widgets whose registry version is newer. */
    suspend fun updateInstalled(store: SpecStore) {
        val index = fetchIndex()
        for (local in store.installed()) {
            // Only auto-update from the repository the widget was installed from.
            if (store.sourceOf(local.id) != baseUrl) continue
            val remote = index.widgets.firstOrNull { it.id == local.id } ?: continue
            if (remote.version > local.version) {
                try {
                    install(remote)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep the working local copy.
                }
            }
        }
    }

    companion object {
        private const val CACHE_INDEX = "registry_index.json"
        private const val CACHE_ETAG = "registry_index.etag"

        fun clearCache(context: Context) {
            File(context.cacheDir, CACHE_INDEX).delete()
            File(context.cacheDir, CACHE_ETAG).delete()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
