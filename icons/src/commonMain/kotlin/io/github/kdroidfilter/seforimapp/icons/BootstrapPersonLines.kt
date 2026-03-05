package io.github.kdroidfilter.seforimapp.icons

/*
The MIT License (MIT)

Copyright (c) 2019-2024 The Bootstrap Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BootstrapPersonLines: ImageVector
    get() {
        if (_BootstrapPersonLines != null) return _BootstrapPersonLines!!

        _BootstrapPersonLines =
            ImageVector
                .Builder(
                    name = "person-lines",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(6f, 8f)
                        arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
                        arcToRelative(3f, 3f, 0f, false, false, 0f, 6f)
                        moveToRelative(-5f, 6f)
                        reflectiveCurveToRelative(-1f, 0f, -1f, -1f)
                        reflectiveCurveToRelative(1f, -4f, 6f, -4f)
                        reflectiveCurveToRelative(6f, 3f, 6f, 4f)
                        reflectiveCurveToRelative(-1f, 1f, -1f, 1f)
                        close()
                        moveTo(11f, 3.5f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0.5f, -0.5f)
                        horizontalLineToRelative(4f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, 0f, 1f)
                        horizontalLineToRelative(-4f)
                        arcToRelative(0.5f, 0.5f, 0f, false, true, -0.5f, -0.5f)
                        moveToRelative(0.5f, 2.5f)
                        arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, 1f)
                        horizontalLineToRelative(4f)
                        arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, -1f)
                        close()
                        moveToRelative(2f, 3f)
                        arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, 1f)
                        horizontalLineToRelative(2f)
                        arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, -1f)
                        close()
                        moveToRelative(0f, 3f)
                        arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, 1f)
                        horizontalLineToRelative(2f)
                        arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, -1f)
                        close()
                    }
                }.build()

        return _BootstrapPersonLines!!
    }

private var _BootstrapPersonLines: ImageVector? = null
