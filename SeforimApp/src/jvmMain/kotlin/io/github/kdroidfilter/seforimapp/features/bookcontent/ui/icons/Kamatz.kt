package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Kamatz: ImageVector
    get() {
        if (_Kamatz != null) return _Kamatz!!
        _Kamatz = ImageVector.Builder(
            name = "Kamatz",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                // Top Bar (Thick)
                moveTo(1f, 3f)
                lineTo(15f, 3f)
                lineTo(15f, 7f)
                lineTo(1f, 7f)
                close()

                // Neck
                moveTo(7f, 6.5f)
                lineTo(9f, 6.5f)
                lineTo(9f, 10f)
                lineTo(7f, 10f)
                close()

                // Bulb (Circle approximation)
                // Center (8, 12.5), Radius 3.5
                moveTo(8f, 9f)
                curveTo(9.93f, 9f, 11.5f, 10.57f, 11.5f, 12.5f)
                curveTo(11.5f, 14.43f, 9.93f, 16f, 8f, 16f)
                curveTo(6.07f, 16f, 4.5f, 14.43f, 4.5f, 12.5f)
                curveTo(4.5f, 10.57f, 6.07f, 9f, 8f, 9f)
                close()
            }
        }.build()
        return _Kamatz!!
    }

private var _Kamatz: ImageVector? = null
