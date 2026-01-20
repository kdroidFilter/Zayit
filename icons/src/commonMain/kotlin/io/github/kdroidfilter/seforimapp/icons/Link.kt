package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Link: ImageVector
    get() {
        if (_Link != null) return _Link!!

        _Link =
            ImageVector
                .Builder(
                    name = "Link",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(
                        stroke = SolidColor(Color(0xFF0F172A)),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(13.1903f, 8.68842f)
                        curveTo(13.6393f, 8.90291f, 14.0601f, 9.19611f, 14.432f, 9.56802f)
                        curveTo(16.1893f, 11.3254f, 16.1893f, 14.1746f, 14.432f, 15.932f)
                        lineTo(9.93198f, 20.432f)
                        curveTo(8.17462f, 22.1893f, 5.32538f, 22.1893f, 3.56802f, 20.432f)
                        curveTo(1.81066f, 18.6746f, 1.81066f, 15.8254f, 3.56802f, 14.068f)
                        lineTo(5.32499f, 12.311f)
                        moveTo(18.675f, 11.689f)
                        lineTo(20.432f, 9.93198f)
                        curveTo(22.1893f, 8.17462f, 22.1893f, 5.32538f, 20.432f, 3.56802f)
                        curveTo(18.6746f, 1.81066f, 15.8254f, 1.81066f, 14.068f, 3.56802f)
                        lineTo(9.56802f, 8.06802f)
                        curveTo(7.81066f, 9.82538f, 7.81066f, 12.6746f, 9.56802f, 14.432f)
                        curveTo(9.93992f, 14.8039f, 10.3607f, 15.0971f, 10.8097f, 15.3116f)
                    }
                }.build()

        return _Link!!
    }

private var _Link: ImageVector? = null
