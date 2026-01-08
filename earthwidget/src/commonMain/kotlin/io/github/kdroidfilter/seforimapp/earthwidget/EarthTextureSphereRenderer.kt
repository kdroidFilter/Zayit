package io.github.kdroidfilter.seforimapp.earthwidget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.*

// ============================================================================
// PARALLELIZATION CONFIGURATION
// ============================================================================

/** Number of parallel chunks for rendering. Tuned for typical desktop CPUs. */
private const val PARALLEL_CHUNK_COUNT = 8

/** Minimum output size to use parallel rendering (smaller sizes have too much overhead). */
private const val MIN_SIZE_FOR_PARALLEL = 200

// ============================================================================
// SPHERE RENDERING
// ============================================================================

/**
 * Renders a textured sphere with Phong lighting using parallel processing.
 *
 * This is the core rendering function that projects a texture onto a sphere
 * and applies realistic lighting including ambient, diffuse, specular, and
 * atmospheric effects.
 *
 * Uses coroutine-based parallelization to distribute work across multiple CPU cores,
 * significantly improving render performance on multi-core systems.
 *
 * @param texture Source texture to map onto sphere.
 * @param outputSizePx Output image size (square).
 * @param rotationDegrees Y-axis rotation (longitude).
 * @param lightDegrees Sun azimuth angle.
 * @param tiltDegrees X-axis tilt (axial inclination).
 * @param ambient Base ambient light level.
 * @param diffuseStrength Diffuse lighting intensity.
 * @param specularStrength Specular highlight intensity.
 * @param specularExponent Specular highlight sharpness.
 * @param sunElevationDegrees Sun vertical angle.
 * @param viewDirX Camera direction X.
 * @param viewDirY Camera direction Y.
 * @param viewDirZ Camera direction Z.
 * @param upHintX Optional up vector hint X.
 * @param upHintY Optional up vector hint Y.
 * @param upHintZ Optional up vector hint Z.
 * @param sunVisibility Shadow occlusion factor (0-1).
 * @param atmosphereStrength Rim atmosphere glow intensity.
 * @param shadowAlphaStrength Shadow-based alpha blending.
 * @param outputBuffer Optional pre-allocated output buffer. If null, a new buffer is created.
 * @return ARGB pixel array of rendered sphere.
 */
internal suspend fun renderTexturedSphereArgb(
    texture: EarthTexture,
    outputSizePx: Int,
    rotationDegrees: Float,
    lightDegrees: Float,
    tiltDegrees: Float,
    ambient: Float = DEFAULT_AMBIENT,
    diffuseStrength: Float = DEFAULT_DIFFUSE_STRENGTH,
    specularStrength: Float = 0f,
    specularExponent: Int = 64,
    sunElevationDegrees: Float = 0f,
    viewDirX: Float = 0f,
    viewDirY: Float = 0f,
    viewDirZ: Float = 1f,
    upHintX: Float = 0f,
    upHintY: Float = 0f,
    upHintZ: Float = 0f,
    sunVisibility: Float = 1f,
    atmosphereStrength: Float = DEFAULT_ATMOSPHERE_STRENGTH,
    shadowAlphaStrength: Float = 0f,
    outputBuffer: IntArray? = null,
): IntArray {
    val expectedSize = outputSizePx * outputSizePx
    val output = if (outputBuffer != null && outputBuffer.size >= expectedSize) {
        outputBuffer
    } else {
        IntArray(expectedSize)
    }

    // Pre-compute all rendering parameters
    val params = SphereRenderParams(
        texture = texture,
        outputSizePx = outputSizePx,
        rotationDegrees = rotationDegrees,
        lightDegrees = lightDegrees,
        tiltDegrees = tiltDegrees,
        ambient = ambient,
        diffuseStrength = diffuseStrength,
        specularStrength = specularStrength,
        specularExponent = specularExponent,
        sunElevationDegrees = sunElevationDegrees,
        viewDirX = viewDirX,
        viewDirY = viewDirY,
        viewDirZ = viewDirZ,
        upHintX = upHintX,
        upHintY = upHintY,
        upHintZ = upHintZ,
        sunVisibility = sunVisibility,
        atmosphereStrength = atmosphereStrength,
        shadowAlphaStrength = shadowAlphaStrength,
    )

    // Use parallel rendering for larger images, sequential for smaller ones
    if (outputSizePx >= MIN_SIZE_FOR_PARALLEL) {
        renderSphereParallel(output, params)
    } else {
        renderSphereSequential(output, params)
    }

    return output
}

/**
 * Pre-computed rendering parameters to avoid redundant calculations across chunks.
 */
private class SphereRenderParams(
    val texture: EarthTexture,
    val outputSizePx: Int,
    rotationDegrees: Float,
    lightDegrees: Float,
    tiltDegrees: Float,
    val ambient: Float,
    val diffuseStrength: Float,
    val specularStrength: Float,
    val specularExponent: Int,
    sunElevationDegrees: Float,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
    upHintX: Float,
    upHintY: Float,
    upHintZ: Float,
    sunVisibility: Float,
    val atmosphereStrength: Float,
    val shadowAlphaStrength: Float,
) {
    // Pre-computed rotation and tilt
    val cosYaw: Float
    val sinYaw: Float
    val cosTilt: Float
    val sinTilt: Float

    // Pre-computed light direction
    val sunX: Float
    val sunY: Float
    val sunZ: Float

    // Camera frame
    val cameraFrame: CameraFrame

    // Specular computation
    val halfVector: HalfVector?
    val specEnabled: Boolean

    // Screen coordinate helpers
    val halfW: Float
    val halfH: Float
    val invHalfW: Float
    val invHalfH: Float
    val lightVisibility: Float

    // Texture references
    val texWidth: Int = texture.width
    val texHeight: Int = texture.height
    val tex: IntArray = texture.argb

    init {
        val rotationRad = rotationDegrees * DEG_TO_RAD_F
        val tiltRad = tiltDegrees * DEG_TO_RAD_F
        cosYaw = cos(rotationRad)
        sinYaw = sin(rotationRad)
        cosTilt = cos(tiltRad)
        sinTilt = sin(tiltRad)

        val sunAzimuthRad = lightDegrees * DEG_TO_RAD_F
        val sunElevationRad = sunElevationDegrees * DEG_TO_RAD_F
        val cosSunElevation = cos(sunElevationRad)
        sunX = sin(sunAzimuthRad) * cosSunElevation
        sunY = sin(sunElevationRad)
        sunZ = cos(sunAzimuthRad) * cosSunElevation

        cameraFrame = buildCameraFrame(viewDirX, viewDirY, viewDirZ, upHintX, upHintY, upHintZ)
        halfVector = computeHalfVector(sunX, sunY, sunZ, cameraFrame)
        specEnabled = specularStrength > 0f && halfVector != null && specularExponent > 0

        halfW = (outputSizePx - 1) / 2f
        halfH = (outputSizePx - 1) / 2f
        invHalfW = 1f / halfW
        invHalfH = 1f / halfH
        lightVisibility = sunVisibility.coerceIn(0f, 1f)
    }
}

/**
 * Renders the sphere using parallel coroutines, splitting work into horizontal chunks.
 */
private suspend fun renderSphereParallel(output: IntArray, params: SphereRenderParams) {
    val chunkSize = (params.outputSizePx + PARALLEL_CHUNK_COUNT - 1) / PARALLEL_CHUNK_COUNT

    coroutineScope {
        (0 until params.outputSizePx step chunkSize).map { startY ->
            async(Dispatchers.Default) {
                val endY = minOf(startY + chunkSize, params.outputSizePx)
                renderRowRange(output, params, startY, endY)
            }
        }.awaitAll()
    }
}

/**
 * Renders the sphere sequentially (for small images where parallelization overhead isn't worth it).
 */
private fun renderSphereSequential(output: IntArray, params: SphereRenderParams) {
    renderRowRange(output, params, 0, params.outputSizePx)
}

/**
 * Renders a range of rows [startY, endY) to the output buffer.
 * Each row is independent, making this safe for parallel execution.
 */
private fun renderRowRange(
    output: IntArray,
    params: SphereRenderParams,
    startY: Int,
    endY: Int,
) {
    val outputSizePx = params.outputSizePx
    val halfW = params.halfW
    val halfH = params.halfH
    val invHalfW = params.invHalfW
    val invHalfH = params.invHalfH
    val cameraFrame = params.cameraFrame
    val cosYaw = params.cosYaw
    val sinYaw = params.sinYaw
    val cosTilt = params.cosTilt
    val sinTilt = params.sinTilt
    val texWidth = params.texWidth
    val texHeight = params.texHeight
    val tex = params.tex
    val sunX = params.sunX
    val sunY = params.sunY
    val sunZ = params.sunZ
    val ambient = params.ambient
    val diffuseStrength = params.diffuseStrength
    val specularStrength = params.specularStrength
    val specularExponent = params.specularExponent
    val specEnabled = params.specEnabled
    val halfVector = params.halfVector
    val lightVisibility = params.lightVisibility
    val atmosphereStrength = params.atmosphereStrength
    val shadowAlphaStrength = params.shadowAlphaStrength

    for (y in startY until endY) {
        val ny = (halfH - y) * invHalfH
        val rowOffset = y * outputSizePx

        for (x in 0 until outputSizePx) {
            val nx = (x - halfW) * invHalfW
            val rr = nx * nx + ny * ny

            // Skip pixels outside sphere
            if (rr > 1f) {
                output[rowOffset + x] = TRANSPARENT_BLACK
                continue
            }

            // Compute sphere normal at this point
            val nz = sqrt(1f - rr)

            // Transform to world space
            val worldX = cameraFrame.rightX * nx + cameraFrame.upX * ny + cameraFrame.forwardX * nz
            val worldY = cameraFrame.rightY * nx + cameraFrame.upY * ny + cameraFrame.forwardY * nz
            val worldZ = cameraFrame.rightZ * nx + cameraFrame.upZ * ny + cameraFrame.forwardZ * nz

            // Apply rotation and tilt to get texture coordinates
            val rotatedX = worldX * cosTilt - worldY * sinTilt
            val rotatedY = worldX * sinTilt + worldY * cosTilt
            val texX = rotatedX * cosYaw + worldZ * sinYaw
            val texZ = -rotatedX * sinYaw + worldZ * cosYaw

            // Convert to spherical coordinates
            val longitude = atan2(texX, texZ)
            val latitude = asin(rotatedY.coerceIn(-1f, 1f))

            // Map to texture UV coordinates
            var u = (longitude / TWO_PI_F) + 0.5f
            u -= floor(u)
            val v = (0.5f - (latitude / PI.toFloat())).coerceIn(0f, 1f)

            // Sample texture with bilinear filtering for smoother results
            val texColor = sampleTextureBilinear(tex, texWidth, texHeight, u, v)

            // Compute lighting
            val pixelColor = computePixelLighting(
                texColor = texColor,
                worldX = worldX, worldY = worldY, worldZ = worldZ,
                sunX = sunX, sunY = sunY, sunZ = sunZ,
                nz = nz, rr = rr,
                ambient = ambient,
                diffuseStrength = diffuseStrength,
                specularStrength = specularStrength,
                specularExponent = specularExponent,
                specEnabled = specEnabled,
                halfVector = halfVector,
                lightVisibility = lightVisibility,
                atmosphereStrength = atmosphereStrength,
                shadowAlphaStrength = shadowAlphaStrength,
            )

            output[rowOffset + x] = pixelColor
        }
    }
}

/**
 * Camera coordinate frame for view transformations.
 */
private data class CameraFrame(
    val forwardX: Float, val forwardY: Float, val forwardZ: Float,
    val rightX: Float, val rightY: Float, val rightZ: Float,
    val upX: Float, val upY: Float, val upZ: Float,
)

/**
 * Builds an orthonormal camera coordinate frame from view direction.
 */
private fun buildCameraFrame(
    viewDirX: Float, viewDirY: Float, viewDirZ: Float,
    upHintX: Float, upHintY: Float, upHintZ: Float,
): CameraFrame {
    // Normalize forward direction
    var forwardX = viewDirX
    var forwardY = viewDirY
    var forwardZ = viewDirZ
    val forwardLen = sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ)
    if (forwardLen > EPSILON) {
        forwardX /= forwardLen
        forwardY /= forwardLen
        forwardZ /= forwardLen
    } else {
        forwardX = 0f
        forwardY = 0f
        forwardZ = 1f
    }

    // Compute right vector from up hint or default
    var rightX: Float
    var rightY: Float
    var rightZ: Float
    val upHintLen = sqrt(upHintX * upHintX + upHintY * upHintY + upHintZ * upHintZ)

    if (upHintLen > EPSILON) {
        val upHX = upHintX / upHintLen
        val upHY = upHintY / upHintLen
        val upHZ = upHintZ / upHintLen
        rightX = upHY * forwardZ - upHZ * forwardY
        rightY = upHZ * forwardX - upHX * forwardZ
        rightZ = upHX * forwardY - upHY * forwardX
    } else {
        rightX = forwardZ
        rightY = 0f
        rightZ = -forwardX
    }

    // Handle degenerate case
    var rightLen = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
    if (rightLen < EPSILON) {
        rightX = 0f
        rightY = forwardZ
        rightZ = -forwardY
        rightLen = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
    }
    if (rightLen > EPSILON) {
        rightX /= rightLen
        rightY /= rightLen
        rightZ /= rightLen
    }

    // Compute up from forward and right
    val upX = forwardY * rightZ - forwardZ * rightY
    val upY = forwardZ * rightX - forwardX * rightZ
    val upZ = forwardX * rightY - forwardY * rightX

    return CameraFrame(forwardX, forwardY, forwardZ, rightX, rightY, rightZ, upX, upY, upZ)
}

/**
 * Half vector for Blinn-Phong specular, or null if not applicable.
 */
private data class HalfVector(val x: Float, val y: Float, val z: Float)

/**
 * Computes the Blinn-Phong half vector between light and view directions.
 */
private fun computeHalfVector(
    sunX: Float, sunY: Float, sunZ: Float,
    cameraFrame: CameraFrame,
): HalfVector? {
    var halfX = sunX + cameraFrame.forwardX
    var halfY = sunY + cameraFrame.forwardY
    var halfZ = sunZ + cameraFrame.forwardZ
    val halfLen = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)

    if (halfLen <= EPSILON) return null

    halfX /= halfLen
    halfY /= halfLen
    halfZ /= halfLen

    return HalfVector(halfX, halfY, halfZ)
}

/**
 * Computes the final pixel color with lighting applied.
 */
private fun computePixelLighting(
    texColor: Int,
    worldX: Float, worldY: Float, worldZ: Float,
    sunX: Float, sunY: Float, sunZ: Float,
    nz: Float, rr: Float,
    ambient: Float,
    diffuseStrength: Float,
    specularStrength: Float,
    specularExponent: Int,
    specEnabled: Boolean,
    halfVector: HalfVector?,
    lightVisibility: Float,
    atmosphereStrength: Float,
    shadowAlphaStrength: Float,
): Int {
    // Diffuse lighting (Lambertian)
    val dot = worldX * sunX + worldY * sunY + worldZ * sunZ
    val shadowMask = smoothStep(SHADOW_EDGE_START, SHADOW_EDGE_END, dot) * lightVisibility
    val diffuse = max(dot, 0f) * lightVisibility

    // Combined ambient (darkened in shadow)
    val ambientShade = ambient * (0.25f + 0.75f * shadowMask)
    val baseShade = (ambientShade + diffuseStrength * diffuse).coerceIn(0f, 1f)

    // View-dependent shading (subtle)
    val viewShade = 0.75f + 0.25f * nz
    val shade = (baseShade * viewShade).coerceIn(0f, 1f)

    // Atmospheric rim glow
    val rim = (1f - nz).coerceIn(0f, 1f)
    val atmosphere = (rim * rim * atmosphereStrength * shadowMask).coerceIn(0f, atmosphereStrength)

    // Extract and linearize texture color (gamma decode)
    val a = (texColor ushr 24) and 0xFF
    val r = (texColor ushr 16) and 0xFF
    val g = (texColor ushr 8) and 0xFF
    val b = texColor and 0xFF

    val rLin = (r / 255f).let { it * it }
    val gLin = (g / 255f).let { it * it }
    val bLin = (b / 255f).let { it * it }

    // Specular highlights (primarily on water/oceans)
    val spec = if (specEnabled && diffuse > 0f && halfVector != null) {
        val dotH = (worldX * halfVector.x + worldY * halfVector.y + worldZ * halfVector.z)
            .coerceAtLeast(0f)
        val baseSpec = specularStrength * powInt(dotH, specularExponent) * lightVisibility
        // Ocean detection: blue channel significantly higher than red/green
        val oceanMask = ((b - max(r, g)).coerceAtLeast(0) / 255f).let { it * it }
        baseSpec * (0.12f + 0.88f * oceanMask)
    } else {
        0f
    }

    // Apply shading in linear space
    val shadedRLin = (rLin * shade + spec).coerceIn(0f, 1f)
    val shadedGLin = (gLin * shade + spec).coerceIn(0f, 1f)
    val shadedBLin = (bLin * shade + spec).coerceIn(0f, 1f)

    // Gamma encode back to sRGB and add atmosphere
    val sr = (sqrt(shadedRLin) * 255f).roundToInt().coerceIn(0, 255)
    val sg = (sqrt(shadedGLin) * 255f).roundToInt().coerceIn(0, 255)
    val sb = ((sqrt(shadedBLin) * 255f) + (255f * atmosphere)).roundToInt().coerceIn(0, 255)

    // Edge feathering for smooth sphere boundary
    val dist = sqrt(rr)
    val alpha = ((1f - dist) / EDGE_FEATHER_WIDTH).coerceIn(0f, 1f)

    // Shadow-based alpha (for Moon phases)
    val shadowAlpha = if (shadowAlphaStrength <= 0f) {
        1f
    } else {
        val strength = shadowAlphaStrength.coerceIn(0f, 1f)
        (1f - strength) + strength * shadowMask
    }

    val outA = (a * alpha * shadowAlpha).toInt().coerceIn(0, 255)

    return (outA shl 24) or (sr shl 16) or (sg shl 8) or sb
}

// ============================================================================
// COMPOSITE SCENE RENDERING
// ============================================================================

/**
 * Renders Earth with Moon in orbit.
 *
 * Creates a complete scene with Earth, Moon, optional starfield background,
 * and orbital path visualization.
 *
 * @param earthTexture Earth surface texture.
 * @param moonTexture Moon surface texture.
 * @param outputSizePx Output image size (square).
 * @param earthRotationDegrees Earth rotation angle.
 * @param lightDegrees Sun azimuth for Earth.
 * @param sunElevationDegrees Sun elevation.
 * @param earthTiltDegrees Earth axial tilt.
 * @param moonOrbitDegrees Moon position on orbit.
 * @param markerLatitudeDegrees Marker latitude on Earth.
 * @param markerLongitudeDegrees Marker longitude on Earth.
 * @param moonRotationDegrees Moon rotation.
 * @param showBackgroundStars Whether to draw starfield.
 * @param showOrbitPath Whether to draw orbit path.
 * @param bufferPool Optional buffer pool for reusing intermediate buffers.
 * @param outputBuffer Optional pre-allocated output buffer.
 * @param starfieldCache Optional cache for pre-rendered starfields.
 * @return ARGB pixel array of complete scene.
 */
internal suspend fun renderEarthWithMoonArgb(
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
    moonRotationDegrees: Float = moonOrbitDegrees + earthRotationDegrees,
    showBackgroundStars: Boolean = true,
    showOrbitPath: Boolean = true,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
    bufferPool: PixelBufferPool? = null,
    outputBuffer: IntArray? = null,
    starfieldCache: StarfieldCache? = null,
): IntArray {
    val outputSize = outputSizePx * outputSizePx
    val out = if (outputBuffer != null && outputBuffer.size >= outputSize) {
        outputBuffer
    } else {
        IntArray(outputSize)
    }

    // Fill background with cached starfield or solid black
    if (showBackgroundStars && starfieldCache != null) {
        val cachedStarfield = starfieldCache.getOrCreate(outputSizePx, outputSizePx)
        cachedStarfield.copyInto(out)
    } else if (showBackgroundStars) {
        out.fill(OPAQUE_BLACK)
        drawStarfield(dst = out, dstW = outputSizePx, dstH = outputSizePx, seed = STARFIELD_SEED)
    } else {
        out.fill(OPAQUE_BLACK)
    }

    if (earthTexture == null) return out

    val geometry = computeSceneGeometry(outputSizePx, earthSizeFraction)
    val moonLayout = computeMoonScreenLayout(geometry, moonOrbitDegrees)

    // Acquire Earth buffer from pool or create new
    val earthSize = geometry.earthSizePx * geometry.earthSizePx
    val earthBuffer = bufferPool?.acquire(earthSize)

    try {
        // Render Earth
        val earth = renderTexturedSphereArgb(
            texture = earthTexture,
            outputSizePx = geometry.earthSizePx,
            rotationDegrees = earthRotationDegrees,
            lightDegrees = lightDegrees,
            tiltDegrees = earthTiltDegrees,
            specularStrength = EARTH_SPECULAR_STRENGTH,
            specularExponent = EARTH_SPECULAR_EXPONENT,
            sunElevationDegrees = sunElevationDegrees,
            viewDirZ = 1f,
            outputBuffer = earthBuffer,
        )

        // Draw marker on Earth
        drawMarkerOnSphere(
            sphereArgb = earth,
            sphereSizePx = geometry.earthSizePx,
            markerLatitudeDegrees = markerLatitudeDegrees,
            markerLongitudeDegrees = markerLongitudeDegrees,
            rotationDegrees = earthRotationDegrees,
            tiltDegrees = earthTiltDegrees,
        )

        // Composite Earth onto scene
        blitOver(dst = out, dstW = outputSizePx, src = earth, srcW = geometry.earthSizePx, left = geometry.earthLeft, top = geometry.earthTop)

        // Draw orbit path
        if (showOrbitPath) {
            drawOrbitPath(
                dst = out,
                dstW = outputSizePx,
                dstH = outputSizePx,
                center = geometry.sceneHalf,
                earthRadiusPx = geometry.earthRadiusPx,
                orbitRadius = geometry.orbitRadius,
                cosInc = geometry.cosInc,
                sinInc = geometry.sinInc,
                cosView = geometry.cosView,
                sinView = geometry.sinView,
                moonCenterX = moonLayout.moonCenterX,
                moonCenterY = moonLayout.moonCenterY,
                moonRadiusPx = if (moonTexture != null) moonLayout.moonRadiusPx else 0f,
                cameraZ = geometry.cameraZ,
            )
        }

        if (moonTexture == null) return out

        // Calculate Moon view direction (from Moon to camera)
        val moonViewDirX = -moonLayout.moonOrbit.x
        val moonViewDirY = -moonLayout.moonOrbit.yCam
        val moonViewDirZ = geometry.cameraZ - moonLayout.moonOrbit.zCam

        // Acquire Moon buffer from pool or create new
        val moonSize = moonLayout.moonSizePx * moonLayout.moonSizePx
        val moonBuffer = bufferPool?.acquire(moonSize)

        try {
            // Render Moon with same sun lighting as Earth (no phase-based shadows)
            val moon = renderTexturedSphereArgb(
                texture = moonTexture,
                outputSizePx = moonLayout.moonSizePx,
                rotationDegrees = moonRotationDegrees,
                lightDegrees = lightDegrees,
                tiltDegrees = 0f,
                ambient = MOON_AMBIENT,
                diffuseStrength = MOON_DIFFUSE_STRENGTH,
                sunElevationDegrees = sunElevationDegrees,
                viewDirX = moonViewDirX,
                viewDirY = moonViewDirY,
                viewDirZ = moonViewDirZ,
                sunVisibility = 1f,
                atmosphereStrength = 0f,
                shadowAlphaStrength = 0f,
                outputBuffer = moonBuffer,
            )

            // Composite Moon with depth sorting
            compositeMoonWithDepth(
                out = out,
                outputSizePx = outputSizePx,
                earth = earth,
                earthSizePx = geometry.earthSizePx,
                earthLeft = geometry.earthLeft,
                earthTop = geometry.earthTop,
                earthRadiusPx = geometry.earthRadiusPx,
                moon = moon,
                moonSizePx = moonLayout.moonSizePx,
                moonLeft = moonLayout.moonLeft,
                moonTop = moonLayout.moonTop,
                moonRadiusPx = moonLayout.moonRadiusPx,
                moonRadiusWorldPx = geometry.moonRadiusWorldPx,
                moonZCam = moonLayout.moonOrbit.zCam,
                moonScale = moonLayout.moonScale,
            )
        } finally {
            // Release Moon buffer back to pool
            @Suppress("UNNECESSARY_SAFE_CALL")
            moonBuffer?.let { bufferPool?.release(it) }
        }
    } finally {
        // Release Earth buffer back to pool
        @Suppress("UNNECESSARY_SAFE_CALL")
        earthBuffer?.let { bufferPool?.release(it) }
    }

    return out
}

/**
 * Computes Moon lighting from ephemeris or phase angle.
 *
 * The illumination fraction comes from the phase angle (or ephemeris) and the viewing direction.
 * Callers that need the crescent oriented to the local sky should use the phase helper that
 * accepts a sun direction hint.
 */
private fun computeMoonLighting(
    julianDay: Double?,
    moonPhaseAngleDegrees: Float?,
    moonViewDirX: Float,
    moonViewDirY: Float,
    moonViewDirZ: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
): LightDirection? = when {
    moonPhaseAngleDegrees != null -> {
        // Use phase-only calculation - Moon illumination is independent of local sun position
        computeMoonLightFromPhaseInternal(
            phaseAngleDegrees = moonPhaseAngleDegrees,
            viewDirX = moonViewDirX,
            viewDirY = moonViewDirY,
            viewDirZ = moonViewDirZ,
        )
    }
    julianDay != null -> computeGeometricMoonIllumination(
        julianDay = julianDay,
        viewDirX = moonViewDirX,
        viewDirY = moonViewDirY,
        viewDirZ = moonViewDirZ,
    )
    else -> null
}

/**
 * Composites Moon onto scene with depth-aware blending against Earth.
 */
private fun compositeMoonWithDepth(
    out: IntArray,
    outputSizePx: Int,
    earth: IntArray,
    earthSizePx: Int,
    earthLeft: Int,
    earthTop: Int,
    earthRadiusPx: Float,
    moon: IntArray,
    moonSizePx: Int,
    moonLeft: Int,
    moonTop: Int,
    moonRadiusPx: Float,
    moonRadiusWorldPx: Float,
    moonZCam: Float,
    moonScale: Float,
) {
    val x0Moon = moonLeft.coerceAtLeast(0)
    val y0Moon = moonTop.coerceAtLeast(0)
    val x1Moon = (moonLeft + moonSizePx).coerceAtMost(outputSizePx)
    val y1Moon = (moonTop + moonSizePx).coerceAtMost(outputSizePx)
    val invMoonScale = if (moonScale > EPSILON) 1f / moonScale else 1f

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

            // Depth comparison
            val earthDx = earthX - earthRadiusPx
            val moonDxScreen = moonX - moonRadiusPx
            val moonDxWorld = moonDxScreen * invMoonScale
            val earthR2 = (earthDx * earthDx + earthDy * earthDy) / (earthRadiusPx * earthRadiusPx)
            val moonR2 = (moonDxWorld * moonDxWorld + moonDyWorld * moonDyWorld) /
                    (moonRadiusWorldPx * moonRadiusWorldPx)
            val earthZ = sqrt(max(0f, 1f - earthR2)) * earthRadiusPx
            val moonZ = sqrt(max(0f, 1f - moonR2)) * moonRadiusWorldPx

            val moonDepth = moonZCam + moonZ
            out[dstIndex] = if (moonDepth > earthZ) {
                alphaOver(moonColor, earthColor)
            } else {
                alphaOver(earthColor, moonColor)
            }
        }
    }
}

/**
 * Renders the Moon as seen from a marker position on Earth.
 *
 * @param moonTexture Moon surface texture.
 * @param outputSizePx Output image size.
 * @param lightDegrees Sun azimuth.
 * @param sunElevationDegrees Sun elevation.
 * @param earthRotationDegrees Earth rotation.
 * @param earthTiltDegrees Earth axial tilt.
 * @param moonOrbitDegrees Moon position on orbit.
 * @param markerLatitudeDegrees Observer latitude.
 * @param markerLongitudeDegrees Observer longitude.
 * @param moonRotationDegrees Moon rotation.
 * @param showBackgroundStars Whether to draw starfield.
 * @param moonLightDegrees Override for Moon light direction.
 * @param moonSunElevationDegrees Override for Moon sun elevation.
 * @param moonPhaseAngleDegrees Moon phase for lighting.
 * @param julianDay Julian Day for ephemeris.
 * @param bufferPool Optional buffer pool for reusing intermediate buffers.
 * @param outputBuffer Optional pre-allocated output buffer.
 * @param starfieldCache Optional cache for pre-rendered starfields.
 * @return ARGB pixel array of Moon view.
 */
internal suspend fun renderMoonFromMarkerArgb(
    moonTexture: EarthTexture?,
    outputSizePx: Int,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    moonRotationDegrees: Float = 0f,
    showBackgroundStars: Boolean = true,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
    bufferPool: PixelBufferPool? = null,
    outputBuffer: IntArray? = null,
    starfieldCache: StarfieldCache? = null,
): IntArray {
    val outputSize = outputSizePx * outputSizePx
    val out = if (outputBuffer != null && outputBuffer.size >= outputSize) {
        outputBuffer
    } else {
        IntArray(outputSize)
    }

    // Fill background with cached starfield or solid black
    if (showBackgroundStars && starfieldCache != null) {
        val cachedStarfield = starfieldCache.getOrCreate(outputSizePx, outputSizePx)
        cachedStarfield.copyInto(out)
    } else if (showBackgroundStars) {
        out.fill(OPAQUE_BLACK)
        drawStarfield(dst = out, dstW = outputSizePx, dstH = outputSizePx, seed = STARFIELD_SEED)
    } else {
        out.fill(OPAQUE_BLACK)
    }

    if (moonTexture == null) return out

    val geometry = computeSceneGeometry(outputSizePx, earthSizeFraction)
    val moonLayout = computeMoonScreenLayout(geometry, moonOrbitDegrees)

    // Calculate observer position on Earth surface
    val observerPosition = calculateObserverPosition(
        markerLatitudeDegrees = markerLatitudeDegrees,
        markerLongitudeDegrees = markerLongitudeDegrees,
        earthRotationDegrees = earthRotationDegrees,
        earthTiltDegrees = earthTiltDegrees,
        earthRadiusPx = geometry.earthRadiusPx,
    )

    // View direction from observer to Moon
    var viewDirX = observerPosition.x - moonLayout.moonOrbit.x
    var viewDirY = observerPosition.y - moonLayout.moonOrbit.yCam
    var viewDirZ = observerPosition.z - moonLayout.moonOrbit.zCam

    // Up hint is radial direction at observer position
    val upLen = sqrt(observerPosition.x * observerPosition.x +
            observerPosition.y * observerPosition.y +
            observerPosition.z * observerPosition.z)
    val upHintX = if (upLen > EPSILON) observerPosition.x / upLen else 0f
    val upHintY = if (upLen > EPSILON) observerPosition.y / upLen else 1f
    val upHintZ = if (upLen > EPSILON) observerPosition.z / upLen else 0f

    // Replace view direction with topocentric Moon direction when ephemeris is available
    if (julianDay != null) {
        val horizontal = computeMoonHorizontalPosition(
            julianDay = julianDay,
            latitudeDeg = markerLatitudeDegrees.toDouble(),
            longitudeDeg = markerLongitudeDegrees.toDouble(),
        )
        val moonDirWorld = horizontalToWorld(
            latitudeDeg = markerLatitudeDegrees.toDouble(),
            longitudeDeg = markerLongitudeDegrees.toDouble(),
            azimuthFromNorthDeg = horizontal.azimuthFromNorthDeg,
            elevationDeg = horizontal.elevationDeg,
            earthRotationDegrees = earthRotationDegrees,
            earthTiltDegrees = earthTiltDegrees,
        )
        viewDirX = -moonDirWorld.x
        viewDirY = -moonDirWorld.y
        viewDirZ = -moonDirWorld.z
    }

    val sunDirectionHint = sunVectorFromAngles(moonLightDegrees, moonSunElevationDegrees)

    // Calculate Moon lighting based on phase angle, observer position, and the sun direction
    // The observer's up direction and sun hint drive the crescent orientation
    val moonLighting = if (moonPhaseAngleDegrees != null) {
        computeMoonLightFromPhaseWithObserverUp(
            phaseAngleDegrees = moonPhaseAngleDegrees,
            viewDirX = viewDirX,
            viewDirY = viewDirY,
            viewDirZ = viewDirZ,
            observerUpX = upHintX,
            observerUpY = upHintY,
            observerUpZ = upHintZ,
            sunDirectionHint = sunDirectionHint,
        )
    } else {
        computeMoonLighting(
            julianDay = julianDay,
            moonPhaseAngleDegrees = null,
            moonViewDirX = viewDirX,
            moonViewDirY = viewDirY,
            moonViewDirZ = viewDirZ,
            lightDegrees = lightDegrees,
            sunElevationDegrees = sunElevationDegrees,
        )
    }
    val moonLightDegreesResolved = moonLighting?.lightDegrees ?: moonLightDegrees
    val moonSunElevationDegreesResolved = moonLighting?.sunElevationDegrees ?: moonSunElevationDegrees

    val sunVisibility = moonSunVisibility(
        moonCenterX = moonLayout.moonOrbit.x,
        moonCenterY = moonLayout.moonOrbit.yCam,
        moonCenterZ = moonLayout.moonOrbit.zCam,
        moonRadius = geometry.moonRadiusWorldPx,
        sunAzimuthDegrees = moonLightDegreesResolved,
        sunElevationDegrees = moonSunElevationDegreesResolved,
    )

    // Acquire Moon buffer from pool or create new
    val moonBuffer = bufferPool?.acquire(outputSize)

    try {
        // Render Moon
        val moon = renderTexturedSphereArgb(
            texture = moonTexture,
            outputSizePx = outputSizePx,
            rotationDegrees = moonRotationDegrees,
            lightDegrees = moonLightDegreesResolved,
            tiltDegrees = 0f,
            ambient = MOON_AMBIENT,
            diffuseStrength = MOON_DIFFUSE_STRENGTH,
            sunElevationDegrees = moonSunElevationDegreesResolved,
            viewDirX = viewDirX,
            viewDirY = viewDirY,
            viewDirZ = viewDirZ,
            upHintX = upHintX,
            upHintY = upHintY,
            upHintZ = upHintZ,
            sunVisibility = sunVisibility,
            atmosphereStrength = 0f,
            shadowAlphaStrength = 1f,
            outputBuffer = moonBuffer,
        )
        drawGhostMoonOutline(argb = moon, sizePx = outputSizePx)

        blitOver(dst = out, dstW = outputSizePx, src = moon, srcW = outputSizePx, left = 0, top = 0)
    } finally {
        // Release Moon buffer back to pool
        @Suppress("UNNECESSARY_SAFE_CALL")
        moonBuffer?.let { bufferPool?.release(it) }
    }

    return out
}

private fun drawGhostMoonOutline(argb: IntArray, sizePx: Int) {
    if (sizePx <= 2) return
    val center = (sizePx - 1) / 2f
    val thickness = max(1.1f, sizePx * 0.0045f)
    val radius = center - thickness - 0.5f
    if (radius <= 0f) return

    val innerRadius = (radius - thickness).coerceAtLeast(0f)
    val outerRadius = radius + thickness
    val inner2 = innerRadius * innerRadius
    val outer2 = outerRadius * outerRadius
    val outlineRgb = GHOST_MOON_OUTLINE_RGB and 0x00FFFFFF
    val outlineColorStrong = (GHOST_MOON_OUTLINE_ALPHA shl 24) or outlineRgb
    val outlineColorSoft = ((GHOST_MOON_OUTLINE_ALPHA * 0.22f).roundToInt().coerceIn(0, 255) shl 24) or outlineRgb

    for (y in 0 until sizePx) {
        val dy = y - center
        val dy2 = dy * dy
        val row = y * sizePx
        for (x in 0 until sizePx) {
            val dx = x - center
            val d2 = dx * dx + dy2
            if (d2 in inner2..outer2) {
                val idx = row + x
                val bgA = (argb[idx] ushr 24) and 0xFF
                val outlineColor = if (bgA == 0) outlineColorStrong else outlineColorSoft
                argb[idx] = alphaOver(outlineColor, argb[idx])
            }
        }
    }
}

/**
 * Observer position on Earth surface in world coordinates.
 */
private data class ObserverPosition(val x: Float, val y: Float, val z: Float)

/**
 * Calculates observer position on Earth surface given lat/lon and Earth orientation.
 */
private fun calculateObserverPosition(
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
    earthRadiusPx: Float,
): ObserverPosition {
    val unit = latLonToUnitVector(markerLatitudeDegrees, markerLongitudeDegrees)

    // Apply Earth rotation (yaw)
    val earthYawRad = earthRotationDegrees * DEG_TO_RAD_F
    val cosYaw = cos(earthYawRad)
    val sinYaw = sin(earthYawRad)
    val xRot = unit.x * cosYaw - unit.z * sinYaw
    val zRot = unit.x * sinYaw + unit.z * cosYaw

    // Apply Earth tilt (pitch)
    val tiltRad = earthTiltDegrees * DEG_TO_RAD_F
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)

    return ObserverPosition(
        x = (xRot * cosTilt + unit.y * sinTilt) * earthRadiusPx,
        y = (-xRot * sinTilt + unit.y * cosTilt) * earthRadiusPx,
        z = zRot * earthRadiusPx,
    )
}

// ============================================================================
// ORBIT PATH RENDERING
// ============================================================================

/**
 * Draws the Moon's orbital path with perspective projection.
 */
private fun drawOrbitPath(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    center: Float,
    earthRadiusPx: Float,
    orbitRadius: Float,
    cosInc: Float,
    sinInc: Float,
    cosView: Float,
    sinView: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusPx: Float,
    cameraZ: Float,
) {
    if (orbitRadius <= 0f) return

    val steps = (orbitRadius * 5.2f).roundToInt().coerceIn(420, 1600)
    val earthRadius2 = earthRadiusPx * earthRadiusPx
    val moonRadiusClip2 = -1f

    var prevX = Int.MIN_VALUE
    var prevY = Int.MIN_VALUE
    var prevZ = 0f

    for (i in 0..steps) {
        val t = (i.toFloat() / steps) * TWO_PI_F
        val x0 = cos(t) * orbitRadius
        val z0 = sin(t) * orbitRadius

        // Apply orbital inclination
        val yInc = -z0 * sinInc
        val zInc = z0 * cosInc

        // Apply view pitch
        val yCam = yInc * cosView - zInc * sinView
        val zCam = yInc * sinView + zInc * cosView

        val orbitScale = perspectiveScale(cameraZ, zCam)
        val sx = (center + x0 * orbitScale).roundToInt()
        val sy = (center - yCam * orbitScale).roundToInt()

        if (prevX != Int.MIN_VALUE) {
            val avgZ = (prevZ + zCam) * 0.5f
            val alpha = if (avgZ >= 0f) ORBIT_ALPHA_FRONT else ORBIT_ALPHA_BACK
            val color = (alpha shl 24) or ORBIT_COLOR_RGB

            drawOrbitLineSegment(
                dst = dst, dstW = dstW, dstH = dstH,
                x0 = prevX, y0 = prevY, x1 = sx, y1 = sy,
                color = color, orbitZ = avgZ,
                earthCenter = center, earthRadiusPx = earthRadiusPx, earthRadius2 = earthRadius2,
                moonCenterX = moonCenterX, moonCenterY = moonCenterY, moonRadiusClip2 = moonRadiusClip2,
            )
        }

        prevX = sx
        prevY = sy
        prevZ = zCam
    }
}

/**
 * Draws a line segment of the orbit path using Bresenham's algorithm.
 */
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
            dst = dst, dstW = dstW, dstH = dstH,
            x = x, y = y, color = color, orbitZ = orbitZ,
            earthCenter = earthCenter, earthRadiusPx = earthRadiusPx, earthRadius2 = earthRadius2,
            moonCenterX = moonCenterX, moonCenterY = moonCenterY, moonRadiusClip2 = moonRadiusClip2,
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

/**
 * Plots a single orbit pixel with depth testing and glow effect.
 */
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

    // Depth test against Earth
    val dx = x - earthCenter
    val dy = y - earthCenter
    val r2 = dx * dx + dy * dy
    if (r2 <= earthRadius2) {
        val earthZ = sqrt((earthRadius2 - r2).coerceAtLeast(0f))
        if (orbitZ <= earthZ) return
    }

    // Clip against Moon
    if (moonRadiusClip2 > 0f) {
        val mdx = x - moonCenterX
        val mdy = y - moonCenterY
        if (mdx * mdx + mdy * mdy <= moonRadiusClip2) return
    }

    // Draw main pixel
    val index = y * dstW + x
    dst[index] = alphaOver(color, dst[index])

    // Draw glow
    val a = (color ushr 24) and 0xFF
    val glowAlpha = (a * ORBIT_GLOW_INTENSITY).roundToInt().coerceIn(0, 255)
    if (glowAlpha == 0) return

    val glowColor = (glowAlpha shl 24) or (color and 0x00FFFFFF)

    // Helper to blend glow at adjacent pixels
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

// ============================================================================
// STARFIELD RENDERING
// ============================================================================

/**
 * Draws a procedurally generated starfield background.
 *
 * Uses a seeded PRNG for deterministic star placement.
 */
private fun drawStarfield(dst: IntArray, dstW: Int, dstH: Int, seed: Int) {
    val pixelCount = dstW * dstH
    val starCount = (pixelCount / PIXELS_PER_STAR).coerceIn(MIN_STAR_COUNT, MAX_STAR_COUNT)
    var state = seed xor (dstW shl 16) xor dstH

    repeat(starCount) {
        // Random position
        state = xorshift32(state)
        val x = (state ushr 1) % dstW
        state = xorshift32(state)
        val y = (state ushr 1) % dstH

        // Random brightness with cubic falloff (more dim stars)
        state = xorshift32(state)
        val t = ((state ushr 24) and 0xFF) / 255f
        val intensity = (32f + 223f * (t * t * t)).roundToInt().coerceIn(0, 255)

        // Subtle color tint
        state = xorshift32(state)
        val tint = (state ushr 29) and 0x7
        val r = (intensity + (tint - 3) * 4).coerceIn(0, 255)
        val g = (intensity + (tint - 3) * 2).coerceIn(0, 255)
        val b = (intensity + (tint - 3) * 5).coerceIn(0, 255)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val index = y * dstW + x
        if (dst[index] != OPAQUE_BLACK) return@repeat
        dst[index] = color

        // Occasional sparkle for bright stars
        val sparkleChance = (state ushr 25) and 0x1F
        if (sparkleChance == 0) {
            stampStar(dst = dst, dstW = dstW, dstH = dstH, centerX = x, centerY = y, color = color)
        }
    }
}

/**
 * Stamps a star sparkle pattern around a bright star.
 */
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
            if (dst[index] != OPAQUE_BLACK) continue

            // Cross pattern is brighter than diagonal
            val alpha = if (dx == 0 || dy == 0) 0x88 else 0x66
            val tinted = (alpha shl 24) or (color and 0x00FFFFFF)
            dst[index] = alphaOver(tinted, dst[index])
        }
    }
}

/**
 * xorshift32 pseudo-random number generator.
 *
 * Fast, deterministic PRNG suitable for procedural generation.
 */
private fun xorshift32(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}

// ============================================================================
// BILINEAR TEXTURE SAMPLING
// ============================================================================

/**
 * Samples a texture using bilinear interpolation for smoother results.
 *
 * Bilinear filtering interpolates between 4 neighboring texels based on
 * the fractional UV coordinates, reducing aliasing artifacts compared
 * to point sampling.
 *
 * @param tex Texture pixel data in ARGB format.
 * @param texWidth Texture width in pixels.
 * @param texHeight Texture height in pixels.
 * @param u Horizontal texture coordinate (0-1, wraps).
 * @param v Vertical texture coordinate (0-1, clamped).
 * @return Interpolated ARGB color.
 */
private fun sampleTextureBilinear(
    tex: IntArray,
    texWidth: Int,
    texHeight: Int,
    u: Float,
    v: Float,
): Int {
    // Convert UV to pixel coordinates
    val x = u * texWidth
    val y = v * texHeight

    // Get integer pixel coordinates
    val x0 = x.toInt()
    val y0 = y.toInt().coerceIn(0, texHeight - 1)

    // Handle horizontal wrapping (for seamless longitude)
    val x0Wrapped = if (x0 < 0) texWidth + (x0 % texWidth) else x0 % texWidth
    val x1Wrapped = (x0Wrapped + 1) % texWidth

    // Clamp vertical (no wrapping at poles)
    val y1 = (y0 + 1).coerceAtMost(texHeight - 1)

    // Fractional parts for interpolation
    val fx = x - x.toInt()
    val fy = y - y.toInt()

    // Sample 4 neighboring texels
    val c00 = tex[y0 * texWidth + x0Wrapped]
    val c10 = tex[y0 * texWidth + x1Wrapped]
    val c01 = tex[y1 * texWidth + x0Wrapped]
    val c11 = tex[y1 * texWidth + x1Wrapped]

    // Bilinear interpolation
    val top = lerpColorLinear(c00, c10, fx)
    val bottom = lerpColorLinear(c01, c11, fx)
    return lerpColorLinear(top, bottom, fy)
}

/**
 * Linearly interpolates between two ARGB colors in linear color space.
 *
 * Performs gamma-correct interpolation by:
 * 1. Converting sRGB to linear
 * 2. Interpolating in linear space
 * 3. Converting back to sRGB
 *
 * This produces more visually accurate results than naive interpolation.
 *
 * @param c0 First color (ARGB).
 * @param c1 Second color (ARGB).
 * @param t Interpolation factor (0 = c0, 1 = c1).
 * @return Interpolated ARGB color.
 */
private fun lerpColorLinear(c0: Int, c1: Int, t: Float): Int {
    if (t <= 0f) return c0
    if (t >= 1f) return c1

    // Extract channels
    val a0 = (c0 ushr 24) and 0xFF
    val r0 = (c0 ushr 16) and 0xFF
    val g0 = (c0 ushr 8) and 0xFF
    val b0 = c0 and 0xFF

    val a1 = (c1 ushr 24) and 0xFF
    val r1 = (c1 ushr 16) and 0xFF
    val g1 = (c1 ushr 8) and 0xFF
    val b1 = c1 and 0xFF

    // Convert to linear space (approximate gamma 2.2 with square)
    val r0Lin = (r0 / 255f).let { it * it }
    val g0Lin = (g0 / 255f).let { it * it }
    val b0Lin = (b0 / 255f).let { it * it }

    val r1Lin = (r1 / 255f).let { it * it }
    val g1Lin = (g1 / 255f).let { it * it }
    val b1Lin = (b1 / 255f).let { it * it }

    // Interpolate in linear space
    val invT = 1f - t
    val aLerp = a0 * invT + a1 * t
    val rLerp = r0Lin * invT + r1Lin * t
    val gLerp = g0Lin * invT + g1Lin * t
    val bLerp = b0Lin * invT + b1Lin * t

    // Convert back to sRGB (gamma encode with sqrt)
    val a = aLerp.roundToInt().coerceIn(0, 255)
    val r = (sqrt(rLerp) * 255f).roundToInt().coerceIn(0, 255)
    val g = (sqrt(gLerp) * 255f).roundToInt().coerceIn(0, 255)
    val b = (sqrt(bLerp) * 255f).roundToInt().coerceIn(0, 255)

    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Fast linear interpolation for colors (without gamma correction).
 *
 * Use this for performance-critical paths where slight color
 * inaccuracy is acceptable.
 *
 * @param c0 First color (ARGB).
 * @param c1 Second color (ARGB).
 * @param t Interpolation factor (0 = c0, 1 = c1).
 * @return Interpolated ARGB color.
 */
@Suppress("unused")
private fun lerpColorFast(c0: Int, c1: Int, t: Float): Int {
    if (t <= 0f) return c0
    if (t >= 1f) return c1

    val invT = 1f - t
    val a = ((c0 ushr 24) and 0xFF) * invT + ((c1 ushr 24) and 0xFF) * t
    val r = ((c0 ushr 16) and 0xFF) * invT + ((c1 ushr 16) and 0xFF) * t
    val g = ((c0 ushr 8) and 0xFF) * invT + ((c1 ushr 8) and 0xFF) * t
    val b = (c0 and 0xFF) * invT + (c1 and 0xFF) * t

    return (a.roundToInt() shl 24) or
            (r.roundToInt() shl 16) or
            (g.roundToInt() shl 8) or
            b.roundToInt()
}

// ============================================================================
// MARKER RENDERING
// ============================================================================

/**
 * Draws a location marker on a rendered sphere.
 *
 * @param sphereArgb Sphere pixel data to modify.
 * @param sphereSizePx Sphere size in pixels.
 * @param markerLatitudeDegrees Marker latitude.
 * @param markerLongitudeDegrees Marker longitude.
 * @param rotationDegrees Sphere rotation.
 * @param tiltDegrees Sphere tilt.
 */
private fun drawMarkerOnSphere(
    sphereArgb: IntArray,
    sphereSizePx: Int,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    rotationDegrees: Float,
    tiltDegrees: Float,
) {
    // Convert marker coordinates to 3D position
    val unit = latLonToUnitVector(markerLatitudeDegrees, markerLongitudeDegrees)

    // Apply rotation
    val yawRad = rotationDegrees * DEG_TO_RAD
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = unit.x * cosYaw - unit.z * sinYaw
    val z1 = unit.x * sinYaw + unit.z * cosYaw

    // Apply tilt
    val tiltRad = tiltDegrees * DEG_TO_RAD
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val x2 = x1 * cosTilt + unit.y * sinTilt
    val y2 = -x1 * sinTilt + unit.y * cosTilt

    // Only draw if marker is on visible hemisphere (facing camera)
    if (z1 <= 0f) return

    // Calculate marker sizes
    val markerRadiusPx = max(MIN_MARKER_RADIUS_PX, sphereSizePx * MARKER_RADIUS_FRACTION)
    val outlineRadiusPx = markerRadiusPx + MARKER_OUTLINE_EXTRA_PX

    // Project to screen coordinates
    val half = (sphereSizePx - 1) / 2f
    val centerX = half + x2 * half
    val centerY = half - y2 * half

    // Bounds for iteration
    val minX = (centerX - outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val maxX = (centerX + outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val minY = (centerY - outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val maxY = (centerY + outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)

    val outlineR2 = outlineRadiusPx * outlineRadiusPx
    val fillR2 = markerRadiusPx * markerRadiusPx

    // Draw marker
    for (y in minY..maxY) {
        val dy = y - centerY
        val row = y * sphereSizePx
        for (x in minX..maxX) {
            val dstIndex = row + x
            // Only draw on visible sphere surface
            if (((sphereArgb[dstIndex] ushr 24) and 0xFF) == 0) continue

            val dx = x - centerX
            val d2 = dx * dx + dy * dy
            when {
                d2 <= fillR2 -> sphereArgb[dstIndex] = MARKER_FILL_COLOR
                d2 <= outlineR2 -> sphereArgb[dstIndex] = MARKER_OUTLINE_COLOR
            }
        }
    }
}

// ============================================================================
// SHADOW CALCULATIONS
// ============================================================================

/**
 * Computes visibility of sun from Moon's position (for eclipse shadows).
 *
 * Returns 0 when Moon is in Earth's shadow (lunar eclipse),
 * 1 when fully illuminated, with smooth penumbra transition.
 *
 * @param moonCenterX Moon X position.
 * @param moonCenterY Moon Y position.
 * @param moonCenterZ Moon Z position.
 * @param moonRadius Moon radius.
 * @param sunAzimuthDegrees Sun azimuth.
 * @param sunElevationDegrees Sun elevation.
 * @return Visibility factor (0-1).
 */
private fun moonSunVisibility(
    moonCenterX: Float,
    moonCenterY: Float,
    moonCenterZ: Float,
    moonRadius: Float,
    sunAzimuthDegrees: Float,
    sunElevationDegrees: Float,
): Float {
    val sunDir = sunVectorFromAngles(sunAzimuthDegrees, sunElevationDegrees)

    // Project Moon position onto sun direction
    val proj = moonCenterX * sunDir.x + moonCenterY * sunDir.y + moonCenterZ * sunDir.z

    // If Moon is on sun side of Earth, fully lit
    if (proj > 0f) return 1f

    // Distance from Moon to Earth-Sun axis
    val r2 = moonCenterX * moonCenterX + moonCenterY * moonCenterY + moonCenterZ * moonCenterZ
    val moonDistance = sqrt(r2)
    val d2 = (r2 - proj * proj).coerceAtLeast(0f)
    val d = sqrt(d2)

    // Scale Earth's shadow using realistic umbra/penumbra size at lunar distance
    val umbraRadius = moonDistance * EARTH_UMBRA_DISTANCE_RATIO
    val penumbraRadius = moonDistance * EARTH_PENUMBRA_DISTANCE_RATIO
    val softPenumbra = (penumbraRadius + moonRadius * 0.12f).coerceAtLeast(umbraRadius)

    // Smooth transition through penumbra
    return smoothStep(umbraRadius, softPenumbra, d)
}

// ============================================================================
// ALPHA BLENDING
// ============================================================================

/**
 * Copies source image onto destination with alpha blending.
 */
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
            if (srcA == 255) {
                dst[dstIndex] = srcColor
                continue
            }

            dst[dstIndex] = alphaOver(srcColor, dst[dstIndex])
        }
    }
}

/**
 * Porter-Duff "over" alpha compositing operation.
 *
 * Blends foreground color over background color.
 */
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

// ============================================================================
// MATH UTILITIES
// ============================================================================

// ============================================================================
// ORBIT SCREEN COORDINATES (UI OVERLAYS)
// ============================================================================

/**
 * Screen-space orbit position in the same pixel coordinate system as the rendered image.
 *
 * @property x X coordinate in [0, outputSizePx).
 * @property y Y coordinate in [0, outputSizePx).
 * @property zCam Depth in camera space (positive = towards camera).
 */
internal data class OrbitScreenPosition(
    val x: Float,
    val y: Float,
    val zCam: Float,
)

/**
 * Computes the Moon orbit position projected into screen space, matching [drawOrbitPath].
 *
 * Intended for UI overlays (e.g., labels) that need to align with the rendered orbit path.
 */
internal fun computeOrbitScreenPosition(
    outputSizePx: Int,
    orbitDegrees: Float,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
): OrbitScreenPosition {
    val geometry = computeSceneGeometry(outputSizePx, earthSizeFraction)
    val orbit = transformMoonOrbitPosition(orbitDegrees, geometry.orbitRadius, geometry.viewPitchRad)
    val orbitScale = perspectiveScale(geometry.cameraZ, orbit.zCam)

    return OrbitScreenPosition(
        x = geometry.sceneHalf + orbit.x * orbitScale,
        y = geometry.sceneHalf - orbit.yCam * orbitScale,
        zCam = orbit.zCam,
    )
}
