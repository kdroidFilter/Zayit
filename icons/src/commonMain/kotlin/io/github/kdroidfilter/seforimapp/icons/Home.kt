package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Composable
fun homeTabs(tint: Color): ImageVector =
    ImageVector
        .Builder(
            name = "Home_Tabs",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
            tintColor = tint,
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF6C707E)),
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.332f, 2.632f)
                lineTo(13.332f, 7.075f)
                curveTo(13.439f, 7.17f, 13.5f, 7.306f, 13.5f, 7.449f)
                verticalLineTo(13f)
                curveTo(13.5f, 13.276f, 13.276f, 13.5f, 13f, 13.5f)
                horizontalLineTo(10f)
                curveTo(9.724f, 13.5f, 9.5f, 13.276f, 9.5f, 13f)
                verticalLineTo(11f)
                curveTo(9.5f, 10.172f, 8.828f, 9.5f, 8f, 9.5f)
                curveTo(7.172f, 9.5f, 6.5f, 10.172f, 6.5f, 11f)
                verticalLineTo(13f)
                curveTo(6.5f, 13.276f, 6.276f, 13.5f, 6f, 13.5f)
                horizontalLineTo(3f)
                curveTo(2.724f, 13.5f, 2.5f, 13.276f, 2.5f, 13f)
                verticalLineTo(7.449f)
                curveTo(2.5f, 7.306f, 2.561f, 7.17f, 2.668f, 7.075f)
                lineTo(7.668f, 2.632f)
                curveTo(7.857f, 2.464f, 8.143f, 2.464f, 8.332f, 2.632f)
                close()
            }
        }.build()
