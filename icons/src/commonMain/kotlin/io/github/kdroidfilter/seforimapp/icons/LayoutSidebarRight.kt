package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LayoutSidebarRight: ImageVector
    get() {
        if (_LayoutSidebarRight != null) return _LayoutSidebarRight!!

        _LayoutSidebarRight =
            ImageVector
                .Builder(
                    name = "LayoutSidebarRight",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(2f, 1f)
                        lineTo(1f, 2f)
                        verticalLineTo(14f)
                        lineTo(2f, 15f)
                        horizontalLineTo(14f)
                        lineTo(15f, 14f)
                        verticalLineTo(2f)
                        lineTo(14f, 1f)
                        horizontalLineTo(2f)
                        close()
                        moveTo(2f, 14f)
                        verticalLineTo(2f)
                        horizontalLineTo(9f)
                        verticalLineTo(14f)
                        horizontalLineTo(2f)
                        close()
                    }
                }.build()

        return _LayoutSidebarRight!!
    }

private var _LayoutSidebarRight: ImageVector? = null
