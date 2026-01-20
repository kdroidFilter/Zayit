package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Align_end: ImageVector
    get() {
        if (_Align_end != null) return _Align_end!!

        _Align_end =
            ImageVector
                .Builder(
                    name = "Align_end",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                ).apply {
                    path(
                        fill = SolidColor(Color(0xFF000000)),
                    ) {
                        moveTo(80f, 880f)
                        verticalLineToRelative(-80f)
                        horizontalLineToRelative(800f)
                        verticalLineToRelative(80f)
                        close()
                        moveToRelative(200f, -440f)
                        verticalLineToRelative(-120f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(120f)
                        close()
                        moveToRelative(0f, 240f)
                        verticalLineToRelative(-120f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(120f)
                        close()
                    }
                }.build()

        return _Align_end!!
    }

private var _Align_end: ImageVector? = null
