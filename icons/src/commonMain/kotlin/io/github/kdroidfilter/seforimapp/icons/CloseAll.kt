package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CloseAll: ImageVector
    get() {
        if (_CloseAll != null) return _CloseAll!!

        _CloseAll =
            ImageVector
                .Builder(
                    name = "CloseAll",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(8.621f, 8.086f)
                        lineToRelative(-0.707f, -0.707f)
                        lineTo(6.5f, 8.793f)
                        lineTo(5.086f, 7.379f)
                        lineToRelative(-0.707f, 0.707f)
                        lineTo(5.793f, 9.5f)
                        lineToRelative(-1.414f, 1.414f)
                        lineToRelative(0.707f, 0.707f)
                        lineTo(6.5f, 10.207f)
                        lineToRelative(1.414f, 1.414f)
                        lineToRelative(0.707f, -0.707f)
                        lineTo(7.207f, 9.5f)
                        lineToRelative(1.414f, -1.414f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(5f, 3f)
                        lineToRelative(1f, -1f)
                        horizontalLineToRelative(7f)
                        lineToRelative(1f, 1f)
                        verticalLineToRelative(7f)
                        lineToRelative(-1f, 1f)
                        horizontalLineToRelative(-2f)
                        verticalLineToRelative(2f)
                        lineToRelative(-1f, 1f)
                        horizontalLineTo(3f)
                        lineToRelative(-1f, -1f)
                        verticalLineTo(6f)
                        lineToRelative(1f, -1f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(3f)
                        close()
                        moveToRelative(1f, 2f)
                        horizontalLineToRelative(4f)
                        lineToRelative(1f, 1f)
                        verticalLineToRelative(4f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(3f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(2f)
                        close()
                        moveToRelative(4f, 1f)
                        horizontalLineTo(3f)
                        verticalLineToRelative(7f)
                        horizontalLineToRelative(7f)
                        verticalLineTo(6f)
                        close()
                    }
                }.build()

        return _CloseAll!!
    }

private var _CloseAll: ImageVector? = null
