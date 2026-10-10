package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.newUiChecker
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider
import java.util.concurrent.ConcurrentHashMap

// One provider per icon: each caches its painters by hints and density.
private val sharedProviders = ConcurrentHashMap<Pair<String, ClassLoader>, ResourcePainterProvider>()

/**
 * Jewel's [Icon] from an [IconKey], with its painter provider shared across compositions. Jewel's own creates one
 * per composition, which sets up an XML parser and parses the SVG again: in rows composed over and over (the
 * category tree, the tabs), that was most of the row's cost.
 */
@Composable
fun SharedIcon(
    key: IconKey,
    contentDescription: String?,
    vararg hints: PainterHint,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val path = key.path(JewelTheme.newUiChecker.isNewUi())
    val classLoader = key.iconClass.classLoader
    val provider = sharedProviders.getOrPut(path to classLoader) { ResourcePainterProvider(path, classLoader) }
    val painter by provider.getPainter(*hints)
    Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
}
