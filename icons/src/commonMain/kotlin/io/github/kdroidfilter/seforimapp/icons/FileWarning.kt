package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FileWarning: ImageVector
    get() {
        if (_FileWarning != null) return _FileWarning!!

        _FileWarning =
            ImageVector
                .Builder(
                    name = "FileWarning",
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
                        moveTo(15f, 2f)
                        horizontalLineTo(6f)
                        arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                        verticalLineToRelative(16f)
                        arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                        horizontalLineToRelative(12f)
                        arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                        verticalLineTo(7f)
                        close()
                        moveToRelative(-3f, 7f)
                        verticalLineToRelative(4f)
                        moveToRelative(0f, 4f)
                        horizontalLineToRelative(0.01f)
                    }
                }.build()

        return _FileWarning!!
    }

private var _FileWarning: ImageVector? = null
