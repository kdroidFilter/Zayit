package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ListTree: ImageVector
    get() {
        if (_ListTree != null) return _ListTree!!

        _ListTree =
            ImageVector
                .Builder(
                    name = "ListTree",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                    autoMirror = true,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(4f, 9f)
                        horizontalLineTo(13f)
                        verticalLineTo(10f)
                        horizontalLineTo(4f)
                        verticalLineTo(9f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(4f, 12f)
                        horizontalLineTo(11f)
                        verticalLineTo(13f)
                        horizontalLineTo(4f)
                        verticalLineTo(12f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(4f, 6f)
                        horizontalLineTo(14f)
                        verticalLineTo(7f)
                        horizontalLineTo(4f)
                        verticalLineTo(6f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(1f, 3f)
                        horizontalLineTo(12f)
                        verticalLineTo(4f)
                        horizontalLineTo(1f)
                        verticalLineTo(3f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(4f, 4f)
                        horizontalLineTo(5f)
                        verticalLineTo(13f)
                        horizontalLineTo(4f)
                        verticalLineTo(4f)
                        close()
                    }
                }.build()

        return _ListTree!!
    }

private var _ListTree: ImageVector? = null
