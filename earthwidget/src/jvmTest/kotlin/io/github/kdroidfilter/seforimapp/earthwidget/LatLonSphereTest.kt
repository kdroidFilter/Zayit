package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertTrue

class LatLonSphereTest {
    /** The mesh UVs must reproduce the old SkSL shader's equirectangular lookup (north pole at v = 1). */
    @Test
    fun uvsMatchShaderMapping() {
        val mesh = latLonSphere(rings = 8, segments = 16)
        for (i in 0 until mesh.positions.size / 3) {
            val x = mesh.positions[i * 3]
            val y = mesh.positions[i * 3 + 1]
            val z = mesh.positions[i * 3 + 2]
            val u = mesh.uvs[i * 2]
            val v = mesh.uvs[i * 2 + 1]
            val lat = asin(y.coerceIn(-1f, 1f))
            assertTrue(abs(v - (0.5f + lat / PI.toFloat())) < 1e-4f, "v at vertex $i")
            if (abs(y) > 0.999f || u == 0f || u == 1f) continue // poles and seam are ambiguous
            val expectedU = atan2(x, z) / (2f * PI.toFloat()) + 0.5f
            assertTrue(abs(u - expectedU) < 1e-4f, "u at vertex $i: $u vs $expectedU")
        }
    }
}
