package io.github.kdroidfilter.seforimapp.framework.platform

import io.github.kdroidfilter.platformtools.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformInfoTest {
    @Test
    fun `currentOS is not null`() {
        val os = PlatformInfo.currentOS
        assertTrue(os in OperatingSystem.entries)
    }

    @Test
    fun `at most one platform flag is true`() {
        val flags = listOf(PlatformInfo.isMacOS, PlatformInfo.isWindows, PlatformInfo.isLinux)
        val trueCount = flags.count { it }
        // On desktop platforms exactly one should be true, on other platforms all are false
        assertTrue(trueCount <= 1, "At most one platform flag should be true")
    }

    @Test
    fun `isMacOS matches currentOS`() {
        assertEquals(
            PlatformInfo.currentOS == OperatingSystem.MACOS,
            PlatformInfo.isMacOS,
        )
    }

    @Test
    fun `isWindows matches currentOS`() {
        assertEquals(
            PlatformInfo.currentOS == OperatingSystem.WINDOWS,
            PlatformInfo.isWindows,
        )
    }

    @Test
    fun `isLinux matches currentOS`() {
        assertEquals(
            PlatformInfo.currentOS == OperatingSystem.LINUX,
            PlatformInfo.isLinux,
        )
    }

    @Test
    fun `platform values are consistent`() {
        when (PlatformInfo.currentOS) {
            OperatingSystem.MACOS -> {
                assertTrue(PlatformInfo.isMacOS)
                assertTrue(!PlatformInfo.isWindows)
                assertTrue(!PlatformInfo.isLinux)
            }
            OperatingSystem.WINDOWS -> {
                assertTrue(!PlatformInfo.isMacOS)
                assertTrue(PlatformInfo.isWindows)
                assertTrue(!PlatformInfo.isLinux)
            }
            OperatingSystem.LINUX -> {
                assertTrue(!PlatformInfo.isMacOS)
                assertTrue(!PlatformInfo.isWindows)
                assertTrue(PlatformInfo.isLinux)
            }
            else -> {
                // For ANDROID, IOS, UNKNOWN - all desktop flags should be false
                assertTrue(!PlatformInfo.isMacOS)
                assertTrue(!PlatformInfo.isWindows)
                assertTrue(!PlatformInfo.isLinux)
            }
        }
    }
}
