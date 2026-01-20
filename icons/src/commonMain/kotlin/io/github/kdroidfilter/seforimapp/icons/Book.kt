package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Book: ImageVector
    get() {
        if (_Book != null) return _Book!!

        _Book =
            ImageVector
                .Builder(
                    name = "Book",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(14.5f, 2f)
                        horizontalLineTo(9f)
                        lineToRelative(-0.35f, 0.15f)
                        lineToRelative(-0.65f, 0.64f)
                        lineToRelative(-0.65f, -0.64f)
                        lineTo(7f, 2f)
                        horizontalLineTo(1.5f)
                        lineToRelative(-0.5f, 0.5f)
                        verticalLineToRelative(10f)
                        lineToRelative(0.5f, 0.5f)
                        horizontalLineToRelative(5.29f)
                        lineToRelative(0.86f, 0.85f)
                        horizontalLineToRelative(0.7f)
                        lineToRelative(0.86f, -0.85f)
                        horizontalLineToRelative(5.29f)
                        lineToRelative(0.5f, -0.5f)
                        verticalLineToRelative(-10f)
                        lineToRelative(-0.5f, -0.5f)
                        close()
                        moveToRelative(-7f, 10.32f)
                        lineToRelative(-0.18f, -0.17f)
                        lineTo(7f, 12f)
                        horizontalLineTo(2f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(4.79f)
                        lineToRelative(0.74f, 0.74f)
                        lineToRelative(-0.03f, 8.58f)
                        close()
                        moveTo(14f, 12f)
                        horizontalLineTo(9f)
                        lineToRelative(-0.35f, 0.15f)
                        lineToRelative(-0.14f, 0.13f)
                        verticalLineTo(3.7f)
                        lineToRelative(0.7f, -0.7f)
                        horizontalLineTo(14f)
                        verticalLineToRelative(9f)
                        close()
                        moveTo(6f, 5f)
                        horizontalLineTo(3f)
                        verticalLineToRelative(1f)
                        horizontalLineToRelative(3f)
                        verticalLineTo(5f)
                        close()
                        moveToRelative(0f, 4f)
                        horizontalLineTo(3f)
                        verticalLineToRelative(1f)
                        horizontalLineToRelative(3f)
                        verticalLineTo(9f)
                        close()
                        moveTo(3f, 7f)
                        horizontalLineToRelative(3f)
                        verticalLineToRelative(1f)
                        horizontalLineTo(3f)
                        verticalLineTo(7f)
                        close()
                        moveToRelative(10f, -2f)
                        horizontalLineToRelative(-3f)
                        verticalLineToRelative(1f)
                        horizontalLineToRelative(3f)
                        verticalLineTo(5f)
                        close()
                        moveToRelative(-3f, 2f)
                        horizontalLineToRelative(3f)
                        verticalLineToRelative(1f)
                        horizontalLineToRelative(-3f)
                        verticalLineTo(7f)
                        close()
                        moveToRelative(0f, 2f)
                        horizontalLineToRelative(3f)
                        verticalLineToRelative(1f)
                        horizontalLineToRelative(-3f)
                        verticalLineTo(9f)
                        close()
                    }
                }.build()

        return _Book!!
    }

private var _Book: ImageVector? = null
