package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CheckCircle: ImageVector
    get() {
        if (_checkCircle != null) {
            return _checkCircle!!
        }
        _checkCircle =
            Builder(
                name = "CheckCircle",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    curveTo(2.0f, 17.52f, 6.48f, 22.0f, 12.0f, 22.0f)
                    curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                    curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(10.0f, 17.0f)
                    lineTo(5.0f, 12.0f)
                    lineTo(6.41f, 10.59f)
                    lineTo(10.0f, 14.17f)
                    lineTo(17.59f, 6.58f)
                    lineTo(19.0f, 8.0f)
                    lineTo(10.0f, 17.0f)
                    close()
                }
            }.build()
        return _checkCircle!!
    }

private var _checkCircle: ImageVector? = null
