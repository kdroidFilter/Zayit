package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LayoutSidebarRightOff: ImageVector
    get() {
        if (_LayoutSidebarRightOff != null) return _LayoutSidebarRightOff!!

        _LayoutSidebarRightOff =
            ImageVector
                .Builder(
                    name = "LayoutSidebarRightOff",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(2f, 1.00073f)
                        lineTo(1f, 2.00073f)
                        verticalLineTo(14.0007f)
                        lineTo(2f, 15.0007f)
                        horizontalLineTo(14f)
                        lineTo(15f, 14.0007f)
                        verticalLineTo(2.00073f)
                        lineTo(14f, 1.00073f)
                        horizontalLineTo(2f)
                        close()
                        moveTo(2f, 14.0007f)
                        verticalLineTo(2.00073f)
                        horizontalLineTo(9f)
                        verticalLineTo(14.0007f)
                        horizontalLineTo(2f)
                        close()
                        moveTo(10f, 14.0007f)
                        verticalLineTo(2.00073f)
                        horizontalLineTo(14f)
                        verticalLineTo(14.0007f)
                        horizontalLineTo(10f)
                        close()
                    }
                }.build()

        return _LayoutSidebarRightOff!!
    }

private var _LayoutSidebarRightOff: ImageVector? = null
