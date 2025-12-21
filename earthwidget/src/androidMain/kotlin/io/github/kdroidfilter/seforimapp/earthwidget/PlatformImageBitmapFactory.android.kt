package io.github.kdroidfilter.seforimapp.earthwidget

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun imageBitmapFromArgb(argb: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(argb, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

internal actual fun earthTextureFromImageBitmap(image: ImageBitmap): EarthTexture {
    val bitmap = image.asAndroidBitmap()
    val width = bitmap.width
    val height = bitmap.height
    val argb = IntArray(width * height)
    bitmap.getPixels(argb, 0, width, 0, 0, width, height)
    return EarthTexture(argb = argb, width = width, height = height)
}
