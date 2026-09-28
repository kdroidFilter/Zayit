package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import io.github.erkko68.filament.compose.scene.Direction
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import kotlin.math.exp

// Camera basis and projection, shared by the shaders (they must match SkyCamera / Filament exactly)
private const val PRELUDE = """
uniform float2 res;
uniform float3 fwd;
uniform float3 rightV;
uniform float3 upV;
uniform float tanV;
uniform float aspect;
uniform float3 sun;

float3 viewDir(float2 p) {
    float u = (p.x / res.x * 2.0 - 1.0) * aspect * tanV;
    float v = (1.0 - p.y / res.y * 2.0) * tanV;
    return normalize(fwd + u * rightV + v * upV);
}
"""

/**
 * The sky: single scattering through a spherical atmosphere, as in [skyRadiance] (wwwtyro/glsl-atmosphere,
 * Hillaire's constants and ozone), plus moonlight, nightglow, eye adaptation and night vision (Stellarium Web's
 * scotopic shift toward a blue-grey).
 */
private const val SKY_SKSL =
    PRELUDE + """
uniform float3 moon;
uniform float moonLight;
uniform float exposure;
uniform float scotopic;

const float RP = 6360.0;
const float RT = 6460.0;
const float3 BR = float3(0.005802, 0.013558, 0.033100);
const float BMS = 0.003996;
const float BME = 0.004440;
const float3 BO = float3(0.000650, 0.001881, 0.000085);
const float PI = 3.14159265;

float farHit(float3 o, float3 d, float r) {
    float b = dot(o, d);
    float c = dot(o, o) - r * r;
    return -b + sqrt(max(b * b - c, 0.0));
}

bool shadowed(float3 o, float3 d) {
    float b = dot(o, d);
    return b < 0.0 && b * b - (dot(o, o) - RP * RP) > 0.0;
}

float ozone(float h) { return max(0.0, 1.0 - abs(h - 25.0) / 15.0); }

// Multiple scattering that carries twilight, as twilightGlow() in SkyAtmosphere.kt
float3 twilight(float3 d, float3 s) {
    float el = degrees(asin(clamp(s.y, -1.0, 1.0)));
    if (el > 4.0 || el < -24.0) return float3(0.0);
    float fade = clamp((4.0 - el) / 6.0, 0.0, 1.0);
    float level = 2.0e-4 * exp(0.855 * (min(el, 0.0) + 6.0)) * fade;
    float toward = dot(normalize(s.xz + float2(1e-5)), normalize(d.xz + float2(1e-5))) * 0.5 + 0.5;
    // Blue overhead; the afterglow low on the Sun's side is orange
    // ...which fades as the Sun sinks: bright just after shkia, a last red line by tzeis
    float glow = 3.0 * toward * toward * exp(-max(d.y, 0.0) * 6.0) * clamp(1.0 - (-el - 3.0) / 5.0, 0.08, 1.0);
    return level * (2.0 * float3(0.35, 0.60, 1.60) + glow * float3(1.30, 0.62, 0.28));
}

float phaseR(float mu) { return 3.0 / (16.0 * PI) * (1.0 + mu * mu); }

// The aureole is capped inside 25° of the Sun: uncapped, it saturates to white far past the disc on screen and the
// Sun reads several times the Moon's size, where the two look the same to the eye
float phaseM(float mu) {
    mu = min(mu, 0.906);
    float g = 0.8;
    float gg = g * g;
    return 3.0 / (8.0 * PI) * ((1.0 - gg) * (mu * mu + 1.0)) / (pow(1.0 + gg - 2.0 * mu * g, 1.5) * (2.0 + gg));
}

float3 scatter(float3 d, float3 s) {
    float3 o = float3(0.0, RP + 0.2, 0.0);
    float len = farHit(o, d, RT);
    float mu = dot(d, s);
    float3 odP = float3(0.0); // rayleigh, mie, ozone optical depth along the view
    float3 totR = float3(0.0);
    float3 totM = float3(0.0);
    for (int i = 0; i < 12; i++) {
        // Samples bunched near the observer, where the air is dense
        float u = (float(i) + 0.5) / 12.0;
        float step = len * 2.0 * u / 12.0;
        float3 p = o + d * (len * u * u);
        float h = length(p) - RP;
        float sr = exp(-h / 8.0) * step;
        float sm = exp(-h / 1.2) * step;
        odP += float3(sr, sm, ozone(h) * step);
        if (shadowed(p, s)) continue;
        float sl = farHit(p, s, RT) / 6.0;
        float3 odS = float3(0.0);
        for (int j = 0; j < 6; j++) {
            float3 q = p + s * ((float(j) + 0.5) * sl);
            float hq = length(q) - RP;
            odS += float3(exp(-hq / 8.0), exp(-hq / 1.2), ozone(hq)) * sl;
        }
        float3 od = odP + odS;
        float3 att = exp(-(BR * od.x + BME * od.y + BO * od.z));
        totR += sr * att;
        totM += sm * att;
    }
    return 22.0 * (phaseR(mu) * BR * totR + phaseM(mu) * BMS * totM) + twilight(d, s);
}

half4 main(float2 p) {
    float3 d = viewDir(p);
    d.y = max(d.y, 0.0005);
    d = normalize(d);
    float3 L = scatter(d, sun);
    // Moonlight: the same air lit a few hundred thousand times more faintly (a cheap single bounce)
    float mm = dot(d, moon);
    float path = 1.0 / (d.y + 0.12);
    L += moonLight * (phaseR(mm) * BR * 8.0 + phaseM(mm) * BMS * 1.2) * path;
    L += float3(0.6e-6, 0.8e-6, 1.3e-6);
    // Tone mapped on luminance, hue kept (as Stellarium Web does): per channel, a bright orange glow burns to white
    float Y = max(dot(L, float3(0.2126, 0.7152, 0.0722)), 1e-12);
    float3 c = min(L * ((1.0 - exp(-Y * exposure)) / Y), float3(1.0));
    // Past full brightness the colour whitens, like an overexposed sky
    c = mix(c, float3(1.0), clamp(Y * exposure - 1.2, 0.0, 1.0) * 0.6);
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(c, lum * float3(0.80, 0.88, 1.08), scotopic * 0.85);
    return half4(half3(c), 1.0);
}
"""

/**
 * The land: a clean skyline (Stellarium's polygonal landscapes), dark and lit as Stellarium lights them, taking the
 * sky's haze toward the horizon.
 */
private const val GROUND_SKSL =
    PRELUDE + """
uniform float3 hazeSun;
uniform float3 hazeAnti;
uniform float3 land;

float hash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float skyline(float az) {
    return 0.020 + 0.012 * sin(2.0 * az + 0.7) + 0.008 * sin(5.0 * az + 2.0) + 0.004 * sin(11.0 * az + 0.3)
        + 0.002 * sin(23.0 * az + 1.1) + 0.001 * sin(47.0 * az + 0.5);
}

half4 main(float2 p) {
    float3 d = viewDir(p);
    float el = asin(clamp(d.y, -1.0, 1.0));
    if (el > 0.05) return half4(0.0);
    float az = atan(d.x, -d.z);
    float px = 2.0 * tanV / res.y;
    float h = skyline(az);
    float a = smoothstep(h + px, h - px, el);
    if (a <= 0.0) return half4(0.0);
    float toward = dot(normalize(sun.xz + float2(1e-5)), normalize(d.xz + float2(1e-5))) * 0.5 + 0.5;
    float3 haze = mix(hazeAnti, hazeSun, toward * toward);
    // Aerial perspective: the skyline is far and takes the sky's colour; the foreground stays dark
    float3 c = mix(land, haze, 0.55 * exp(-(h - el) / 0.025));
    return half4(half3(c * a), a);
}
"""

/**
 * The Moon, drawn where it sits in the sky (after Stellarium Web's planet shader): a sphere worked out per pixel on
 * its round disc, its near side toward us and turned to the real celestial north, lit by the real Sun (its phase
 * and the tilt of its horns), flatter than Lambert as lunar dust is, with a faint earthshine on its night side.
 * Blended additively, so that night side shows the sky behind.
 */
private const val MOON_SKSL = """
uniform float2 center;
uniform float radius;
uniform float3 rightW;
uniform float3 upW;
uniform float3 toViewer;
uniform float3 northW;
uniform float3 eastW;
uniform float3 sunW;
uniform shader moonTex;
uniform float2 texSize;
uniform float shine;

half4 main(float2 p) {
    float2 q = (p - center) / radius;
    q.y = -q.y;
    float rr = dot(q, q);
    if (rr > 1.0) return half4(0.0);
    float3 n = q.x * rightW + q.y * upW + sqrt(1.0 - rr) * toViewer;
    float lon = atan(dot(n, eastW), dot(n, toViewer));
    float lat = asin(clamp(dot(n, northW), -1.0, 1.0));
    float3 albedo = moonTex.eval(float2(0.5 + lon / 6.2831853, 0.5 - lat / 3.14159265) * texSize).rgb;
    albedo = (albedo - 0.5) * 0.8 + 0.5;
    float lit = pow(max(dot(n, sunW), 0.0), 0.7);
    float3 c = albedo * (lit * 1.05 + shine);
    c *= smoothstep(1.0, 1.0 - 2.0 / radius, sqrt(rr));
    return half4(half3(c), max(c.r, max(c.g, c.b)));
}
"""

private val MoonEffect by lazy { RuntimeEffect.makeForShader(MOON_SKSL) }

private fun Direction.minus(
    other: Direction,
    k: Float,
) = Direction(x - other.x * k, y - other.y * k, z - other.z * k)

private fun Direction.dot(o: Direction) = x * o.x + y * o.y + z * o.z

private fun Direction.unit(): Direction {
    val l = kotlin.math.sqrt(dot(this)).coerceAtLeast(1e-6f)
    return Direction(x / l, y / l, z / l)
}

/** The Moon's disc on screen (a rect covering it); an empty brush when it is behind the viewer. */
internal fun moonBrush(
    state: SkyViewState,
    camera: SkyCamera,
    image: Image,
    imageShader: Shader,
    illumination: Float,
): ShaderBrush {
    val m = state.moon
    val at = camera.project(m) ?: Offset(-1e4f, -1e4f)
    val radius = kotlin.math.tan(MOON_ANGULAR_RADIUS_DEG * DEG_TO_RAD_F) / camera.tanHalfV * state.heightPx / 2f
    val toViewer = Direction(-m.x, -m.y, -m.z)
    val north = (state.stars * Direction(0f, 0f, 1f)).let { it.minus(m, it.dot(m)).unit() }
    val east = Direction(m.y * north.z - m.z * north.y, m.z * north.x - m.x * north.z, m.x * north.y - m.y * north.x)
    val right = camera.right.minus(m, camera.right.dot(m)).unit()
    val up = camera.up.minus(m, camera.up.dot(m)).unit()
    return ShaderBrush(
        RuntimeShaderBuilder(MoonEffect)
            .apply {
                uniform("center", at.x, at.y)
                uniform("radius", radius)
                uniform("rightW", right)
                uniform("upW", up)
                uniform("toViewer", toViewer)
                uniform("northW", north)
                uniform("eastW", east)
                uniform("sunW", state.sun)
                child("moonTex", imageShader)
                uniform("texSize", image.width.toFloat(), image.height.toFloat())
                // Earthshine: brightest on a thin crescent, when the Earth seen from the Moon is nearly full
                uniform("shine", 0.05f * (1f - illumination))
            }.makeShader()
            .asComposeShader(),
    )
}

private val SkyEffect by lazy { RuntimeEffect.makeForShader(SKY_SKSL) }
private val GroundEffect by lazy { RuntimeEffect.makeForShader(GROUND_SKSL) }

private fun RuntimeShaderBuilder.uniform(
    name: String,
    d: Direction,
) = uniform(name, d.x, d.y, d.z)

private fun RuntimeShaderBuilder.uniform(
    name: String,
    c: Color,
) = uniform(name, c.red, c.green, c.blue)

private fun builder(
    effect: RuntimeEffect,
    state: SkyViewState,
): RuntimeShaderBuilder {
    val camera = SkyCamera(state)
    return RuntimeShaderBuilder(effect).apply {
        uniform("res", state.widthPx.toFloat(), state.heightPx.toFloat())
        uniform("fwd", camera.forward)
        uniform("rightV", camera.right)
        uniform("upV", camera.up)
        uniform("tanV", camera.tanHalfV)
        uniform("aspect", camera.aspect)
        uniform("sun", state.sun)
    }
}

/** What the shaders need beyond the camera, worked out once a frame on the CPU. */
internal class SkyLight(
    val exposure: Float,
    val scotopic: Float,
    val moonLight: Float,
    val hazeSun: Color,
    val hazeAnti: Color,
    val land: Color,
    /** Zenith brightness as displayed, 0..1: sets the faintest stars that show. */
    val zenithShown: Float,
)

internal fun skyBrush(
    state: SkyViewState,
    light: SkyLight,
) = ShaderBrush(
    builder(SkyEffect, state)
        .apply {
            uniform("moon", state.moon)
            uniform("moonLight", light.moonLight)
            uniform("exposure", light.exposure)
            uniform("scotopic", light.scotopic)
        }.makeShader()
        .asComposeShader(),
)

internal fun groundBrush(
    state: SkyViewState,
    light: SkyLight,
) = ShaderBrush(
    builder(GroundEffect, state)
        .apply {
            uniform("hazeSun", light.hazeSun)
            uniform("hazeAnti", light.hazeAnti)
            uniform("land", light.land)
        }.makeShader()
        .asComposeShader(),
)

internal fun imageShader(image: Image): Shader = image.makeShader(FilterTileMode.REPEAT, FilterTileMode.CLAMP, SamplingMode.LINEAR)

/** Tone mapping (on luminance, hue kept) and night vision, as in the sky shader. */
internal fun shownColor(
    radiance: DoubleArray,
    exposure: Double,
    scotopic: Double,
): Color {
    val l = DoubleArray(3) { radiance[it] + NIGHT_FLOOR[it] }
    val y = maxOf(0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2], 1e-12)
    val white = ((y * exposure - 1.2).coerceIn(0.0, 1.0)) * 0.6
    val c = DoubleArray(3) { (l[it] * (1.0 - exp(-y * exposure)) / y).coerceAtMost(1.0).let { v -> v + (1.0 - v) * white } }
    val lum = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    val tint = doubleArrayOf(0.80, 0.88, 1.08)
    val k = scotopic * 0.85
    return Color(
        (c[0] + (lum * tint[0] - c[0]) * k).toFloat().coerceIn(0f, 1f),
        (c[1] + (lum * tint[1] - c[1]) * k).toFloat().coerceIn(0f, 1f),
        (c[2] + (lum * tint[2] - c[2]) * k).toFloat().coerceIn(0f, 1f),
    )
}
