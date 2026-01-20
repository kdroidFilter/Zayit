package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Download_for_offline: ImageVector
    get() {
        if (_Download_for_offline != null) return _Download_for_offline!!

        _Download_for_offline =
            ImageVector
                .Builder(
                    name = "Download_for_offline",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                ).apply {
                    path(
                        fill = SolidColor(Color(0xFF000000)),
                    ) {
                        moveTo(280f, 680f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(-80f)
                        horizontalLineTo(280f)
                        close()
                        moveToRelative(200f, -120f)
                        lineToRelative(160f, -160f)
                        lineToRelative(-56f, -56f)
                        lineToRelative(-64f, 62f)
                        verticalLineToRelative(-166f)
                        horizontalLineToRelative(-80f)
                        verticalLineToRelative(166f)
                        lineToRelative(-64f, -62f)
                        lineToRelative(-56f, 56f)
                        close()
                        moveToRelative(0f, 320f)
                        quadToRelative(-83f, 0f, -156f, -31.5f)
                        reflectiveQuadTo(197f, 763f)
                        reflectiveQuadToRelative(-85.5f, -127f)
                        reflectiveQuadTo(80f, 480f)
                        reflectiveQuadToRelative(31.5f, -156f)
                        reflectiveQuadTo(197f, 197f)
                        reflectiveQuadToRelative(127f, -85.5f)
                        reflectiveQuadTo(480f, 80f)
                        reflectiveQuadToRelative(156f, 31.5f)
                        reflectiveQuadTo(763f, 197f)
                        reflectiveQuadToRelative(85.5f, 127f)
                        reflectiveQuadTo(880f, 480f)
                        reflectiveQuadToRelative(-31.5f, 156f)
                        reflectiveQuadTo(763f, 763f)
                        reflectiveQuadToRelative(-127f, 85.5f)
                        reflectiveQuadTo(480f, 880f)
                        moveToRelative(0f, -80f)
                        quadToRelative(134f, 0f, 227f, -93f)
                        reflectiveQuadToRelative(93f, -227f)
                        reflectiveQuadToRelative(-93f, -227f)
                        reflectiveQuadToRelative(-227f, -93f)
                        reflectiveQuadToRelative(-227f, 93f)
                        reflectiveQuadToRelative(-93f, 227f)
                        reflectiveQuadToRelative(93f, 227f)
                        reflectiveQuadToRelative(227f, 93f)
                        moveToRelative(0f, -320f)
                    }
                }.build()

        return _Download_for_offline!!
    }

private var _Download_for_offline: ImageVector? = null
