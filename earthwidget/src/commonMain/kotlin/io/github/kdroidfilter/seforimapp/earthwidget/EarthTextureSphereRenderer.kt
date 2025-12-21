package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEG_TO_RAD = 0.017453292f
private const val MOON_TO_EARTH_DIAMETER_RATIO = 0.2724f
private const val STARFIELD_SEED = 0x6D2B79F5
private const val CAMERA_DISTANCE_FACTOR = 1.6f

internal data class EarthTexture(
    val argb: IntArray,
    val width: Int,
    val height: Int,
)

internal fun renderTexturedSphereArgb(
    texture: EarthTexture,
    outputSizePx: Int,
    rotationDegrees: Float,
    lightDegrees: Float,
    tiltDegrees: Float,
    ambient: Float = 0.18f,
    diffuseStrength: Float = 0.92f,
    specularStrength: Float = 0f,
    specularExponent: Int = 64,
    sunElevationDegrees: Float = 0f,
    viewDirX: Float = 0f,
    viewDirY: Float = 0f,
    viewDirZ: Float = 1f,
    sunVisibility: Float = 1f,
    atmosphereStrength: Float = 0.22f,
): IntArray {
    val output = IntArray(outputSizePx * outputSizePx)

    val rotationRad = rotationDegrees * DEG_TO_RAD
    val tiltRad = tiltDegrees * DEG_TO_RAD
    val sunAzimuthRad = lightDegrees * DEG_TO_RAD
    val sunElevationRad = sunElevationDegrees * DEG_TO_RAD
    val cosSunElevation = cos(sunElevationRad)
    val sunX = sin(sunAzimuthRad) * cosSunElevation
    val sunY = sin(sunElevationRad)
    val sunZ = cos(sunAzimuthRad) * cosSunElevation

    val texWidth = texture.width
    val texHeight = texture.height
    val tex = texture.argb

    var forwardX = viewDirX
    var forwardY = viewDirY
    var forwardZ = viewDirZ
    val forwardLen = sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ)
    if (forwardLen > 1e-6f) {
        forwardX /= forwardLen
        forwardY /= forwardLen
        forwardZ /= forwardLen
    } else {
        forwardX = 0f
        forwardY = 0f
        forwardZ = 1f
    }

    var rightX = forwardZ
    var rightY = 0f
    var rightZ = -forwardX
    var rightLen = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
    if (rightLen < 1e-6f) {
        rightX = 0f
        rightY = forwardZ
        rightZ = -forwardY
        rightLen = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
    }
    if (rightLen > 1e-6f) {
        rightX /= rightLen
        rightY /= rightLen
        rightZ /= rightLen
    }

    val upX = forwardY * rightZ - forwardZ * rightY
    val upY = forwardZ * rightX - forwardX * rightZ
    val upZ = forwardX * rightY - forwardY * rightX

    var halfX = sunX + forwardX
    var halfY = sunY + forwardY
    var halfZ = sunZ + forwardZ
    val halfLen = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)
    val specEnabled = specularStrength > 0f && halfLen > 1e-6f && specularExponent > 0
    if (specEnabled) {
        halfX /= halfLen
        halfY /= halfLen
        halfZ /= halfLen
    } else {
        halfX = 0f
        halfY = 0f
        halfZ = 0f
    }

    val halfW = (outputSizePx - 1) / 2f
    val halfH = (outputSizePx - 1) / 2f
    val invHalfW = 1f / halfW
    val invHalfH = 1f / halfH
    val edgeFeather = 0.012f
    val cosYaw = cos(rotationRad)
    val sinYaw = sin(rotationRad)
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val lightVisibility = sunVisibility.coerceIn(0f, 1f)

    for (y in 0 until outputSizePx) {
        val ny = (halfH - y) * invHalfH
        for (x in 0 until outputSizePx) {
            val nx = (x - halfW) * invHalfW
            val rr = nx * nx + ny * ny
            if (rr > 1f) {
                output[y * outputSizePx + x] = 0x00000000
                continue
            }

            val nz = sqrt(1f - rr)
            val worldX = rightX * nx + upX * ny + forwardX * nz
            val worldY = rightY * nx + upY * ny + forwardY * nz
            val worldZ = rightZ * nx + upZ * ny + forwardZ * nz

            val rotatedX = worldX * cosTilt - worldY * sinTilt
            val rotatedY = worldX * sinTilt + worldY * cosTilt

            val texX = rotatedX * cosYaw + worldZ * sinYaw
            val texZ = -rotatedX * sinYaw + worldZ * cosYaw

            val longitude = atan2(texX, texZ)
            val latitude = asin(rotatedY.coerceIn(-1f, 1f))

            var u = (longitude / (2f * PI.toFloat())) + 0.5f
            u -= floor(u)
            val v = (0.5f - (latitude / PI.toFloat())).coerceIn(0f, 1f)

            val tx = (u * texWidth).toInt().coerceIn(0, texWidth - 1)
            val ty = (v * texHeight).toInt().coerceIn(0, texHeight - 1)
            val texColor = tex[ty * texWidth + tx]

            val dot = (worldX * sunX + worldY * sunY + worldZ * sunZ)
            val shadowMask = smoothStep(-0.15f, 0.1f, dot) * lightVisibility
            val diffuse = max(dot, 0f) * lightVisibility
            val ambientShade = ambient * (0.25f + 0.75f * shadowMask)
            val baseShade = (ambientShade + diffuseStrength * diffuse).coerceIn(0f, 1f)
            val viewShade = 0.75f + 0.25f * nz
            val shade = (baseShade * viewShade).coerceIn(0f, 1f)

            val rim = (1f - nz).coerceIn(0f, 1f)
            val atmosphere = (rim * rim * atmosphereStrength * shadowMask).coerceIn(0f, atmosphereStrength)

            val a = (texColor ushr 24) and 0xFF
            val r = (texColor ushr 16) and 0xFF
            val g = (texColor ushr 8) and 0xFF
            val b = texColor and 0xFF

            val rLin = (r / 255f).let { it * it }
            val gLin = (g / 255f).let { it * it }
            val bLin = (b / 255f).let { it * it }

            val spec = if (specEnabled && diffuse > 0f) {
                val dotH = (worldX * halfX + worldY * halfY + worldZ * halfZ).coerceAtLeast(0f)
                val baseSpec = specularStrength * powInt(dotH, specularExponent) * lightVisibility
                val oceanMask = ((b - max(r, g)).coerceAtLeast(0) / 255f).let { it * it }
                baseSpec * (0.12f + 0.88f * oceanMask)
            } else {
                0f
            }

            val shadedRLin = (rLin * shade + spec).coerceIn(0f, 1f)
            val shadedGLin = (gLin * shade + spec).coerceIn(0f, 1f)
            val shadedBLin = (bLin * shade + spec).coerceIn(0f, 1f)

            val sr = (sqrt(shadedRLin) * 255f).roundToInt().coerceIn(0, 255)
            val sg = (sqrt(shadedGLin) * 255f).roundToInt().coerceIn(0, 255)
            val sb = ((sqrt(shadedBLin) * 255f) + (255f * atmosphere)).roundToInt().coerceIn(0, 255)

            val dist = sqrt(rr)
            val alpha = ((1f - dist) / edgeFeather).coerceIn(0f, 1f)
            val outA = (a * alpha).toInt().coerceIn(0, 255)

            output[y * outputSizePx + x] = (outA shl 24) or (sr shl 16) or (sg shl 8) or sb
        }
    }

    return output
}

private fun perspectiveScale(cameraZ: Float, z: Float): Float {
    val denom = max(1f, cameraZ - z)
    return cameraZ / denom
}

internal fun renderEarthWithMoonArgb(
    earthTexture: EarthTexture?,
    moonTexture: EarthTexture?,
    outputSizePx: Int,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    moonRotationDegrees: Float = 0f,
    showBackgroundStars: Boolean = true,
    showOrbitPath: Boolean = true,
): IntArray {
    val out = IntArray(outputSizePx * outputSizePx)
    if (earthTexture == null) return out

    val sceneHalf = outputSizePx / 2f
    val cameraZ = outputSizePx * CAMERA_DISTANCE_FACTOR

    out.fill(0xFF000000.toInt())
    if (showBackgroundStars) {
        drawStarfield(dst = out, dstW = outputSizePx, dstH = outputSizePx, seed = STARFIELD_SEED)
    }

    val earthSizePx = (outputSizePx * 0.40f).roundToInt().coerceAtLeast(8)
    val earthRadiusPx = (earthSizePx - 1) / 2f
    val earthLeft = (sceneHalf - earthSizePx / 2f).roundToInt()
    val earthTop = (sceneHalf - earthSizePx / 2f).roundToInt()

    val moonBaseSizePx = (earthSizePx * MOON_TO_EARTH_DIAMETER_RATIO).roundToInt().coerceAtLeast(8)
    val moonRadiusWorldPx = (moonBaseSizePx - 1) / 2f
    val edgeMarginPx = max(6f, outputSizePx * 0.02f)
    val orbitRadius = (sceneHalf - moonRadiusWorldPx - edgeMarginPx).coerceAtLeast(0f)
    val desiredSeparation = earthRadiusPx + moonRadiusWorldPx + 1.5f
    val viewPitchRad = if (orbitRadius > 1e-6f) {
        asin((desiredSeparation / orbitRadius).coerceIn(0f, 0.999f))
    } else {
        0f
    }
    val orbitInclinationRad = 5.145f * DEG_TO_RAD
    // Earth's spin (earthRotationDegrees) should not rotate the Moon's orbit; keep orbit in an inertial frame.
    val yawRad = 0f
    // Earth's axial tilt should not change the Moon's orbit plane.
    val pitchRad = 0f

    val cosInc = cos(orbitInclinationRad)
    val sinInc = sin(orbitInclinationRad)
    val cosPitchOrbit = cos(pitchRad)
    val sinPitchOrbit = sin(pitchRad)
    val cosYawOrbit = cos(yawRad)
    val sinYawOrbit = sin(yawRad)
    val cosView = cos(viewPitchRad)
    val sinView = sin(viewPitchRad)

    val angle = moonOrbitDegrees * DEG_TO_RAD
    val x0 = cos(angle) * orbitRadius
    val y0 = 0f
    val z0 = sin(angle) * orbitRadius

    val yInc = y0 * cosInc - z0 * sinInc
    val zInc = y0 * sinInc + z0 * cosInc

    val y1 = yInc * cosPitchOrbit - zInc * sinPitchOrbit
    val z1 = yInc * sinPitchOrbit + zInc * cosPitchOrbit

    val x2 = x0 * cosYawOrbit + z1 * sinYawOrbit
    val z2 = -x0 * sinYawOrbit + z1 * cosYawOrbit

    val yCam = y1 * cosView - z2 * sinView
    val zCam = y1 * sinView + z2 * cosView

    val moonScale = perspectiveScale(cameraZ, zCam)
    val moonSizePx = (moonBaseSizePx * moonScale).roundToInt().coerceAtLeast(8)
    val moonRadiusPx = (moonSizePx - 1) / 2f
    val moonCenterX = sceneHalf + x2 * moonScale
    val moonCenterY = sceneHalf - yCam * moonScale
    val moonLeft = (moonCenterX - moonRadiusPx).roundToInt()
    val moonTop = (moonCenterY - moonRadiusPx).roundToInt()

    val earth = renderTexturedSphereArgb(
        texture = earthTexture,
        outputSizePx = earthSizePx,
        rotationDegrees = earthRotationDegrees,
        lightDegrees = lightDegrees,
        tiltDegrees = earthTiltDegrees,
        specularStrength = 0.18f,
        specularExponent = 128,
        sunElevationDegrees = sunElevationDegrees,
        viewDirZ = 1f,
    )
    drawMarkerOnSphere(
        sphereArgb = earth,
        sphereSizePx = earthSizePx,
        markerLatitudeDegrees = markerLatitudeDegrees,
        markerLongitudeDegrees = markerLongitudeDegrees,
        rotationDegrees = earthRotationDegrees,
        tiltDegrees = earthTiltDegrees,
    )

    blitOver(dst = out, dstW = outputSizePx, src = earth, srcW = earthSizePx, left = earthLeft, top = earthTop)

    if (showOrbitPath) {
        drawOrbitPath(
            dst = out,
            dstW = outputSizePx,
            dstH = outputSizePx,
            center = sceneHalf,
            earthRadiusPx = earthRadiusPx,
            orbitRadius = orbitRadius,
            cosInc = cosInc,
            sinInc = sinInc,
            cosPitch = cosPitchOrbit,
            sinPitch = sinPitchOrbit,
            cosYaw = cosYawOrbit,
            sinYaw = sinYawOrbit,
            cosView = cosView,
            sinView = sinView,
            moonCenterX = moonCenterX,
            moonCenterY = moonCenterY,
            moonRadiusPx = if (moonTexture != null) moonRadiusPx else 0f,
            cameraZ = cameraZ,
        )
    }

    if (moonTexture == null) return out

    val sunVisibility = moonSunVisibility(
        moonCenterX = x2,
        moonCenterY = yCam,
        moonCenterZ = zCam,
        earthRadius = earthRadiusPx,
        moonRadius = moonRadiusWorldPx,
        sunAzimuthDegrees = lightDegrees,
        sunElevationDegrees = sunElevationDegrees,
    )

    val moonViewDirX = -x2
    val moonViewDirY = -yCam
    val moonViewDirZ = cameraZ - zCam
    val moon = renderTexturedSphereArgb(
        texture = moonTexture,
        outputSizePx = moonSizePx,
        rotationDegrees = moonRotationDegrees,
        lightDegrees = lightDegrees,
        tiltDegrees = 0f,
        ambient = 0.04f,
        diffuseStrength = 0.96f,
        sunElevationDegrees = sunElevationDegrees,
        viewDirX = moonViewDirX,
        viewDirY = moonViewDirY,
        viewDirZ = moonViewDirZ,
        sunVisibility = sunVisibility,
        atmosphereStrength = 0f,
    )

    val x0Moon = moonLeft.coerceAtLeast(0)
    val y0Moon = moonTop.coerceAtLeast(0)
    val x1Moon = (moonLeft + moonSizePx).coerceAtMost(outputSizePx)
    val y1Moon = (moonTop + moonSizePx).coerceAtMost(outputSizePx)
    val invMoonScale = if (moonScale > 1e-6f) 1f / moonScale else 1f

    for (y in y0Moon until y1Moon) {
        val moonY = y - moonTop
        val moonDyScreen = moonRadiusPx - moonY
        val moonDyWorld = moonDyScreen * invMoonScale
        val moonRow = moonY * moonSizePx

        val earthY = y - earthTop
        val earthDy = earthRadiusPx - earthY
        val earthRow = earthY * earthSizePx

        val hasEarthRow = earthY in 0 until earthSizePx

        for (x in x0Moon until x1Moon) {
            val moonX = x - moonLeft
            val moonColor = moon[moonRow + moonX]
            val moonA = (moonColor ushr 24) and 0xFF
            if (moonA == 0) continue

            val dstIndex = y * outputSizePx + x

            if (!hasEarthRow) {
                out[dstIndex] = alphaOver(moonColor, out[dstIndex])
                continue
            }

            val earthX = x - earthLeft
            if (earthX !in 0 until earthSizePx) {
                out[dstIndex] = alphaOver(moonColor, out[dstIndex])
                continue
            }

            val earthColor = earth[earthRow + earthX]
            val earthA = (earthColor ushr 24) and 0xFF
            if (earthA == 0) {
                out[dstIndex] = alphaOver(moonColor, out[dstIndex])
                continue
            }

            val earthDx = earthX - earthRadiusPx
            val moonDxScreen = moonX - moonRadiusPx
            val moonDxWorld = moonDxScreen * invMoonScale
            val earthR2 = (earthDx * earthDx + earthDy * earthDy) / (earthRadiusPx * earthRadiusPx)
            val moonR2 = (moonDxWorld * moonDxWorld + moonDyWorld * moonDyWorld) /
                (moonRadiusWorldPx * moonRadiusWorldPx)
            val earthZ = sqrt(max(0f, 1f - earthR2)) * earthRadiusPx
            val moonZ = sqrt(max(0f, 1f - moonR2)) * moonRadiusWorldPx

            val moonDepth = zCam + moonZ
            if (moonDepth > earthZ) {
                out[dstIndex] = alphaOver(moonColor, earthColor)
            } else {
                out[dstIndex] = alphaOver(earthColor, moonColor)
            }
        }
    }

    return out
}

private fun drawOrbitPath(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    center: Float,
    earthRadiusPx: Float,
    orbitRadius: Float,
    cosInc: Float,
    sinInc: Float,
    cosPitch: Float,
    sinPitch: Float,
    cosYaw: Float,
    sinYaw: Float,
    cosView: Float,
    sinView: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusPx: Float,
    cameraZ: Float,
) {
    if (orbitRadius <= 0f) return

    val steps = (orbitRadius * 5.2f).roundToInt().coerceIn(420, 1600)
    val orbitColorRgb = 0x00FFFFFF
    val earthRadius2 = earthRadiusPx * earthRadiusPx
    val moonRadiusClip2 = if (moonRadiusPx > 0f) (moonRadiusPx + 3.0f).let { it * it } else -1f

    var prevX = Int.MIN_VALUE
    var prevY = Int.MIN_VALUE
    var prevZ = 0f

    val twoPi = (2f * PI.toFloat())
    for (i in 0..steps) {
        val t = (i.toFloat() / steps) * twoPi
        val x0 = cos(t) * orbitRadius
        val z0 = sin(t) * orbitRadius

        val yInc = -z0 * sinInc
        val zInc = z0 * cosInc

        val y1 = yInc * cosPitch - zInc * sinPitch
        val z1 = yInc * sinPitch + zInc * cosPitch

        val x2 = x0 * cosYaw + z1 * sinYaw
        val z2 = -x0 * sinYaw + z1 * cosYaw

        val yCam = y1 * cosView - z2 * sinView
        val zCam = y1 * sinView + z2 * cosView

        val orbitScale = perspectiveScale(cameraZ, zCam)
        val sx = (center + x2 * orbitScale).roundToInt()
        val sy = (center - yCam * orbitScale).roundToInt()

        if (prevX != Int.MIN_VALUE) {
            val avgZ = (prevZ + zCam) * 0.5f
            val alpha = if (avgZ >= 0f) 0xC8 else 0x6C
            val color = (alpha shl 24) or orbitColorRgb
            drawOrbitLineSegment(
                dst = dst,
                dstW = dstW,
                dstH = dstH,
                x0 = prevX,
                y0 = prevY,
                x1 = sx,
                y1 = sy,
                color = color,
                orbitZ = avgZ,
                earthCenter = center,
                earthRadiusPx = earthRadiusPx,
                earthRadius2 = earthRadius2,
                moonCenterX = moonCenterX,
                moonCenterY = moonCenterY,
                moonRadiusClip2 = moonRadiusClip2,
            )
        }

        prevX = sx
        prevY = sy
        prevZ = zCam
    }
}

private fun drawOrbitLineSegment(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    color: Int,
    orbitZ: Float,
    earthCenter: Float,
    earthRadiusPx: Float,
    earthRadius2: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusClip2: Float,
) {
    var x = x0
    var y = y0
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy

    while (true) {
        plotOrbitPixel(
            dst = dst,
            dstW = dstW,
            dstH = dstH,
            x = x,
            y = y,
            color = color,
            orbitZ = orbitZ,
            earthCenter = earthCenter,
            earthRadiusPx = earthRadiusPx,
            earthRadius2 = earthRadius2,
            moonCenterX = moonCenterX,
            moonCenterY = moonCenterY,
            moonRadiusClip2 = moonRadiusClip2,
        )

        if (x == x1 && y == y1) break
        val e2 = 2 * err
        if (e2 >= dy) {
            if (x == x1) break
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            if (y == y1) break
            err += dx
            y += sy
        }
    }
}

private fun plotOrbitPixel(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    x: Int,
    y: Int,
    color: Int,
    orbitZ: Float,
    earthCenter: Float,
    earthRadiusPx: Float,
    earthRadius2: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusClip2: Float,
) {
    if (x !in 0 until dstW || y !in 0 until dstH) return

    val dx = x - earthCenter
    val dy = y - earthCenter
    val r2 = dx * dx + dy * dy
    if (r2 <= earthRadius2) {
        val earthZ = sqrt((earthRadius2 - r2).coerceAtLeast(0f))
        if (orbitZ <= earthZ) return
    }

    if (moonRadiusClip2 > 0f) {
        val mdx = x - moonCenterX
        val mdy = y - moonCenterY
        if (mdx * mdx + mdy * mdy <= moonRadiusClip2) return
    }

    val index = y * dstW + x
    dst[index] = alphaOver(color, dst[index])

    val a = (color ushr 24) and 0xFF
    val glowAlpha = (a * 0.42f).roundToInt().coerceIn(0, 255)
    if (glowAlpha == 0) return

    val glowColor = (glowAlpha shl 24) or (color and 0x00FFFFFF)

    fun blendGlowAt(px: Int, py: Int, dstIndex: Int) {
        val gx = px - earthCenter
        val gy = py - earthCenter
        val gr2 = gx * gx + gy * gy
        if (gr2 <= earthRadius2) {
            val earthZ = sqrt((earthRadius2 - gr2).coerceAtLeast(0f))
            if (orbitZ <= earthZ) return
        }
        if (moonRadiusClip2 > 0f) {
            val gmx = px - moonCenterX
            val gmy = py - moonCenterY
            if (gmx * gmx + gmy * gmy <= moonRadiusClip2) return
        }
        dst[dstIndex] = alphaOver(glowColor, dst[dstIndex])
    }

    if (x + 1 < dstW) blendGlowAt(x + 1, y, index + 1)
    if (x - 1 >= 0) blendGlowAt(x - 1, y, index - 1)
    if (y + 1 < dstH) blendGlowAt(x, y + 1, index + dstW)
    if (y - 1 >= 0) blendGlowAt(x, y - 1, index - dstW)
}

private fun drawStarfield(dst: IntArray, dstW: Int, dstH: Int, seed: Int) {
    val pixelCount = dstW * dstH
    val starCount = (pixelCount / 700).coerceIn(90, 2200)
    var state = seed xor (dstW shl 16) xor dstH

    repeat(starCount) {
        state = xorshift32(state)
        val x = ((state ushr 1) % dstW)
        state = xorshift32(state)
        val y = ((state ushr 1) % dstH)

        state = xorshift32(state)
        val t = ((state ushr 24) and 0xFF) / 255f
        val intensity = (32f + 223f * (t * t * t)).roundToInt().coerceIn(0, 255)

        state = xorshift32(state)
        val tint = (state ushr 29) and 0x7
        val r = (intensity + (tint - 3) * 4).coerceIn(0, 255)
        val g = (intensity + (tint - 3) * 2).coerceIn(0, 255)
        val b = (intensity + (tint - 3) * 5).coerceIn(0, 255)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val index = y * dstW + x
        if (dst[index] != 0xFF000000.toInt()) return@repeat
        dst[index] = color

        val sparkleChance = (state ushr 25) and 0x1F
        if (sparkleChance == 0) {
            stampStar(dst = dst, dstW = dstW, dstH = dstH, centerX = x, centerY = y, color = color)
        }
    }
}

private fun stampStar(dst: IntArray, dstW: Int, dstH: Int, centerX: Int, centerY: Int, color: Int) {
    val offsets = intArrayOf(-1, 0, 1)
    for (dy in offsets) {
        val y = centerY + dy
        if (y !in 0 until dstH) continue
        val row = y * dstW
        for (dx in offsets) {
            if (dx == 0 && dy == 0) continue
            val x = centerX + dx
            if (x !in 0 until dstW) continue
            val index = row + x
            if (dst[index] != 0xFF000000.toInt()) continue

            val alpha = if (dx == 0 || dy == 0) 0x88 else 0x66
            val tinted = (alpha shl 24) or (color and 0x00FFFFFF)
            dst[index] = alphaOver(tinted, dst[index])
        }
    }
}

private fun xorshift32(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}

private fun blitOver(dst: IntArray, dstW: Int, src: IntArray, srcW: Int, left: Int, top: Int) {
    val srcH = src.size / srcW
    val dstH = dst.size / dstW

    val x0 = left.coerceAtLeast(0)
    val y0 = top.coerceAtLeast(0)
    val x1 = (left + srcW).coerceAtMost(dstW)
    val y1 = (top + srcH).coerceAtMost(dstH)

    for (y in y0 until y1) {
        val srcY = y - top
        val dstRow = y * dstW
        val srcRow = srcY * srcW
        for (x in x0 until x1) {
            val srcColor = src[srcRow + (x - left)]
            val srcA = (srcColor ushr 24) and 0xFF
            if (srcA == 0) continue

            val dstIndex = dstRow + x
            val dstColor = dst[dstIndex]
            if (srcA == 255) {
                dst[dstIndex] = srcColor
                continue
            }

            val dstA = (dstColor ushr 24) and 0xFF
            val invA = 255 - srcA
            val outA = (srcA + (dstA * invA + 127) / 255).coerceIn(0, 255)

            val srcR = (srcColor ushr 16) and 0xFF
            val srcG = (srcColor ushr 8) and 0xFF
            val srcB = srcColor and 0xFF
            val dstR = (dstColor ushr 16) and 0xFF
            val dstG = (dstColor ushr 8) and 0xFF
            val dstB = dstColor and 0xFF

            val outR = (srcR * srcA + dstR * invA + 127) / 255
            val outG = (srcG * srcA + dstG * invA + 127) / 255
            val outB = (srcB * srcA + dstB * invA + 127) / 255

            dst[dstIndex] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }
}

private fun alphaOver(foreground: Int, background: Int): Int {
    val fgA = (foreground ushr 24) and 0xFF
    if (fgA == 255) return foreground
    if (fgA == 0) return background

    val bgA = (background ushr 24) and 0xFF
    val invA = 255 - fgA
    val outA = (fgA + (bgA * invA + 127) / 255).coerceIn(0, 255)

    val fgR = (foreground ushr 16) and 0xFF
    val fgG = (foreground ushr 8) and 0xFF
    val fgB = foreground and 0xFF
    val bgR = (background ushr 16) and 0xFF
    val bgG = (background ushr 8) and 0xFF
    val bgB = background and 0xFF

    val outR = (fgR * fgA + bgR * invA + 127) / 255
    val outG = (fgG * fgA + bgG * invA + 127) / 255
    val outB = (fgB * fgA + bgB * invA + 127) / 255

    return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
}

private fun powInt(base: Float, exponent: Int): Float {
    var result = 1f
    var powBase = base
    var exp = exponent
    while (exp > 0) {
        if ((exp and 1) == 1) result *= powBase
        powBase *= powBase
        exp = exp ushr 1
    }
    return result
}

private fun moonSunVisibility(
    moonCenterX: Float,
    moonCenterY: Float,
    moonCenterZ: Float,
    earthRadius: Float,
    moonRadius: Float,
    sunAzimuthDegrees: Float,
    sunElevationDegrees: Float,
): Float {
    val az = sunAzimuthDegrees * DEG_TO_RAD
    val el = sunElevationDegrees * DEG_TO_RAD
    val cosEl = cos(el)
    val sunX = sin(az) * cosEl
    val sunY = sin(el)
    val sunZ = cos(az) * cosEl

    val proj = moonCenterX * sunX + moonCenterY * sunY + moonCenterZ * sunZ
    if (proj > 0f) return 1f

    val r2 = moonCenterX * moonCenterX + moonCenterY * moonCenterY + moonCenterZ * moonCenterZ
    val d2 = (r2 - proj * proj).coerceAtLeast(0f)
    val d = sqrt(d2)

    val penumbra = earthRadius + moonRadius * 0.9f
    return smoothStep(earthRadius, penumbra, d)
}

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun drawMarkerOnSphere(
    sphereArgb: IntArray,
    sphereSizePx: Int,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    rotationDegrees: Float,
    tiltDegrees: Float,
) {
    val latRad = markerLatitudeDegrees.coerceIn(-90f, 90f) * DEG_TO_RAD
    val lonRad = markerLongitudeDegrees.coerceIn(-180f, 180f) * DEG_TO_RAD
    val cosLat = cos(latRad)

    val texX = sin(lonRad) * cosLat
    val texY = sin(latRad)
    val texZ = cos(lonRad) * cosLat

    val yawRad = rotationDegrees * DEG_TO_RAD
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = texX * cosYaw - texZ * sinYaw
    val z1 = texX * sinYaw + texZ * cosYaw

    val tiltRad = tiltDegrees * DEG_TO_RAD
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val x2 = x1 * cosTilt + texY * sinTilt
    val y2 = -x1 * sinTilt + texY * cosTilt

    if (z1 <= 0f) return

    val half = (sphereSizePx - 1) / 2f
    val centerX = half + x2 * half
    val centerY = half - y2 * half

    val markerRadiusPx = max(2f, sphereSizePx * 0.017f)
    val outlineRadiusPx = markerRadiusPx + 1.6f

    val outlineColor = 0xFFFFFFFF.toInt()
    val fillColor = 0xFFE53935.toInt()

    val minX = (centerX - outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val maxX = (centerX + outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val minY = (centerY - outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val maxY = (centerY + outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)

    val outlineR2 = outlineRadiusPx * outlineRadiusPx
    val fillR2 = markerRadiusPx * markerRadiusPx

    for (y in minY..maxY) {
        val dy = y - centerY
        val row = y * sphereSizePx
        for (x in minX..maxX) {
            val dstIndex = row + x
            if (((sphereArgb[dstIndex] ushr 24) and 0xFF) == 0) continue

            val dx = x - centerX
            val d2 = dx * dx + dy * dy
            when {
                d2 <= fillR2 -> sphereArgb[dstIndex] = fillColor
                d2 <= outlineR2 -> sphereArgb[dstIndex] = outlineColor
            }
        }
    }
}
