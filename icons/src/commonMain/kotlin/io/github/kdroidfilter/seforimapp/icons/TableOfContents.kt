package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TableOfContents: ImageVector
    get() {
        if (_TableOfContents != null) return _TableOfContents!!

        _TableOfContents =
            ImageVector
                .Builder(
                    name = "TableOfContents",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(16f, 12f)
                        horizontalLineTo(3f)
                        moveToRelative(13f, 6f)
                        horizontalLineTo(3f)
                        moveTo(16f, 6f)
                        horizontalLineTo(3f)
                        moveToRelative(18f, 6f)
                        horizontalLineToRelative(0.01f)
                        moveTo(21f, 18f)
                        horizontalLineToRelative(0.01f)
                        moveTo(21f, 6f)
                        horizontalLineToRelative(0.01f)
                    }
                }.build()

        return _TableOfContents!!
    }

private var _TableOfContents: ImageVector? = null
