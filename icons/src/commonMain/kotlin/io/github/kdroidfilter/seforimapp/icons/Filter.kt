package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Filter: ImageVector
    get() {
        if (_Filter != null) return _Filter!!

        _Filter =
            ImageVector
                .Builder(
                    name = "Filter",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(15f, 2f)
                        verticalLineToRelative(1.67f)
                        lineToRelative(-5f, 4.759f)
                        verticalLineTo(14f)
                        horizontalLineTo(6f)
                        verticalLineTo(8.429f)
                        lineToRelative(-5f, -4.76f)
                        verticalLineTo(2f)
                        horizontalLineToRelative(14f)
                        close()
                        moveTo(7f, 8f)
                        verticalLineToRelative(5f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(8f)
                        lineToRelative(5f, -4.76f)
                        verticalLineTo(3f)
                        horizontalLineTo(2f)
                        verticalLineToRelative(0.24f)
                        lineTo(7f, 8f)
                        close()
                    }
                }.build()

        return _Filter!!
    }

private var _Filter: ImageVector? = null
