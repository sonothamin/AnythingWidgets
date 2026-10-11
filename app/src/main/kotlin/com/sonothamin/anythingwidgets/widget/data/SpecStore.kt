package com.sonothamin.anythingwidgets.widget.data

import android.content.Context
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.SpecValidator
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import java.io.File

/** Installed specs live in app-private storage. Everything read back is re-validated. */
class SpecStore(context: Context) {
    private val dir = File(context.filesDir, "widget_specs").apply { mkdirs() }

    fun save(spec: WidgetSpec, raw: String) {
        require(SpecValidator.ID.matches(spec.id))
        val tmp = File(dir, "${spec.id}.json.tmp")
        tmp.writeText(raw)
        check(tmp.renameTo(File(dir, "${spec.id}.json"))) { "Could not save spec" }
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
        if (SpecValidator.ID.matches(id)) File(dir, "$id.json").delete()
    }
}
