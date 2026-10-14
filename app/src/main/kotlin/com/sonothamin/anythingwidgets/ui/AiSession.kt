package com.sonothamin.anythingwidgets.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sonothamin.anythingwidgets.ai.AiResult
import com.sonothamin.anythingwidgets.widget.spec.SpecCodec
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.serialization.encodeToString

/**
 * One iteration and the request that produced it. A [base] version is not generated: it is the
 * installed widget a refactor starts from, shown for comparison and never installed.
 */
class AiVersion(val result: AiResult, val request: String, val base: Boolean = false)

/**
 * What the AI tab is working on. Lives for the process rather than the screen, so switching tabs
 * doesn't throw away an iteration the person hasn't installed yet.
 */
object AiSession {
    val versions = mutableStateListOf<AiVersion>()

    /** Which version is showing (and will be refined or installed). -1 when empty. */
    var index by mutableIntStateOf(-1)

    var prompt by mutableStateOf("")

    val current: AiVersion? get() = versions.getOrNull(index)

    /** True when this session started from an installed widget rather than from a description. */
    val isRefactor: Boolean get() = versions.firstOrNull()?.base == true

    /** The id of the installed widget being refactored, or null. */
    val refactorId: String? get() = versions.firstOrNull()?.takeIf { it.base }?.result?.spec?.id

    /** True when there are iterations the person hasn't installed or discarded yet. */
    val hasDraft: Boolean get() = versions.any { !it.base }

    fun add(version: AiVersion) {
        versions.add(version)
        index = versions.lastIndex
    }

    /**
     * Starts over from an installed widget: it becomes the "Current" version, and every change the
     * person asks for is made on top of it. Installing a result keeps the id and bumps the version.
     */
    fun startRefactor(spec: WidgetSpec) {
        clear()
        val raw = SpecCodec.json.encodeToString(spec)
        add(
            AiVersion(
                AiResult(spec, raw, usedLabel = "", provider = "Installed", model = "", millis = 0, failures = emptyList()),
                request = "",
                base = true,
            ),
        )
        prompt = ""
    }

    /** "Current" for the installed base, otherwise v1, v2... counting only generated versions. */
    fun label(i: Int): String {
        if (versions.getOrNull(i)?.base == true) return "Current"
        return "v${if (isRefactor) i else i + 1}"
    }

    fun clear() {
        versions.clear()
        index = -1
    }
}
