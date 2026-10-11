package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import android.net.Uri
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Imports widgets from a zip laid out like a registry:
 *
 *     index.json
 *     widgets/<id>.json
 *
 * (a single wrapper folder, e.g. what "zip this folder" produces, is fine). Checksums in index.json are
 * ignored: the user picked the file themselves, which is the trust decision. Every spec still goes
 * through the full validator. Imported widgets are never auto-updated from a registry.
 */
object WidgetImporter {
    const val SOURCE = "import"

    private const val MAX_ENTRIES = 300
    private const val MAX_ENTRY_BYTES = 256 * 1024
    private const val MAX_TOTAL_BYTES = 8 * 1024 * 1024

    class Result(val imported: List<WidgetSpec>, val skipped: List<Pair<String, String>>)

    @Serializable
    private data class LooseIndex(val widgets: List<LooseEntry> = emptyList())

    @Serializable
    private data class LooseEntry(val spec: String)

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val files = readZip(context, uri)

        val indexPath = files.keys.filter { it == "index.json" || (it.endsWith("/index.json") && it.count { c -> c == '/' } == 1) }
            .minByOrNull { it.length }
        val prefix = when {
            indexPath != null -> indexPath.removeSuffix("index.json")
            else -> files.keys.firstOrNull { "widgets/" in it }?.substringBefore("widgets/") ?: ""
        }

        val specPaths: List<String> = if (indexPath != null) {
            val index = runCatching {
                SpecCodec.json.decodeFromString<LooseIndex>(files.getValue(indexPath).decodeToString())
            }.getOrElse { throw IllegalArgumentException("index.json is not valid JSON") }
            index.widgets.map { prefix + it.spec }
        } else {
            files.keys.filter { it.startsWith(prefix + "widgets/") && it.removePrefix(prefix + "widgets/").count { c -> c == '/' } == 0 }
        }
        require(specPaths.isNotEmpty()) { "No widgets found in the zip" }

        val store = SpecStore(context)
        val imported = mutableListOf<WidgetSpec>()
        val skipped = mutableListOf<Pair<String, String>>()
        for (path in specPaths.distinct()) {
            val name = path.removePrefix(prefix)
            val bytes = files[path]
            if (bytes == null) {
                skipped += name to "File not found in zip"
                continue
            }
            try {
                val raw = bytes.decodeToString()
                val spec = SpecCodec.parseSpec(raw)
                store.save(spec, raw, SOURCE)
                imported += spec
            } catch (e: Exception) {
                skipped += name to (e.message?.lineSequence()?.firstOrNull()?.take(100) ?: "Invalid spec")
            }
        }
        require(imported.isNotEmpty() || skipped.isNotEmpty()) { "No widgets found in the zip" }
        Result(imported, skipped)
    }

    /** Reads only .json entries into memory, with hard caps and path-traversal rejection. */
    private fun readZip(context: Context, uri: Uri): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var entries = 0
        var total = 0
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Can't open file")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IllegalArgumentException("Zip has too many files")
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                    throw IllegalArgumentException("Unsafe path in zip")
                }
                val ignored = entry.isDirectory || !name.endsWith(".json") ||
                    name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")
                if (ignored) continue

                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var size = 0
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    size += n
                    total += n
                    if (size > MAX_ENTRY_BYTES) throw IllegalArgumentException("'$name' is too large")
                    if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("Zip is too large")
                    buf.write(chunk, 0, n)
                }
                out[name] = buf.toByteArray()
            }
        }
        return out
    }
}
