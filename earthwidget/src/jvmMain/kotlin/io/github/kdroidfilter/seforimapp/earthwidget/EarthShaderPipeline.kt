package io.github.kdroidfilter.seforimapp.earthwidget

import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * Manages compiled SkSL shaders for the Earth widget.
 *
 * Shaders are compiled once and reused across frames.
 * Uniform updates are applied per-frame via [RuntimeShaderBuilder].
 */
internal class EarthShaderPipeline private constructor(
    private val sphereEffect: RuntimeEffect,
) {
    private val sphereBuilder = RuntimeShaderBuilder(sphereEffect)

    /**
     * Builds a sphere shader with the given parameters.
     *
     * @param resolution Output size in pixels (square).
     * @param cosYaw Pre-computed cos(rotation).
     * @param sinYaw Pre-computed sin(rotation).
     * @param cosTilt Pre-computed cos(tilt).
     * @param sinTilt Pre-computed sin(tilt).
     * @param sunX Light direction X.
     * @param sunY Light direction Y.
     * @param sunZ Light direction Z.
     * @param ambient Ambient light intensity.
     * @param diffuseStrength Diffuse light strength.
     * @param atmosphereStrength Atmosphere rim glow.
     * @param lightVisibility Shadow occlusion factor (0-1).
     * @param texture Earth or Moon texture as a child shader.
     * @param texWidth Texture width in pixels.
     * @param texHeight Texture height in pixels.
     */
    fun buildSphereShader(
        resolution: Float,
        cosYaw: Float,
        sinYaw: Float,
        cosTilt: Float,
        sinTilt: Float,
        sunX: Float,
        sunY: Float,
        sunZ: Float,
        ambient: Float,
        diffuseStrength: Float,
        specularStrength: Float,
        specularExponent: Int,
        atmosphereStrength: Float,
        shadowAlphaStrength: Float,
        lightVisibility: Float,
        camRightX: Float,
        camRightY: Float,
        camRightZ: Float,
        camUpX: Float,
        camUpY: Float,
        camUpZ: Float,
        camForwardX: Float,
        camForwardY: Float,
        camForwardZ: Float,
        halfVecX: Float,
        halfVecY: Float,
        halfVecZ: Float,
        specEnabled: Int,
        texture: Shader,
        texWidth: Float,
        texHeight: Float,
    ): Shader {
        sphereBuilder.uniform("uResolution", resolution, resolution)
        sphereBuilder.uniform("uCosYaw", cosYaw)
        sphereBuilder.uniform("uSinYaw", sinYaw)
        sphereBuilder.uniform("uCosTilt", cosTilt)
        sphereBuilder.uniform("uSinTilt", sinTilt)
        sphereBuilder.uniform("uSunDir", sunX, sunY, sunZ)
        sphereBuilder.uniform("uAmbient", ambient)
        sphereBuilder.uniform("uDiffuseStrength", diffuseStrength)
        sphereBuilder.uniform("uSpecularStrength", specularStrength)
        sphereBuilder.uniform("uSpecularExponent", specularExponent)
        sphereBuilder.uniform("uAtmosphereStrength", atmosphereStrength)
        sphereBuilder.uniform("uShadowAlphaStrength", shadowAlphaStrength)
        sphereBuilder.uniform("uLightVisibility", lightVisibility)
        sphereBuilder.uniform("uCamRight", camRightX, camRightY, camRightZ)
        sphereBuilder.uniform("uCamUp", camUpX, camUpY, camUpZ)
        sphereBuilder.uniform("uCamForward", camForwardX, camForwardY, camForwardZ)
        sphereBuilder.uniform("uHalfVector", halfVecX, halfVecY, halfVecZ)
        sphereBuilder.uniform("uSpecEnabled", specEnabled)
        sphereBuilder.uniform("uTexSize", texWidth, texHeight)
        sphereBuilder.child("uTexture", texture)
        return sphereBuilder.makeShader()
    }

    companion object {
        /**
         * Creates a pipeline by compiling the sphere shader.
         *
         * @return Pipeline instance, or null if shader compilation fails.
         */
        fun create(): EarthShaderPipeline? =
            try {
                EarthShaderPipeline(RuntimeEffect.makeForShader(SPHERE_SKSL))
            } catch (e: Exception) {
                System.err.println("SkSL compilation failed: ${e.message}")
                null
            }
    }
}
