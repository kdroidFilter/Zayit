package io.github.kdroidfilter.seforimapp.earthwidget

import io.github.erkko68.filament.compose.scene.Direction
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** [tubeMesh] builds the same arrays as its former list-based version, bit for bit. */
class TubeMeshTest {
    @Test
    fun `tube meshes are unchanged`() {
        val r = 200f
        val full = (0..360).map { orbit(it.toFloat(), r) }
        val runs = full.mapIndexed { i, p -> p.takeIf { i % 50 !in 20..24 && i != 100 && i != 102 } }
        val cases =
            listOf(
                Triple(full, 0.8f, 6),
                Triple(runs, 0.8f, 6),
                Triple(runs, 1.5f, 16),
                Triple(listOf(orbit(3f, r)), 1f, 6),
                Triple(listOf<MoonOrbitPosition?>(null, null), 1f, 6),
            )
        for ((path, radius, sides) in cases) {
            for (origin in listOf(Vec3f(0f, 0f, 0f), Vec3f(12f, -3f, 40f))) {
                val expected = tubeMeshReference(path, radius, sides, origin)
                val actual = tubeMesh(path, radius, sides, origin)
                if (expected == null) {
                    assertEquals(null, actual)
                    continue
                }
                actual!!
                assertContentEquals(expected.positions, actual.positions)
                assertContentEquals(expected.normals, actual.normals)
                assertContentEquals(expected.uvs, actual.uvs)
                assertContentEquals(expected.indices, actual.indices)
            }
        }
    }

    @Test
    fun `orbit meshes are unchanged`() {
        val r = 200f
        for ((kl, offsets) in listOf(null to (0f to 0f), (10f to 120f) to (0.4f to 0.25f), (300f to 40f) to (13.7f to 0.7f))) {
            for (withOrbit in listOf(true, false)) {
                val point = { deg: Float ->
                    val a = Math.toRadians(deg.toDouble())

                    Direction((cos(a) * r).toFloat(), (sin(a) * r * 0.3).toFloat(), (sin(a) * r).toFloat())
                }
                val expected = OrbitMeshesReference.build(r, kl?.first, kl?.second, offsets.first, offsets.second, withOrbit, point)
                val actual = OrbitMeshes.build(r, kl?.first, kl?.second, offsets.first, offsets.second, withOrbit, point)
                for ((e, a) in (expected.orbit + expected.kiddushLevana).zip(actual.orbit + actual.kiddushLevana)) {
                    assertEquals(e == null, a == null)
                    if (e == null || a == null) continue
                    assertContentEquals(e.positions, a.positions)
                    assertContentEquals(e.uvs, a.uvs)
                    assertContentEquals(e.indices, a.indices)
                }
            }
        }
    }

    private fun orbit(
        deg: Float,
        r: Float,
    ): MoonOrbitPosition {
        val a = Math.toRadians(deg.toDouble())
        return MoonOrbitPosition(x = (cos(a) * r).toFloat(), yCam = (sin(a) * r * 0.3).toFloat(), zCam = (sin(a) * r).toFloat())
    }
}

private fun referenceNormals(positions: FloatArray): FloatArray {
    val out = FloatArray(positions.size)
    for (i in positions.indices step 3) {
        val v = Vec3f(positions[i], positions[i + 1], positions[i + 2]).normalized()
        out[i] = v.x
        out[i + 1] = v.y
        out[i + 2] = v.z
    }
    return out
}

/** Tubes along each run of non-null points; the orbit is centred on the origin, so the radial vector is a normal. */
private fun tubeMeshReference(
    path: List<MoonOrbitPosition?>,
    radius: Float,
    sides: Int = 6,
    /** Where the cross-sections are turned from, relative to the points: the origin by default. */
    normalOrigin: Vec3f = Vec3f(0f, 0f, 0f),
): MeshArrays? {
    val positions = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    val indices = ArrayList<Int>()
    var runStart = -1
    for (i in path.indices) {
        val p = path[i]
        if (p == null) {
            runStart = -1
            continue
        }
        val prev = path.getOrNull(i - 1) ?: p
        val next = path.getOrNull(i + 1) ?: p
        val t = Vec3f(next.x - prev.x, next.yCam - prev.yCam, next.zCam - prev.zCam).normalized()
        val n1 = Vec3f(p.x + normalOrigin.x, p.yCam + normalOrigin.y, p.zCam + normalOrigin.z).normalized()
        val n2 = cross(t, n1)
        val base = positions.size / 3
        for (k in 0 until sides) {
            val a = k * 2f * PI.toFloat() / sides
            positions += p.x + radius * (cos(a) * n1.x + sin(a) * n2.x)
            positions += p.yCam + radius * (cos(a) * n1.y + sin(a) * n2.y)
            positions += p.zCam + radius * (cos(a) * n1.z + sin(a) * n2.z)
            uvs += i.toFloat() / path.size
            uvs += k.toFloat() / sides
        }
        if (runStart >= 0) {
            val prevBase = base - sides
            for (k in 0 until sides) {
                val k1 = (k + 1) % sides
                indices += listOf(prevBase + k, prevBase + k1, base + k, prevBase + k1, base + k1, base + k)
            }
        }
        runStart = i
    }
    if (indices.isEmpty()) return null
    val pos = positions.toFloatArray()
    return MeshArrays(pos, referenceNormals(pos), uvs.toFloatArray(), indices.toIntArray())
}

/** OrbitMeshes.build before it moved to primitive arrays, for [TubeMeshTest]. */
internal object OrbitMeshesReference {
    fun build(
        orbitRadius: Float,
        klStart: Float?,
        klEnd: Float?,
        sampleOffset: Float = 0f,
        membershipOffset: Float = 0f,
        withOrbit: Boolean = true,
        cameraPoint: (Float) -> Direction,
    ): OrbitMeshes {
        val points =
            (0..360).map { i ->
                val deg = i * (360f / 360) - sampleOffset
                val p = cameraPoint(deg)
                deg to MoonOrbitPosition(x = p.x, yCam = p.y, zCam = p.z)
            }

        val bands =
            points.map { (_, p) ->
                val depth = orbitDepth(p.zCam, orbitRadius)
                (depth * ORBIT_DEPTH_BANDS).toInt().coerceIn(0, ORBIT_DEPTH_BANDS - 1)
            }

        // A point also closes the previous point's band, so neighbouring bands join without a gap.
        fun tube(
            radius: Float,
            band: Int,
            keep: (Float) -> Boolean,
        ) = tubeMeshReference(
            points.mapIndexed { i, (deg, pos) ->
                pos.takeIf { keep(deg) && (bands[i] == band || bands.getOrNull(i - 1) == band) }
            },
            radius,
        )
        val inKl = { deg: Float -> klStart != null && klEnd != null && isAngleInRange(deg + membershipOffset, klStart, klEnd) }
        return OrbitMeshes(
            orbit = List(ORBIT_DEPTH_BANDS) { band -> if (withOrbit) tube(0.75f, band) { true } else null },
            kiddushLevana = List(ORBIT_DEPTH_BANDS) { tube(1.25f, it, inKl) },
        )
    }
}
