package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ColorMode: ImageVector
    get() {
        if (_ColorMode != null) return _ColorMode!!

        _ColorMode =
            ImageVector
                .Builder(
                    name = "ColorMode",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(8f, 1f)
                        arcToRelative(7f, 7f, 0f, true, false, 0f, 14f)
                        arcTo(7f, 7f, 0f, false, false, 8f, 1f)
                        close()
                        moveToRelative(0f, 13f)
                        verticalLineTo(2f)
                        arcToRelative(6f, 6f, 0f, true, true, 0f, 12f)
                        close()
                    }
                }.build()

        return _ColorMode!!
    }

private var _ColorMode: ImageVector? = null
