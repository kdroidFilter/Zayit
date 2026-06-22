package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val HardDriveUpload: ImageVector
    get() {
        if (_HardDriveUpload != null) return _HardDriveUpload!!

        _HardDriveUpload =
            ImageVector
                .Builder(
                    name = "HardDriveUpload",
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
                        moveToRelative(16f, 6f)
                        lineToRelative(-4f, -4f)
                        lineToRelative(-4f, 4f)
                        moveToRelative(4f, -4f)
                        verticalLineToRelative(8f)
                    }
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(4f, 14f)
                        horizontalLineTo(20f)
                        arcTo(2f, 2f, 0f, false, true, 22f, 16f)
                        verticalLineTo(20f)
                        arcTo(2f, 2f, 0f, false, true, 20f, 22f)
                        horizontalLineTo(4f)
                        arcTo(2f, 2f, 0f, false, true, 2f, 20f)
                        verticalLineTo(16f)
                        arcTo(2f, 2f, 0f, false, true, 4f, 14f)
                        close()
                    }
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(6f, 18f)
                        horizontalLineToRelative(0.01f)
                        moveTo(10f, 18f)
                        horizontalLineToRelative(0.01f)
                    }
                }.build()

        return _HardDriveUpload!!
    }

private var _HardDriveUpload: ImageVector? = null
