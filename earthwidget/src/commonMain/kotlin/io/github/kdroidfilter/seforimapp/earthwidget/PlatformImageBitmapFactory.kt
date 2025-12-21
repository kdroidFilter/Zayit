package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun imageBitmapFromArgb(argb: IntArray, width: Int, height: Int): ImageBitmap

internal expect fun earthTextureFromImageBitmap(image: ImageBitmap): EarthTexture
