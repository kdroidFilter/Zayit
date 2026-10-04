package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Share: ImageVector
    get() {
        if (_Share != null) return _Share!!

        _Share =
            ImageVector
                .Builder(
                    name = "Share",
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
                        // Box open at the top
                        moveTo(9f, 8.25f)
                        horizontalLineTo(7.5f)
                        arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, 2.25f)
                        verticalLineToRelative(9f)
                        arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, 2.25f)
                        horizontalLineToRelative(9f)
                        arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, -2.25f)
                        verticalLineToRelative(-9f)
                        arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, -2.25f)
                        horizontalLineTo(15f)
                        // Arrow pointing up out of the box
                        moveTo(15f, 5.25f)
                        lineTo(12f, 2.25f)
                        lineTo(9f, 5.25f)
                        moveTo(12f, 2.25f)
                        verticalLineTo(15f)
                    }
                }.build()

        return _Share!!
    }

private var _Share: ImageVector? = null
