package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPU-based renderer that replaces [EarthWidgetRenderer] using SkSL shaders.
 *
 * The sphere rendering (the most expensive part) runs entirely on the GPU.
 * Orbit paths use Skia Path rendering. Starfield is pre-rendered once as a texture.
 * The marker is drawn as a simple circle overlay.
 *
 * Produces [ImageBitmap] with the same API as the CPU renderer for drop-in replacement.
 */
internal class EarthShaderRenderer private constructor(
    private val pipeline: EarthShaderPipeline,
) {
    private var cachedStarfield: Image? = null
    private var cachedStarfieldSize: Int = 0

    // Texture shader cache — textures never change, so we cache the GPU shader
    private var cachedEarthShader: Shader? = null
    private var cachedEarthTexId: Int = 0
    private var cachedMoonShader: Shader? = null
    private var cachedMoonTexId: Int = 0

    // Pre-allocated Skia objects to avoid per-frame allocation
    private val shaderPaint = Paint()
    private val markerFillPaint =
        Paint().apply {
            isAntiAlias = true
            color = MARKER_FILL_COLOR
        }
    private val markerOutlinePaint =
        Paint().apply {
            isAntiAlias = true
            color = MARKER_OUTLINE_COLOR
        }
    private val starfieldPaint = Paint()
    private val orbitPath = Path()
    private val klPath = Path()

    // Pre-allocated arrays for camera frame and half vector (avoid per-frame FloatArray allocation)
    private val frameBuffer = FloatArray(9)
    private val halfBuffer = FloatArray(3)

    /**
     * Renders the Earth-Moon composite scene on GPU.
     */
    fun renderScene(
        state: EarthRenderState,
        textures: EarthWidgetTextures,
    ): ImageBitmap {
        val size = state.renderSizePx
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(size, size, true)
        val canvas = Canvas(bitmap)

        // Background: starfield or black
        if (state.showBackgroundStars) {
            drawStarfieldBackground(canvas, size)
        }

        if (textures.earth == null) {
            return bitmap.asComposeImageBitmap()
        }

        val geometry = computeSceneGeometry(size, state.earthSizeFraction)
        val moonLayout = computeMoonScreenLayout(geometry, state.moonOrbitDegrees)

        val earthTexShader = getOrCreateEarthShader(textures.earth)

        // Draw orbit path behind Earth (zCam < 0)
        if (state.showOrbitPath) {
            drawOrbitPathSkia(
                canvas = canvas,
                geometry = geometry,
                moonLayout = moonLayout,
                behind = true,
                kiddushLevanaStartDegrees = state.kiddushLevanaStartDegrees,
                kiddushLevanaEndDegrees = state.kiddushLevanaEndDegrees,
                kiddushLevanaColorRgb = state.kiddushLevanaColorRgb,
            )
        }

        // Draw Moon behind Earth if it's behind
        val moonBehind = moonLayout.moonOrbit.zCam < 0f
        if (textures.moon != null && moonBehind) {
            drawMoonSphere(canvas, state, geometry, moonLayout, textures.moon)
        }

        // Draw Earth sphere
        drawEarthSphere(canvas, state, geometry, earthTexShader, textures.earth)

        // Draw marker on Earth
        drawMarkerOverlay(
            canvas = canvas,
            geometry = geometry,
            markerLatitudeDegrees = state.markerLatitudeDegrees,
            markerLongitudeDegrees = state.markerLongitudeDegrees,
            rotationDegrees = state.earthRotationDegrees,
            tiltDegrees = state.earthTiltDegrees,
        )

        // Draw orbit path in front of Earth (zCam >= 0)
        if (state.showOrbitPath) {
            drawOrbitPathSkia(
                canvas = canvas,
                geometry = geometry,
                moonLayout = moonLayout,
                behind = false,
                kiddushLevanaStartDegrees = state.kiddushLevanaStartDegrees,
                kiddushLevanaEndDegrees = state.kiddushLevanaEndDegrees,
                kiddushLevanaColorRgb = state.kiddushLevanaColorRgb,
            )
        }

        // Draw Moon in front of Earth
        if (textures.moon != null && !moonBehind) {
            drawMoonSphere(canvas, state, geometry, moonLayout, textures.moon)
        }

        canvas.close()
        return bitmap.asComposeImageBitmap()
    }

    /**
     * Renders the Moon as seen from a marker position on Earth.
     */
    fun renderMoonFromMarker(
        state: MoonFromMarkerRenderState,
        moonTexture: EarthTexture?,
    ): ImageBitmap {
        val size = state.renderSizePx
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(size, size, true)
        val canvas = Canvas(bitmap)

        if (state.showBackgroundStars) {
            drawStarfieldBackground(canvas, size)
        }

        if (moonTexture == null) {
            canvas.close()
            return bitmap.asComposeImageBitmap()
        }

        val geometry = computeSceneGeometry(size, state.earthSizeFraction)
        val moonLayout = computeMoonScreenLayout(geometry, state.moonOrbitDegrees)

        // Calculate observer position
        val unit = latLonToUnitVector(state.markerLatitudeDegrees, state.markerLongitudeDegrees)
        val earthYawRad = state.earthRotationDegrees * DEG_TO_RAD_F
        val cosYaw = cos(earthYawRad)
        val sinYaw = sin(earthYawRad)
        val xRot = unit.x * cosYaw - unit.z * sinYaw
        val zRot = unit.x * sinYaw + unit.z * cosYaw
        val tiltRad = state.earthTiltDegrees * DEG_TO_RAD_F
        val cosTilt = cos(tiltRad)
        val sinTilt = sin(tiltRad)
        val obsX = (xRot * cosTilt + unit.y * sinTilt) * geometry.earthRadiusPx
        val obsY = (-xRot * sinTilt + unit.y * cosTilt) * geometry.earthRadiusPx
        val obsZ = zRot * geometry.earthRadiusPx

        // View direction from observer to Moon
        var viewDirX = obsX - moonLayout.moonOrbit.x
        var viewDirY = obsY - moonLayout.moonOrbit.yCam
        var viewDirZ = obsZ - moonLayout.moonOrbit.zCam

        // Up hint is radial direction at observer position
        val upLen = sqrt(obsX * obsX + obsY * obsY + obsZ * obsZ)
        val upHintX = if (upLen > EPSILON) obsX / upLen else 0f
        val upHintY = if (upLen > EPSILON) obsY / upLen else 1f
        val upHintZ = if (upLen > EPSILON) obsZ / upLen else 0f

        // Replace with topocentric direction when ephemeris available
        if (state.julianDay != null) {
            val horizontal =
                computeMoonHorizontalPosition(
                    julianDay = state.julianDay,
                    latitudeDeg = state.markerLatitudeDegrees.toDouble(),
                    longitudeDeg = state.markerLongitudeDegrees.toDouble(),
                )
            val moonDirWorld =
                horizontalToWorld(
                    latitudeDeg = state.markerLatitudeDegrees.toDouble(),
                    longitudeDeg = state.markerLongitudeDegrees.toDouble(),
                    azimuthFromNorthDeg = horizontal.azimuthFromNorthDeg,
                    elevationDeg = horizontal.elevationDeg,
                    earthRotationDegrees = state.earthRotationDegrees,
                    earthTiltDegrees = state.earthTiltDegrees,
                )
            viewDirX = -moonDirWorld.x
            viewDirY = -moonDirWorld.y
            viewDirZ = -moonDirWorld.z
        }

        val sunHint = sunVectorFromAngles(state.moonLightDegrees, state.moonSunElevationDegrees)

        // Compute Moon lighting
        val moonLighting =
            if (state.moonPhaseAngleDegrees != null) {
                computeMoonLightFromPhaseWithObserverUp(
                    phaseAngleDegrees = state.moonPhaseAngleDegrees,
                    viewDirX = viewDirX,
                    viewDirY = viewDirY,
                    viewDirZ = viewDirZ,
                    observerUpX = upHintX,
                    observerUpY = upHintY,
                    observerUpZ = upHintZ,
                    sunDirectionHint = sunHint,
                )
            } else {
                null
            }
        val resolvedLightDeg = moonLighting?.lightDegrees ?: state.moonLightDegrees
        val resolvedSunElev = moonLighting?.sunElevationDegrees ?: state.moonSunElevationDegrees

        val sunVis =
            moonSunVisibility(
                moonCenterX = moonLayout.moonOrbit.x,
                moonCenterY = moonLayout.moonOrbit.yCam,
                moonCenterZ = moonLayout.moonOrbit.zCam,
                moonRadius = geometry.moonRadiusWorldPx,
                sunAzimuthDegrees = resolvedLightDeg,
                sunElevationDegrees = resolvedSunElev,
            )

        // Build camera frame for this view direction
        val vLen = sqrt(viewDirX * viewDirX + viewDirY * viewDirY + viewDirZ * viewDirZ)
        val fwdX = if (vLen > EPSILON) viewDirX / vLen else 0f
        val fwdY = if (vLen > EPSILON) viewDirY / vLen else 0f
        val fwdZ = if (vLen > EPSILON) viewDirZ / vLen else 1f

        val moonTexShader = getOrCreateMoonShader(moonTexture)
        val frame = buildCameraFrameValues(fwdX, fwdY, fwdZ, upHintX, upHintY, upHintZ)
        val sunDir = sunVectorFromAngles(resolvedLightDeg, resolvedSunElev)
        val half = computeHalfVectorValues(sunDir.x, sunDir.y, sunDir.z, frame)

        val moonRotRad = 0f * DEG_TO_RAD_F
        val shader =
            pipeline.buildSphereShader(
                resolution = size.toFloat(),
                cosYaw = cos(moonRotRad),
                sinYaw = sin(moonRotRad),
                cosTilt = 1f,
                sinTilt = 0f,
                sunX = sunDir.x,
                sunY = sunDir.y,
                sunZ = sunDir.z,
                ambient = MOON_AMBIENT,
                diffuseStrength = MOON_DIFFUSE_STRENGTH,
                specularStrength = 0f,
                specularExponent = 64,
                atmosphereStrength = 0f,
                shadowAlphaStrength = 1f,
                lightVisibility = sunVis,
                camRightX = frame[0],
                camRightY = frame[1],
                camRightZ = frame[2],
                camUpX = frame[3],
                camUpY = frame[4],
                camUpZ = frame[5],
                camForwardX = frame[6],
                camForwardY = frame[7],
                camForwardZ = frame[8],
                halfVecX = half[0],
                halfVecY = half[1],
                halfVecZ = half[2],
                specEnabled = 0,
                texture = moonTexShader,
                texWidth = moonTexture.width.toFloat(),
                texHeight = moonTexture.height.toFloat(),
            )

        shaderPaint.shader = shader
        canvas.drawRect(Rect.makeWH(size.toFloat(), size.toFloat()), shaderPaint)

        // Draw ghost outline
        drawGhostOutline(canvas, size)

        canvas.close()
        return bitmap.asComposeImageBitmap()
    }

    // =========================================================================
    // PRIVATE DRAWING METHODS
    // =========================================================================

    private fun drawStarfieldBackground(
        canvas: Canvas,
        size: Int,
    ) {
        if (cachedStarfield == null || cachedStarfieldSize != size) {
            cachedStarfield?.close()
            val starBitmap = Bitmap()
            starBitmap.allocN32Pixels(size, size, false)
            val pixels = IntArray(size * size)
            pixels.fill(OPAQUE_BLACK)
            drawStarfield(dst = pixels, dstW = size, dstH = size, seed = STARFIELD_SEED)
            starBitmap.installPixels(
                ImageInfo.makeN32(size, size, ColorAlphaType.OPAQUE),
                intArrayToBytes(pixels),
                size * 4,
            )
            cachedStarfield = Image.makeFromBitmap(starBitmap)
            cachedStarfieldSize = size
            starBitmap.close()
        }
        canvas.drawImage(cachedStarfield!!, 0f, 0f, starfieldPaint)
    }

    private fun drawEarthSphere(
        canvas: Canvas,
        state: EarthRenderState,
        geometry: SceneGeometry,
        earthTexShader: Shader,
        earthTexture: EarthTexture,
    ) {
        val rotRad = state.earthRotationDegrees * DEG_TO_RAD_F
        val tiltRad = state.earthTiltDegrees * DEG_TO_RAD_F
        val sunDir = sunVectorFromAngles(state.lightDegrees, state.sunElevationDegrees)

        // Default camera: looking along +Z with Y up
        val frame = buildCameraFrameValues(0f, 0f, 1f, 0f, 0f, 0f)
        val half = computeHalfVectorValues(sunDir.x, sunDir.y, sunDir.z, frame)

        val shader =
            pipeline.buildSphereShader(
                resolution = geometry.earthSizePx.toFloat(),
                cosYaw = cos(rotRad),
                sinYaw = sin(rotRad),
                cosTilt = cos(tiltRad),
                sinTilt = sin(tiltRad),
                sunX = sunDir.x,
                sunY = sunDir.y,
                sunZ = sunDir.z,
                ambient = DEFAULT_AMBIENT,
                diffuseStrength = DEFAULT_DIFFUSE_STRENGTH,
                specularStrength = EARTH_SPECULAR_STRENGTH,
                specularExponent = EARTH_SPECULAR_EXPONENT,
                atmosphereStrength = DEFAULT_ATMOSPHERE_STRENGTH,
                shadowAlphaStrength = 0f,
                lightVisibility = 1f,
                camRightX = frame[0],
                camRightY = frame[1],
                camRightZ = frame[2],
                camUpX = frame[3],
                camUpY = frame[4],
                camUpZ = frame[5],
                camForwardX = frame[6],
                camForwardY = frame[7],
                camForwardZ = frame[8],
                halfVecX = half[0],
                halfVecY = half[1],
                halfVecZ = half[2],
                specEnabled = 1,
                texture = earthTexShader,
                texWidth = earthTexture.width.toFloat(),
                texHeight = earthTexture.height.toFloat(),
            )

        shaderPaint.shader = shader
        canvas.save()
        canvas.translate(geometry.earthLeft.toFloat(), geometry.earthTop.toFloat())
        canvas.drawRect(
            Rect.makeWH(geometry.earthSizePx.toFloat(), geometry.earthSizePx.toFloat()),
            shaderPaint,
        )
        canvas.restore()
    }

    private fun drawMoonSphere(
        canvas: Canvas,
        state: EarthRenderState,
        geometry: SceneGeometry,
        moonLayout: MoonScreenLayout,
        moonTexture: EarthTexture,
    ) {
        val moonRotRad = (state.moonOrbitDegrees + state.earthRotationDegrees) * DEG_TO_RAD_F
        val moonViewDirX = -moonLayout.moonOrbit.x
        val moonViewDirY = -moonLayout.moonOrbit.yCam
        val moonViewDirZ = geometry.cameraZ - moonLayout.moonOrbit.zCam

        val vLen = sqrt(moonViewDirX * moonViewDirX + moonViewDirY * moonViewDirY + moonViewDirZ * moonViewDirZ)
        val fwdX = if (vLen > EPSILON) moonViewDirX / vLen else 0f
        val fwdY = if (vLen > EPSILON) moonViewDirY / vLen else 0f
        val fwdZ = if (vLen > EPSILON) moonViewDirZ / vLen else 1f

        val frame = buildCameraFrameValues(fwdX, fwdY, fwdZ, 0f, 0f, 0f)
        val sunDir = sunVectorFromAngles(state.lightDegrees, state.sunElevationDegrees)
        val half = computeHalfVectorValues(sunDir.x, sunDir.y, sunDir.z, frame)
        val moonTexShader = getOrCreateMoonShader(moonTexture)

        val shader =
            pipeline.buildSphereShader(
                resolution = moonLayout.moonSizePx.toFloat(),
                cosYaw = cos(moonRotRad),
                sinYaw = sin(moonRotRad),
                cosTilt = 1f,
                sinTilt = 0f,
                sunX = sunDir.x,
                sunY = sunDir.y,
                sunZ = sunDir.z,
                ambient = MOON_AMBIENT,
                diffuseStrength = MOON_DIFFUSE_STRENGTH,
                specularStrength = 0f,
                specularExponent = 64,
                atmosphereStrength = 0f,
                shadowAlphaStrength = 0f,
                lightVisibility = 1f,
                camRightX = frame[0],
                camRightY = frame[1],
                camRightZ = frame[2],
                camUpX = frame[3],
                camUpY = frame[4],
                camUpZ = frame[5],
                camForwardX = frame[6],
                camForwardY = frame[7],
                camForwardZ = frame[8],
                halfVecX = half[0],
                halfVecY = half[1],
                halfVecZ = half[2],
                specEnabled = 0,
                texture = moonTexShader,
                texWidth = moonTexture.width.toFloat(),
                texHeight = moonTexture.height.toFloat(),
            )

        shaderPaint.shader = shader
        canvas.save()
        canvas.translate(moonLayout.moonLeft.toFloat(), moonLayout.moonTop.toFloat())
        canvas.drawRect(
            Rect.makeWH(moonLayout.moonSizePx.toFloat(), moonLayout.moonSizePx.toFloat()),
            shaderPaint,
        )
        canvas.restore()
    }

    private fun drawOrbitPathSkia(
        canvas: Canvas,
        geometry: SceneGeometry,
        moonLayout: MoonScreenLayout,
        behind: Boolean,
        kiddushLevanaStartDegrees: Float?,
        kiddushLevanaEndDegrees: Float?,
        kiddushLevanaColorRgb: Int,
    ) {
        if (geometry.orbitRadius <= 0f) return

        val steps = (geometry.orbitRadius * 5.2f).roundToInt().coerceIn(420, 1600)
        val hasKL = kiddushLevanaStartDegrees != null && kiddushLevanaEndDegrees != null

        orbitPath.reset()
        klPath.reset()
        var orbitStarted = false
        var klStarted = false

        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * TWO_PI_F
            val x0 = cos(t) * geometry.orbitRadius
            val z0 = sin(t) * geometry.orbitRadius

            val yInc = -z0 * geometry.sinInc
            val zInc = z0 * geometry.cosInc
            val yCam = yInc * geometry.cosView - zInc * geometry.sinView
            val zCam = yInc * geometry.sinView + zInc * geometry.cosView

            // Filter by depth
            val isBehind = zCam < 0f
            if (isBehind != behind) {
                orbitStarted = false
                klStarted = false
                continue
            }

            val orbitScale = perspectiveScale(geometry.cameraZ, zCam)
            val sx = geometry.sceneHalf + x0 * orbitScale
            val sy = geometry.sceneHalf - yCam * orbitScale

            if (!orbitStarted) {
                orbitPath.moveTo(sx, sy)
                orbitStarted = true
            } else {
                orbitPath.lineTo(sx, sy)
            }

            // Kiddush Levana arc
            if (hasKL) {
                val deg = (t * 180f / PI.toFloat()) % 360f
                val inKL = isAngleInRange(deg, kiddushLevanaStartDegrees!!, kiddushLevanaEndDegrees!!)
                if (inKL) {
                    if (!klStarted) {
                        klPath.moveTo(sx, sy)
                        klStarted = true
                    } else {
                        klPath.lineTo(sx, sy)
                    }
                } else {
                    klStarted = false
                }
            }
        }

        // Draw orbit line
        val alpha = if (behind) ORBIT_ALPHA_BACK else ORBIT_ALPHA_FRONT
        shaderPaint.apply {
            shader = null
            isAntiAlias = true
            color = (alpha shl 24) or ORBIT_COLOR_RGB
            mode = org.jetbrains.skia.PaintMode.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawPath(orbitPath, shaderPaint)

        // Draw KL arc
        if (hasKL && !klPath.isEmpty) {
            val klAlpha = if (behind) KIDDUSH_LEVANA_ALPHA_BACK else KIDDUSH_LEVANA_ALPHA_FRONT
            shaderPaint.apply {
                color = (klAlpha shl 24) or kiddushLevanaColorRgb
                strokeWidth = 2.5f
            }
            canvas.drawPath(klPath, shaderPaint)
        }
        shaderPaint.mode = org.jetbrains.skia.PaintMode.FILL
    }

    private fun drawMarkerOverlay(
        canvas: Canvas,
        geometry: SceneGeometry,
        markerLatitudeDegrees: Float,
        markerLongitudeDegrees: Float,
        rotationDegrees: Float,
        tiltDegrees: Float,
    ) {
        val unit = latLonToUnitVector(markerLatitudeDegrees, markerLongitudeDegrees)
        val yawRad = rotationDegrees * DEG_TO_RAD
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val x1 = (unit.x * cosYaw - unit.z * sinYaw).toFloat()
        val z1 = (unit.x * sinYaw + unit.z * cosYaw).toFloat()
        val tiltRad = tiltDegrees * DEG_TO_RAD
        val cosTilt = cos(tiltRad).toFloat()
        val sinTilt = sin(tiltRad).toFloat()
        val x2 = x1 * cosTilt + unit.y * sinTilt
        val y2 = -x1 * sinTilt + unit.y * cosTilt

        // Only visible hemisphere
        if (z1 <= 0f) return

        val half = (geometry.earthSizePx - 1) / 2f
        val cx = geometry.earthLeft + half + x2 * half
        val cy = geometry.earthTop + half - y2 * half
        val markerR = max(MIN_MARKER_RADIUS_PX, geometry.earthSizePx * MARKER_RADIUS_FRACTION)
        val outlineR = markerR + MARKER_OUTLINE_EXTRA_PX

        canvas.drawCircle(cx, cy, outlineR, markerOutlinePaint)
        canvas.drawCircle(cx, cy, markerR, markerFillPaint)
    }

    private fun drawGhostOutline(
        canvas: Canvas,
        sizePx: Int,
    ) {
        if (sizePx <= 2) return
        val center = (sizePx - 1) / 2f
        val thickness = max(1.1f, sizePx * 0.0045f)
        val radius = center - thickness - 0.5f
        if (radius <= 0f) return

        shaderPaint.apply {
            shader = null
            isAntiAlias = true
            color = (GHOST_MOON_OUTLINE_ALPHA shl 24) or (GHOST_MOON_OUTLINE_RGB and 0x00FFFFFF)
            mode = org.jetbrains.skia.PaintMode.STROKE
            strokeWidth = thickness * 2f
        }
        canvas.drawCircle(center, center, radius, shaderPaint)
        shaderPaint.mode = org.jetbrains.skia.PaintMode.FILL
    }

    // =========================================================================
    // TEXTURE & MATH HELPERS
    // =========================================================================

    private fun getOrCreateEarthShader(texture: EarthTexture): Shader {
        val id = System.identityHashCode(texture.argb)
        if (cachedEarthShader != null && cachedEarthTexId == id) return cachedEarthShader!!
        cachedEarthShader = buildTextureShader(texture)
        cachedEarthTexId = id
        return cachedEarthShader!!
    }

    private fun getOrCreateMoonShader(texture: EarthTexture): Shader {
        val id = System.identityHashCode(texture.argb)
        if (cachedMoonShader != null && cachedMoonTexId == id) return cachedMoonShader!!
        cachedMoonShader = buildTextureShader(texture)
        cachedMoonTexId = id
        return cachedMoonShader!!
    }

    private fun buildTextureShader(texture: EarthTexture): Shader {
        val bmp = Bitmap()
        bmp.allocN32Pixels(texture.width, texture.height, false)
        bmp.installPixels(
            ImageInfo.makeN32(texture.width, texture.height, ColorAlphaType.UNPREMUL),
            intArrayToBytes(texture.argb),
            texture.width * 4,
        )
        val image = Image.makeFromBitmap(bmp)
        bmp.close()
        return image.makeShader(
            FilterTileMode.REPEAT,
            FilterTileMode.CLAMP,
            SamplingMode.LINEAR,
        )
    }

    /**
     * Builds a camera frame (right, up, forward) as a float array [rx,ry,rz, ux,uy,uz, fx,fy,fz].
     */
    private fun buildCameraFrameValues(
        viewDirX: Float,
        viewDirY: Float,
        viewDirZ: Float,
        upHintX: Float,
        upHintY: Float,
        upHintZ: Float,
    ): FloatArray {
        val result = frameBuffer
        // Normalize forward
        val fLen = sqrt(viewDirX * viewDirX + viewDirY * viewDirY + viewDirZ * viewDirZ)
        var fx = if (fLen > EPSILON) viewDirX / fLen else 0f
        var fy = if (fLen > EPSILON) viewDirY / fLen else 0f
        var fz = if (fLen > EPSILON) viewDirZ / fLen else 1f

        // Right = up x forward (or fallback)
        val upLen = sqrt(upHintX * upHintX + upHintY * upHintY + upHintZ * upHintZ)
        var rx: Float
        var ry: Float
        var rz: Float
        if (upLen > EPSILON) {
            val uhx = upHintX / upLen
            val uhy = upHintY / upLen
            val uhz = upHintZ / upLen
            rx = uhy * fz - uhz * fy
            ry = uhz * fx - uhx * fz
            rz = uhx * fy - uhy * fx
        } else {
            rx = fz
            ry = 0f
            rz = -fx
        }
        var rLen = sqrt(rx * rx + ry * ry + rz * rz)
        if (rLen < EPSILON) {
            rx = 0f
            ry = fz
            rz = -fy
            rLen = sqrt(rx * rx + ry * ry + rz * rz)
        }
        if (rLen > EPSILON) {
            rx /= rLen
            ry /= rLen
            rz /= rLen
        }

        // Up = forward x right
        val ux = fy * rz - fz * ry
        val uy = fz * rx - fx * rz
        val uz = fx * ry - fy * rx

        result[0] = rx
        result[1] = ry
        result[2] = rz
        result[3] = ux
        result[4] = uy
        result[5] = uz
        result[6] = fx
        result[7] = fy
        result[8] = fz
        return result
    }

    /**
     * Computes Blinn-Phong half vector as [hx, hy, hz].
     */
    private fun computeHalfVectorValues(
        sunX: Float,
        sunY: Float,
        sunZ: Float,
        frame: FloatArray,
    ): FloatArray {
        val fx = frame[6]
        val fy = frame[7]
        val fz = frame[8]
        var hx = sunX + fx
        var hy = sunY + fy
        var hz = sunZ + fz
        val hLen = sqrt(hx * hx + hy * hy + hz * hz)
        if (hLen > EPSILON) {
            hx /= hLen
            hy /= hLen
            hz /= hLen
        }
        halfBuffer[0] = hx
        halfBuffer[1] = hy
        halfBuffer[2] = hz
        return halfBuffer
    }

    /**
     * Converts IntArray ARGB to ByteArray for Skia (handles BGRA native byte order).
     */
    private fun intArrayToBytes(argb: IntArray): ByteArray {
        val bytes = ByteArray(argb.size * 4)
        for (i in argb.indices) {
            val c = argb[i]
            val off = i * 4
            // Skia N32 on little-endian = BGRA
            bytes[off] = ((c) and 0xFF).toByte() // B
            bytes[off + 1] = ((c ushr 8) and 0xFF).toByte() // G
            bytes[off + 2] = ((c ushr 16) and 0xFF).toByte() // R
            bytes[off + 3] = ((c ushr 24) and 0xFF).toByte() // A
        }
        return bytes
    }

    companion object {
        /**
         * Creates a GPU renderer, or null if shader compilation fails.
         */
        fun create(): EarthShaderRenderer? {
            val pipeline = EarthShaderPipeline.create() ?: return null
            return EarthShaderRenderer(pipeline)
        }
    }
}
