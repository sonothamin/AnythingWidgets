package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import com.sonothamin.anythingwidgets.debug.DebugLog
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
        val res = Http.get(baseUrl + "index.json", etag, allowHttp = RegistrySettings.allowHttp(context))
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

    /** A verified, validated spec that has been downloaded but not necessarily installed. */
    class Fetched(val spec: WidgetSpec, val raw: String, val sizeBytes: Int)

    private fun download(entry: IndexEntry): Fetched {
        val url = baseUrl + entry.spec
        val bytes = try {
            Http.get(url, allowHttp = RegistrySettings.allowHttp(context)).bytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Download failed for $url (${e.message ?: e::class.java.simpleName})", e)
        }
        val actual = sha256(bytes)
        check(actual.equals(entry.sha256, ignoreCase = true)) {
            "Checksum mismatch for ${entry.id}: index says ${entry.sha256.take(12)}, file is ${actual.take(12)} (${bytes.size} bytes). The hosted index and spec are out of sync."
        }
        val raw = bytes.decodeToString()
        val spec = try {
            SpecCodec.parseSpec(raw)
        } catch (e: Exception) {
            throw IllegalStateException("${entry.id} v${entry.version} failed validation: ${e.message ?: e::class.java.simpleName}", e)
        }
        check(spec.id == entry.id && spec.version == entry.version) {
            "Index lists ${entry.id} v${entry.version} but the file is ${spec.id} v${spec.version}"
        }
        return Fetched(spec, raw, bytes.size)
    }

    /** Download and verify without installing (used to preview a widget before adding it). */
    suspend fun fetchSpec(entry: IndexEntry): Fetched = withContext(Dispatchers.IO) { download(entry) }

    /** Download, verify checksum, validate, then store. */
    suspend fun install(entry: IndexEntry): WidgetSpec = withContext(Dispatchers.IO) {
        val fetched = try {
            download(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (first: Exception) {
            // The index we hold can be older than the hosted files (cached copy, or a host that is still
            // updating). Reload it without the cache and try the current entry once before giving up.
            DebugLog.w(context, "install", "${entry.id} v${entry.version}: ${first.message}. Reloading the index and retrying once")
            val fresh = try {
                clearCache(context)
                fetchIndex().widgets.firstOrNull { it.id == entry.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w(context, "install", "Could not reload the index: ${e.message}")
                null
            }
            if (fresh == null) {
                DebugLog.e(context, "install", "${entry.id} install failed", first)
                throw first
            }
            try {
                download(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (second: Exception) {
                DebugLog.e(context, "install", "${entry.id} install failed after retry: ${second.message}", second)
                throw second
            }
        }
        try {
            SpecStore(context).save(fetched.spec, fetched.raw, baseUrl)
        } catch (e: Exception) {
            DebugLog.e(context, "install", "${entry.id} downloaded fine but could not be saved: ${e.message}", e)
            throw e
        }
        DebugLog.i(context, "install", "${fetched.spec.id} v${fetched.spec.version} installed (${fetched.sizeBytes} bytes)")
        fetched.spec
    }

    /** Upgrades installed widgets whose registry version is newer. */
    suspend fun updateInstalled(store: SpecStore) {
        val index = fetchIndex()
        for (local in store.installed()) {
            // Only auto-update from the repository the widget was installed from.
            if (store.sourceOf(local.id) != baseUrl) continue
            if (!WidgetPrefs.autoUpdate(context, local.id)) continue
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
