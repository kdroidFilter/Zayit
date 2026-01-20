package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Timer: ImageVector
    get() {
        if (_Timer != null) return _Timer!!

        _Timer =
            ImageVector
                .Builder(
                    name = "Timer",
                    defaultWidth = 15.dp,
                    defaultHeight = 15.dp,
                    viewportWidth = 15f,
                    viewportHeight = 15f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.EvenOdd,
                    ) {
                        moveTo(7.49998f, 0.849976f)
                        curveTo(7.22383f, 0.849976f, 6.99998f, 1.07383f, 6.99998f, 1.34998f)
                        verticalLineTo(3.52234f)
                        curveTo(6.99998f, 3.79848f, 7.22383f, 4.02234f, 7.49998f, 4.02234f)
                        curveTo(7.77612f, 4.02234f, 7.99998f, 3.79848f, 7.99998f, 3.52234f)
                        verticalLineTo(1.8718f)
                        curveTo(10.8862f, 2.12488f, 13.15f, 4.54806f, 13.15f, 7.49998f)
                        curveTo(13.15f, 10.6204f, 10.6204f, 13.15f, 7.49998f, 13.15f)
                        curveTo(4.37957f, 13.15f, 1.84998f, 10.6204f, 1.84998f, 7.49998f)
                        curveTo(1.84998f, 6.10612f, 2.35407f, 4.83128f, 3.19049f, 3.8459f)
                        curveTo(3.36919f, 3.63538f, 3.34339f, 3.31985f, 3.13286f, 3.14115f)
                        curveTo(2.92234f, 2.96245f, 2.60681f, 2.98825f, 2.42811f, 3.19877f)
                        curveTo(1.44405f, 4.35808f, 0.849976f, 5.86029f, 0.849976f, 7.49998f)
                        curveTo(0.849976f, 11.1727f, 3.82728f, 14.15f, 7.49998f, 14.15f)
                        curveTo(11.1727f, 14.15f, 14.15f, 11.1727f, 14.15f, 7.49998f)
                        curveTo(14.15f, 3.82728f, 11.1727f, 0.849976f, 7.49998f, 0.849976f)
                        close()
                        moveTo(6.74049f, 8.08072f)
                        lineTo(4.22363f, 4.57237f)
                        curveTo(4.15231f, 4.47295f, 4.16346f, 4.33652f, 4.24998f, 4.25f)
                        curveTo(4.33649f, 4.16348f, 4.47293f, 4.15233f, 4.57234f, 4.22365f)
                        lineTo(8.08069f, 6.74051f)
                        curveTo(8.56227f, 7.08599f, 8.61906f, 7.78091f, 8.19998f, 8.2f)
                        curveTo(7.78089f, 8.61909f, 7.08597f, 8.56229f, 6.74049f, 8.08072f)
                        close()
                    }
                }.build()

        return _Timer!!
    }

private var _Timer: ImageVector? = null
