package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ChevronDown: ImageVector
    get() {
        if (_ChevronDown != null) return _ChevronDown!!

        _ChevronDown =
            ImageVector
                .Builder(
                    name = "ChevronDown",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(7.976f, 10.072f)
                        lineToRelative(4.357f, -4.357f)
                        lineToRelative(0.62f, 0.618f)
                        lineTo(8.284f, 11f)
                        horizontalLineToRelative(-0.618f)
                        lineTo(3f, 6.333f)
                        lineToRelative(0.619f, -0.618f)
                        lineToRelative(4.357f, 4.357f)
                        close()
                    }
                }.build()

        return _ChevronDown!!
    }

private var _ChevronDown: ImageVector? = null
