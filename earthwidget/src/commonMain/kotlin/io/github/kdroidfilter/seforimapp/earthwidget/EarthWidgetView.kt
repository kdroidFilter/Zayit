package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthmap1k
import seforimapp.earthwidget.generated.resources.moonmap2k
import seforimapp.earthwidget.generated.resources.earthwidget_light_label
import seforimapp.earthwidget.generated.resources.earthwidget_marker_latitude_label
import seforimapp.earthwidget.generated.resources.earthwidget_marker_longitude_label
import seforimapp.earthwidget.generated.resources.earthwidget_moon_orbit_label
import seforimapp.earthwidget.generated.resources.earthwidget_rotation_label
import seforimapp.earthwidget.generated.resources.earthwidget_show_background_label
import seforimapp.earthwidget.generated.resources.earthwidget_show_orbit_label
import seforimapp.earthwidget.generated.resources.earthwidget_sun_elevation_label
import seforimapp.earthwidget.generated.resources.earthwidget_tilt_label
import kotlin.math.roundToInt

@Composable
fun EarthWidgetView(
    modifier: Modifier = Modifier,
    sphereSize: Dp = 500.dp,
    renderSizePx: Int = 600,
    initialMarkerLatitudeDegrees: Float = 31.7683f,
    initialMarkerLongitudeDegrees: Float = 35.2137f,
) {
    val texture = rememberEarthTexture()
    val moonTexture = rememberMoonTexture()
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var lightDegrees by remember { mutableFloatStateOf(30f) }
    var sunElevationDegrees by remember { mutableFloatStateOf(12f) }
    var tiltDegrees by remember { mutableFloatStateOf(23.44f) }
    var moonOrbitDegrees by remember { mutableFloatStateOf(0f) }
    var markerLatitudeDegrees by remember { mutableFloatStateOf(initialMarkerLatitudeDegrees.coerceIn(-90f, 90f)) }
    var markerLongitudeDegrees by remember { mutableFloatStateOf(initialMarkerLongitudeDegrees.coerceIn(-180f, 180f)) }
    var showBackground by remember { mutableStateOf(true) }
    var showOrbitPath by remember { mutableStateOf(true) }

    LazyColumn (
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            val sphere = rememberEarthMoonImage(
                earthTexture = texture,
                moonTexture = moonTexture,
                renderSizePx = renderSizePx,
                earthRotationDegrees = rotationDegrees,
                lightDegrees = lightDegrees,
                sunElevationDegrees = sunElevationDegrees,
                earthTiltDegrees = tiltDegrees,
                moonOrbitDegrees = moonOrbitDegrees,
                markerLatitudeDegrees = markerLatitudeDegrees,
                markerLongitudeDegrees = markerLongitudeDegrees,
                showBackgroundStars = showBackground,
                showOrbitPath = showOrbitPath,
            )

            Image(
                bitmap = sphere,
                contentDescription = null,
                modifier = Modifier.size(sphereSize),
            )

        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(checked = showBackground, onCheckedChange = { showBackground = it })
                Text(text = stringResource(Res.string.earthwidget_show_background_label))
            }

        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(checked = showOrbitPath, onCheckedChange = { showOrbitPath = it })
                Text(text = stringResource(Res.string.earthwidget_show_orbit_label))
            }

        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_rotation_label))
                    Text(text = rotationDegrees.roundToInt().toString())
                }
                Slider(
                    value = rotationDegrees,
                    onValueChange = { rotationDegrees = it },
                    valueRange = 0f..360f,
                )
            }

        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_light_label))
                    Text(text = lightDegrees.roundToInt().toString())
                }
                Slider(
                    value = lightDegrees,
                    onValueChange = { lightDegrees = it },
                    valueRange = -180f..180f,
                )
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_sun_elevation_label))
                    Text(text = sunElevationDegrees.roundToInt().toString())
                }
                Slider(
                    value = sunElevationDegrees,
                    onValueChange = { sunElevationDegrees = it },
                    valueRange = -90f..90f,
                )
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_tilt_label))
                    Text(text = tiltDegrees.roundToInt().toString())
                }
                Slider(
                    value = tiltDegrees,
                    onValueChange = { tiltDegrees = it },
                    valueRange = -60f..60f,
                )
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_moon_orbit_label))
                    Text(text = moonOrbitDegrees.roundToInt().toString())
                }
                Slider(
                    value = moonOrbitDegrees,
                    onValueChange = { moonOrbitDegrees = it },
                    valueRange = 0f..360f,
                )
            }
        }
        item {

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_marker_latitude_label))
                    Text(text = markerLatitudeDegrees.roundToInt().toString())
                }
                Slider(
                    value = markerLatitudeDegrees,
                    onValueChange = { markerLatitudeDegrees = it },
                    valueRange = -90f..90f,
                )
            }
        }

        item {

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(Res.string.earthwidget_marker_longitude_label))
                    Text(text = markerLongitudeDegrees.roundToInt().toString())
                }
                Slider(
                    value = markerLongitudeDegrees,
                    onValueChange = { markerLongitudeDegrees = it },
                    valueRange = -180f..180f,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

        }

    }
}

@Composable
private fun rememberEarthTexture(): EarthTexture? {
    val image = imageResource(Res.drawable.earthmap1k)
    return remember(image) { earthTextureFromImageBitmap(image) }
}

@Composable
private fun rememberMoonTexture(): EarthTexture? {
    val image = imageResource(Res.drawable.moonmap2k)
    return remember(image) { earthTextureFromImageBitmap(image) }
}

@Composable
private fun rememberEarthMoonImage(
    earthTexture: EarthTexture?,
    moonTexture: EarthTexture?,
    renderSizePx: Int,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    showBackgroundStars: Boolean,
    showOrbitPath: Boolean,
): ImageBitmap {
    return remember(
        earthTexture,
        moonTexture,
        renderSizePx,
        earthRotationDegrees,
        lightDegrees,
        sunElevationDegrees,
        earthTiltDegrees,
        moonOrbitDegrees,
        markerLatitudeDegrees,
        markerLongitudeDegrees,
        showBackgroundStars,
        showOrbitPath,
    ) {
        val argb = renderEarthWithMoonArgb(
            earthTexture = earthTexture,
            moonTexture = moonTexture,
            outputSizePx = renderSizePx,
            earthRotationDegrees = earthRotationDegrees,
            lightDegrees = lightDegrees,
            sunElevationDegrees = sunElevationDegrees,
            earthTiltDegrees = earthTiltDegrees,
            moonOrbitDegrees = moonOrbitDegrees,
            markerLatitudeDegrees = markerLatitudeDegrees,
            markerLongitudeDegrees = markerLongitudeDegrees,
            showBackgroundStars = showBackgroundStars,
            showOrbitPath = showOrbitPath,
        )
        imageBitmapFromArgb(argb, renderSizePx, renderSizePx)
    }
}
