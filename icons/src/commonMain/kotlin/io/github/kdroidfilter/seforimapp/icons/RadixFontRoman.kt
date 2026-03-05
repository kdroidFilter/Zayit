package io.github.kdroidfilter.seforimapp.icons

/*
MIT License

Copyright (c) 2022 WorkOS

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RadixFontRoman: ImageVector
    get() {
        if (_RadixFontRoman != null) return _RadixFontRoman!!

        _RadixFontRoman =
            ImageVector
                .Builder(
                    name = "font-roman",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 15f,
                    viewportHeight = 15f,
                ).apply {
                    // Horizontal top bar
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.6f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(4.2f, 2.8f)
                        horizontalLineTo(10.8f)
                    }
                    // Vertical stem
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.6f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(7.5f, 2.8f)
                        verticalLineTo(12.2f)
                    }
                    // Horizontal bottom bar
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.6f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(4.2f, 12.2f)
                        horizontalLineTo(10.8f)
                    }
                }.build()

        return _RadixFontRoman!!
    }

private var _RadixFontRoman: ImageVector? = null
