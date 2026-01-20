package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Slightly thinner Kamatz-style diacritic icon.
 * Same overall shape, reduced visual weight.
 */
val TextDiacritics: ImageVector
    get() {
        if (_TextDiacritics != null) return _TextDiacritics!!

        _TextDiacritics =
            ImageVector
                .Builder(
                    name = "TextDiacritics",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        // Top Bar (slightly thinner)
                        moveTo(1f, 3.5f)
                        lineTo(15f, 3.5f)
                        lineTo(15f, 6.5f)
                        lineTo(1f, 6.5f)
                        close()

                        // Neck (slightly narrower)
                        moveTo(7.25f, 6.4f)
                        lineTo(8.75f, 6.4f)
                        lineTo(8.75f, 9.8f)
                        lineTo(7.25f, 9.8f)
                        close()

                        // Bulb (very slightly reduced radius)
                        // Center (8, 12.4), Radius ~3.3
                        moveTo(8f, 9.2f)
                        curveTo(9.8f, 9.2f, 11.3f, 10.6f, 11.3f, 12.4f)
                        curveTo(11.3f, 14.2f, 9.8f, 15.6f, 8f, 15.6f)
                        curveTo(6.2f, 15.6f, 4.7f, 14.2f, 4.7f, 12.4f)
                        curveTo(4.7f, 10.6f, 6.2f, 9.2f, 8f, 9.2f)
                        close()
                    }
                }.build()

        return _TextDiacritics!!
    }

private var _TextDiacritics: ImageVector? = null
