package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FileArrowDown: ImageVector
    get() {
        if (_FileArrowDown != null) return _FileArrowDown!!

        _FileArrowDown =
            ImageVector
                .Builder(
                    name = "FileArrowDown",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(8f, 5f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0.5f, 0.5f)
                        verticalLineToRelative(3.793f)
                        lineToRelative(1.146f, -1.147f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0.708f, 0.708f)
                        lineToRelative(-2f, 2f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, -0.708f, 0f)
                        lineToRelative(-2f, -2f)
                        arcToRelative(0.5f, 0.5f, 0f, true, true, 0.708f, -0.708f)
                        lineTo(7.5f, 9.293f)
                        verticalLineTo(5.5f)
                        arcTo(0.5f, 0.5f, 0f, false, true, 8f, 5f)
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(4f, 0f)
                        arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                        verticalLineToRelative(12f)
                        arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                        horizontalLineToRelative(8f)
                        arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                        verticalLineTo(2f)
                        arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                        close()
                        moveToRelative(0f, 1f)
                        horizontalLineToRelative(8f)
                        arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                        verticalLineToRelative(12f)
                        arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                        horizontalLineTo(4f)
                        arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                        verticalLineTo(2f)
                        arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                    }
                }.build()

        return _FileArrowDown!!
    }

private var _FileArrowDown: ImageVector? = null
