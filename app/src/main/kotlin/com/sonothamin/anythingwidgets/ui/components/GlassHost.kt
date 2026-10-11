package com.sonothamin.anythingwidgets.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Records [content] into a [GraphicsLayer] while still drawing it normally, so overlays
 * (like [GlassNavBar]) can draw a blurred copy of what is behind them. No third-party library.
 * The host must fill the window so overlay positions line up with the recorded layer.
 */
@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(backdrop: GraphicsLayer) -> Unit,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val scope: ContentDrawScope = this
                    layer.record { scope.drawContent() }
                    drawLayer(layer)
                },
        ) { content() }
        overlay(layer)
    }
}
