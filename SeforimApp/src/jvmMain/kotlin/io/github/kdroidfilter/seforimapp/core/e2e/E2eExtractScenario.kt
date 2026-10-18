package io.github.kdroidfilter.seforimapp.core.e2e

import com.russhwolf.settings.PreferencesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase
import okio.Path.Companion.toOkioPath
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.prefs.Preferences

/**
 * The database bundle's extraction, as the onboarding runs it (`ZAYIT_E2E_SCENARIO=extract`, `ZAYIT_E2E_BUNDLE` naming a
 * `.tar.zst` or its `.part01`): into the run's output, with throwaway settings, never the user's databases. Notes the
 * extracted database's size and sha256, to compare with the one packed.
 */
object E2eExtractScenario {
    private const val SETTINGS_NODE = "io/github/kdroidfilter/seforimapp/e2e-extract"

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "extract") return
        val bundle = System.getenv("ZAYIT_E2E_BUNDLE") ?: error("ZAYIT_E2E_BUNDLE names no bundle")
        val dbDir = File(E2e.outDir, "extracted")
        val node = Preferences.userRoot().node(SETTINGS_NODE)
        try {
            var lastProgress = 0f
            val dbPath =
                ExtractUseCase(AppSettings(PreferencesSettings(node))) { dbDir.toOkioPath() }
                    .extractToDatabase(bundle) { lastProgress = it }
            val db = File(dbPath)
            sc.note("extract: $dbPath, ${db.length()} bytes, sha256 ${sha256(db)}, progress $lastProgress")
            dbDir.walkTopDown().filter { it.isFile }.forEach { sc.note("extract: file ${it.relativeTo(dbDir)} ${it.length()}") }
        } finally {
            node.removeNode()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}
