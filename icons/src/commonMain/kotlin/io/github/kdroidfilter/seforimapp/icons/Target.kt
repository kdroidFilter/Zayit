package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Target: ImageVector
    get() {
        if (_Target != null) return _Target!!

        _Target =
            ImageVector
                .Builder(
                    name = "Target",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(8f, 9f)
                        curveTo(8.55228f, 9f, 9f, 8.55228f, 9f, 8f)
                        curveTo(9f, 7.44772f, 8.55228f, 7f, 8f, 7f)
                        curveTo(7.44772f, 7f, 7f, 7.44772f, 7f, 8f)
                        curveTo(7f, 8.55228f, 7.44772f, 9f, 8f, 9f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(12f, 8f)
                        curveTo(12f, 10.2091f, 10.2091f, 12f, 8f, 12f)
                        curveTo(5.79086f, 12f, 4f, 10.2091f, 4f, 8f)
                        curveTo(4f, 5.79086f, 5.79086f, 4f, 8f, 4f)
                        curveTo(10.2091f, 4f, 12f, 5.79086f, 12f, 8f)
                        close()
                        moveTo(8f, 11f)
                        curveTo(9.65685f, 11f, 11f, 9.65685f, 11f, 8f)
                        curveTo(11f, 6.34315f, 9.65685f, 5f, 8f, 5f)
                        curveTo(6.34315f, 5f, 5f, 6.34315f, 5f, 8f)
                        curveTo(5f, 9.65685f, 6.34315f, 11f, 8f, 11f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(15f, 8f)
                        curveTo(15f, 11.866f, 11.866f, 15f, 8f, 15f)
                        curveTo(4.13401f, 15f, 1f, 11.866f, 1f, 8f)
                        curveTo(1f, 4.13401f, 4.13401f, 1f, 8f, 1f)
                        curveTo(11.866f, 1f, 15f, 4.13401f, 15f, 8f)
                        close()
                        moveTo(8f, 14f)
                        curveTo(11.3137f, 14f, 14f, 11.3137f, 14f, 8f)
                        curveTo(14f, 4.68629f, 11.3137f, 2f, 8f, 2f)
                        curveTo(4.68629f, 2f, 2f, 4.68629f, 2f, 8f)
                        curveTo(2f, 11.3137f, 4.68629f, 14f, 8f, 14f)
                        close()
                    }
                }.build()

        return _Target!!
    }

private var _Target: ImageVector? = null
