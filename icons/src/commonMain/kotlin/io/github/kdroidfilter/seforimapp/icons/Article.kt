package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Article: ImageVector
    get() {
        if (_Article != null) return _Article!!

        _Article =
            ImageVector
                .Builder(
                    name = "Article",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                ).apply {
                    path(
                        fill = SolidColor(Color(0xFF000000)),
                    ) {
                        moveTo(280f, 680f)
                        horizontalLineToRelative(280f)
                        verticalLineToRelative(-80f)
                        horizontalLineTo(280f)
                        close()
                        moveToRelative(0f, -160f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(-80f)
                        horizontalLineTo(280f)
                        close()
                        moveToRelative(0f, -160f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(-80f)
                        horizontalLineTo(280f)
                        close()
                        moveToRelative(-80f, 480f)
                        quadToRelative(-33f, 0f, -56.5f, -23.5f)
                        reflectiveQuadTo(120f, 760f)
                        verticalLineToRelative(-560f)
                        quadToRelative(0f, -33f, 23.5f, -56.5f)
                        reflectiveQuadTo(200f, 120f)
                        horizontalLineToRelative(560f)
                        quadToRelative(33f, 0f, 56.5f, 23.5f)
                        reflectiveQuadTo(840f, 200f)
                        verticalLineToRelative(560f)
                        quadToRelative(0f, 33f, -23.5f, 56.5f)
                        reflectiveQuadTo(760f, 840f)
                        close()
                        moveToRelative(0f, -80f)
                        horizontalLineToRelative(560f)
                        verticalLineToRelative(-560f)
                        horizontalLineTo(200f)
                        close()
                        moveToRelative(0f, -560f)
                        verticalLineToRelative(560f)
                        close()
                    }
                }.build()

        return _Article!!
    }

private var _Article: ImageVector? = null
