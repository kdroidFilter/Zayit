package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Map: ImageVector
    get() {
        if (_Map != null) return _Map!!

        _Map =
            ImageVector
                .Builder(
                    name = "Map",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(15.817f, 0.113f)
                        arcTo(0.5f, 0.5f, 0f, false, true, 16f, 0.5f)
                        verticalLineToRelative(14f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, -0.402f, 0.49f)
                        lineToRelative(-5f, 1f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, -0.196f, 0f)
                        lineTo(5.5f, 15.01f)
                        lineToRelative(-4.902f, 0.98f)
                        arcTo(0.5f, 0.5f, 0f, false, true, 0f, 15.5f)
                        verticalLineToRelative(-14f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0.402f, -0.49f)
                        lineToRelative(5f, -1f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0.196f, 0f)
                        lineTo(10.5f, 0.99f)
                        lineToRelative(4.902f, -0.98f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0.415f, 0.103f)
                        moveTo(10f, 1.91f)
                        lineToRelative(-4f, -0.8f)
                        verticalLineToRelative(12.98f)
                        lineToRelative(4f, 0.8f)
                        close()
                        moveToRelative(1f, 12.98f)
                        lineToRelative(4f, -0.8f)
                        verticalLineTo(1.11f)
                        lineToRelative(-4f, 0.8f)
                        close()
                        moveToRelative(-6f, -0.8f)
                        verticalLineTo(1.11f)
                        lineToRelative(-4f, 0.8f)
                        verticalLineToRelative(12.98f)
                        close()
                    }
                }.build()

        return _Map!!
    }

private var _Map: ImageVector? = null
