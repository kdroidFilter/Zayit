package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Library: ImageVector
    get() {
        if (_Library != null) return _Library!!

        _Library =
            ImageVector
                .Builder(
                    name = "Library",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                    autoMirror = true,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(5f, 2.5f)
                        lineToRelative(0.5f, -0.5f)
                        horizontalLineToRelative(2f)
                        lineToRelative(0.5f, 0.5f)
                        verticalLineToRelative(11f)
                        lineToRelative(-0.5f, 0.5f)
                        horizontalLineToRelative(-2f)
                        lineToRelative(-0.5f, -0.5f)
                        verticalLineToRelative(-11f)
                        close()
                        moveTo(6f, 3f)
                        verticalLineToRelative(10f)
                        horizontalLineToRelative(1f)
                        verticalLineTo(3f)
                        horizontalLineTo(6f)
                        close()
                        moveToRelative(3.171f, 0.345f)
                        lineToRelative(0.299f, -0.641f)
                        lineToRelative(1.88f, -0.684f)
                        lineToRelative(0.64f, 0.299f)
                        lineToRelative(3.762f, 10.336f)
                        lineToRelative(-0.299f, 0.641f)
                        lineToRelative(-1.879f, 0.684f)
                        lineToRelative(-0.64f, -0.299f)
                        lineTo(9.17f, 3.345f)
                        close()
                        moveToRelative(1.11f, 0.128f)
                        lineToRelative(3.42f, 9.396f)
                        lineToRelative(0.94f, -0.341f)
                        lineToRelative(-3.42f, -9.397f)
                        lineToRelative(-0.94f, 0.342f)
                        close()
                        moveTo(1f, 2.5f)
                        lineToRelative(0.5f, -0.5f)
                        horizontalLineToRelative(2f)
                        lineToRelative(0.5f, 0.5f)
                        verticalLineToRelative(11f)
                        lineToRelative(-0.5f, 0.5f)
                        horizontalLineToRelative(-2f)
                        lineToRelative(-0.5f, -0.5f)
                        verticalLineToRelative(-11f)
                        close()
                        moveTo(2f, 3f)
                        verticalLineToRelative(10f)
                        horizontalLineToRelative(1f)
                        verticalLineTo(3f)
                        horizontalLineTo(2f)
                        close()
                    }
                }.build()

        return _Library!!
    }

private var _Library: ImageVector? = null
