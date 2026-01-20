package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Tab_close_right: ImageVector
    get() {
        if (_Tab_close_right != null) return _Tab_close_right!!

        _Tab_close_right =
            ImageVector
                .Builder(
                    name = "Tab_close_right",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                ).apply {
                    path(
                        fill = SolidColor(Color(0xFF000000)),
                    ) {
                        moveToRelative(366f, -310f)
                        lineToRelative(114f, -114f)
                        lineToRelative(114f, 114f)
                        lineToRelative(56f, -56f)
                        lineToRelative(-114f, -114f)
                        lineToRelative(114f, -114f)
                        lineToRelative(-56f, -56f)
                        lineToRelative(-114f, 114f)
                        lineToRelative(-114f, -114f)
                        lineToRelative(-56f, 56f)
                        lineToRelative(114f, 114f)
                        lineToRelative(-114f, 114f)
                        close()
                        moveTo(200f, 760f)
                        verticalLineToRelative(-560f)
                        close()
                        verticalLineToRelative(-1f)
                        close()
                        moveToRelative(0f, 80f)
                        quadToRelative(-33f, 0f, -56.5f, -23.5f)
                        reflectiveQuadTo(120f, 760f)
                        verticalLineToRelative(-560f)
                        quadToRelative(0f, -33f, 23.5f, -56.5f)
                        reflectiveQuadTo(200f, 120f)
                        horizontalLineToRelative(560f)
                        quadToRelative(33f, 0f, 56.5f, 23.5f)
                        reflectiveQuadTo(840f, 200f)
                        verticalLineToRelative(334f)
                        quadToRelative(-19f, -7f, -39f, -10.5f)
                        reflectiveQuadToRelative(-41f, -3.5f)
                        verticalLineToRelative(-320f)
                        horizontalLineTo(200f)
                        verticalLineToRelative(560f)
                        horizontalLineToRelative(320f)
                        quadToRelative(0f, 21f, 3.5f, 41f)
                        reflectiveQuadToRelative(10.5f, 39f)
                        close()
                        moveToRelative(560f, 80f)
                        lineToRelative(-56f, -56f)
                        lineToRelative(63f, -64f)
                        horizontalLineTo(600f)
                        verticalLineToRelative(-80f)
                        horizontalLineToRelative(167f)
                        lineToRelative(-63f, -64f)
                        lineToRelative(56f, -56f)
                        lineToRelative(160f, 160f)
                        close()
                    }
                }.build()

        return _Tab_close_right!!
    }

private var _Tab_close_right: ImageVector? = null
