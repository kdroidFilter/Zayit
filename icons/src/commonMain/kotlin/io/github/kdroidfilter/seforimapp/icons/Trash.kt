package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Lucide "trash-2"
val Trash: ImageVector
    get() {
        if (_Trash != null) return _Trash!!

        _Trash =
            ImageVector
                .Builder(
                    name = "Trash",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(
                        stroke = SolidColor(Color(0xFF000000)),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        // Lid
                        moveTo(3f, 6f)
                        horizontalLineTo(21f)
                        // Bin
                        moveTo(19f, 6f)
                        verticalLineToRelative(14f)
                        curveToRelative(0f, 1f, -1f, 2f, -2f, 2f)
                        horizontalLineTo(7f)
                        curveToRelative(-1f, 0f, -2f, -1f, -2f, -2f)
                        verticalLineTo(6f)
                        // Handle
                        moveTo(8f, 6f)
                        verticalLineTo(4f)
                        curveToRelative(0f, -1f, 1f, -2f, 2f, -2f)
                        horizontalLineToRelative(4f)
                        curveToRelative(1f, 0f, 2f, 1f, 2f, 2f)
                        verticalLineToRelative(2f)
                        // Slats
                        moveTo(10f, 11f)
                        verticalLineToRelative(6f)
                        moveTo(14f, 11f)
                        verticalLineToRelative(6f)
                    }
                }.build()

        return _Trash!!
    }

private var _Trash: ImageVector? = null
