package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.SpecValidator
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import java.io.File

/** Installed specs live in app-private storage. Everything read back is re-validated. */
class SpecStore(context: Context) {
    private val dir = File(context.filesDir, "widget_specs").apply { mkdirs() }

    fun save(spec: WidgetSpec, raw: String, source: String) {
        require(SpecValidator.ID.matches(spec.id))
        val tmp = File(dir, "${spec.id}.json.tmp")
        tmp.writeText(raw)
        check(tmp.renameTo(File(dir, "${spec.id}.json"))) { "Could not save spec" }
        File(dir, "${spec.id}.src").writeText(source)
        // Remember when it was first added; updates keep the original date.
        val added = File(dir, "${spec.id}.added")
        if (!added.exists()) added.writeText(System.currentTimeMillis().toString())
    }

    /** Epoch millis when the widget was first added (falls back to the file's age for older installs). */
    fun addedAt(id: String): Long? {
        if (!SpecValidator.ID.matches(id)) return null
        File(dir, "$id.added").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()?.let { return it }
        return File(dir, "$id.json").takeIf { it.exists() }?.lastModified()
    }

    /** Size of the stored spec file in bytes (0 if not installed). */
    fun sizeOf(id: String): Long {
        if (!SpecValidator.ID.matches(id)) return 0L
        return File(dir, "$id.json").takeIf { it.exists() }?.length() ?: 0L
    }

    /** Base URL of the registry this widget was installed from (null if unknown). */
    fun sourceOf(id: String): String? {
        if (!SpecValidator.ID.matches(id)) return null
        val f = File(dir, "$id.src")
        return if (f.exists()) f.readText() else null
    }

    fun load(id: String): WidgetSpec? {
        if (!SpecValidator.ID.matches(id)) return null
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return runCatching { SpecCodec.parseSpec(file.readText()) }.getOrNull()
    }

    fun installed(): List<WidgetSpec> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { load(it.name.removeSuffix(".json")) }
            .sortedBy { it.name.lowercase() }

    fun delete(id: String) {
        if (SpecValidator.ID.matches(id)) {
            File(dir, "$id.json").delete()
            File(dir, "$id.src").delete()
            File(dir, "$id.added").delete()
        }
    }
}
