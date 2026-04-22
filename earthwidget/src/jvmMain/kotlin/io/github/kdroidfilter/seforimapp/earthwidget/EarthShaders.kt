package io.github.kdroidfilter.seforimapp.earthwidget

/**
 * SkSL shader source for textured sphere rendering with Phong lighting.
 *
 * This shader is the GPU equivalent of [renderTexturedSphereArgb] / [renderRowRange].
 * It performs per-pixel ray-sphere intersection, texture sampling, and lighting
 * entirely on the GPU.
 *
 * Uniforms mirror the pre-computed values from [SphereRenderParams].
 */
internal val SPHERE_SKSL =
    """
    // Output size (square)
    uniform float2 uResolution;

    // Rotation (pre-computed cos/sin of yaw)
    uniform float uCosYaw;
    uniform float uSinYaw;

    // Tilt (pre-computed cos/sin)
    uniform float uCosTilt;
    uniform float uSinTilt;

    // Light direction (normalized)
    uniform float3 uSunDir;

    // Lighting parameters
    uniform float uAmbient;
    uniform float uDiffuseStrength;
    uniform float uSpecularStrength;
    uniform int uSpecularExponent;
    uniform float uAtmosphereStrength;
    uniform float uShadowAlphaStrength;
    uniform float uLightVisibility;

    // Camera frame (orthonormal basis)
    uniform float3 uCamRight;
    uniform float3 uCamUp;
    uniform float3 uCamForward;

    // Blinn-Phong half vector
    uniform float3 uHalfVector;
    uniform int uSpecEnabled;

    // Texture child shader and dimensions
    uniform shader uTexture;
    uniform float2 uTexSize;

    const float PI = 3.14159265359;
    const float TWO_PI = 6.28318530718;
    const float EDGE_FEATHER = 0.012;
    const float SHADOW_EDGE_START = -0.15;
    const float SHADOW_EDGE_END = 0.1;

    // sRGB <-> linear approximation (gamma 2.2 via square/sqrt)
    float3 srgbToLinear(float3 c) { return c * c; }
    float3 linearToSrgb(float3 c) { return sqrt(c); }

    float smoothStep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    float powInt(float base, int exp) {
        // SkSL doesn't support while loops or bitwise ops — use pow() instead
        return pow(base, float(exp));
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = (uResolution - 1.0) * 0.5;

        // Map to [-1, 1] with Y flipped (screen Y down, sphere Y up)
        float nx = (fragCoord.x - halfSize.x) / halfSize.x;
        float ny = (halfSize.y - fragCoord.y) / halfSize.y;
        float rr = nx * nx + ny * ny;

        // Outside sphere — transparent
        if (rr > 1.0) return half4(0.0);

        float nz = sqrt(1.0 - rr);

        // Camera-to-world transform
        float3 world = uCamRight * nx + uCamUp * ny + uCamForward * nz;

        // Apply tilt then rotation to get texture coordinates
        float rotatedX = world.x * uCosTilt - world.y * uSinTilt;
        float rotatedY = world.x * uSinTilt + world.y * uCosTilt;
        float texX = rotatedX * uCosYaw + world.z * uSinYaw;
        float texZ = -rotatedX * uSinYaw + world.z * uCosYaw;

        // Spherical coordinates -> UV
        float longitude = atan(texX, texZ);
        float latitude = asin(clamp(rotatedY, -1.0, 1.0));
        float u = longitude / TWO_PI + 0.5;
        u = u - floor(u);
        float v = clamp(0.5 - latitude / PI, 0.0, 1.0);

        // Sample texture via child shader (hardware bilinear filtering)
        float2 texCoord = float2(u * uTexSize.x, v * uTexSize.y);
        half4 texSample = uTexture.eval(texCoord);
        float3 texColor = float3(texSample.r, texSample.g, texSample.b);

        // sRGB decode
        float3 texLin = srgbToLinear(texColor);

        // Diffuse lighting (Lambertian)
        float dot_nl = dot(world, uSunDir);
        float shadowMask = smoothStep(SHADOW_EDGE_START, SHADOW_EDGE_END, dot_nl) * uLightVisibility;
        float diffuse = max(dot_nl, 0.0) * uLightVisibility;

        // Ambient (darkened in shadow)
        float ambientShade = uAmbient * (0.25 + 0.75 * shadowMask);
        float baseShade = clamp(ambientShade + uDiffuseStrength * diffuse, 0.0, 1.0);

        // View-dependent shading
        float viewShade = 0.75 + 0.25 * nz;
        float shade = clamp(baseShade * viewShade, 0.0, 1.0);

        // Atmospheric rim glow
        float rim = clamp(1.0 - nz, 0.0, 1.0);
        float atmosphere = clamp(rim * rim * uAtmosphereStrength * shadowMask, 0.0, uAtmosphereStrength);

        // Specular (Blinn-Phong, ocean-weighted)
        float spec = 0.0;
        if (uSpecEnabled == 1 && diffuse > 0.0) {
            float dotH = max(dot(world, uHalfVector), 0.0);
            float baseSpec = uSpecularStrength * powInt(dotH, uSpecularExponent) * uLightVisibility;
            // Ocean mask: blue channel significantly higher than red/green
            float oceanMask = srgbToLinear(float3(clamp(texColor.b - max(texColor.r, texColor.g), 0.0, 1.0), 0.0, 0.0)).x;
            spec = baseSpec * (0.12 + 0.88 * oceanMask);
        }

        // Apply shading in linear space
        float3 shadedLin = clamp(texLin * shade + spec, 0.0, 1.0);

        // Gamma encode + atmosphere (blue tint)
        float3 srgb = linearToSrgb(shadedLin);
        srgb.b = clamp(srgb.b + atmosphere, 0.0, 1.0);

        // Edge feathering
        float dist = sqrt(rr);
        float alpha = clamp((1.0 - dist) / EDGE_FEATHER, 0.0, 1.0);

        // Shadow-based alpha (Moon phases)
        float shadowAlpha = 1.0;
        if (uShadowAlphaStrength > 0.0) {
            float strength = clamp(uShadowAlphaStrength, 0.0, 1.0);
            shadowAlpha = (1.0 - strength) + strength * shadowMask;
        }

        float outAlpha = texSample.a * alpha * shadowAlpha;

        return half4(srgb * outAlpha, outAlpha);
    }
    """.trimIndent()
