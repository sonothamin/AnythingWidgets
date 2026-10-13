package com.sonothamin.anythingwidgets.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sonothamin.anythingwidgets.ai.AiResult

/** One generated iteration and the request that produced it. */
class AiVersion(val result: AiResult, val request: String)

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

    fun add(version: AiVersion) {
        versions.add(version)
        index = versions.lastIndex
    }

    fun clear() {
        versions.clear()
        index = -1
    }
}
