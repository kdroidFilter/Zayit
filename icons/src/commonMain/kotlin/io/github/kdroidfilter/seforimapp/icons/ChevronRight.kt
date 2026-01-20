package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ChevronRight: ImageVector
    get() {
        if (_ChevronRight != null) return _ChevronRight!!

        _ChevronRight =
            ImageVector
                .Builder(
                    name = "ChevronRight",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                    autoMirror = true,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(10.072f, 8.024f)
                        lineTo(5.715f, 3.667f)
                        lineToRelative(0.618f, -0.62f)
                        lineTo(11f, 7.716f)
                        verticalLineToRelative(0.618f)
                        lineTo(6.333f, 13f)
                        lineToRelative(-0.618f, -0.619f)
                        lineToRelative(4.357f, -4.357f)
                        close()
                    }
                }.build()

        return _ChevronRight!!
    }

private var _ChevronRight: ImageVector? = null
