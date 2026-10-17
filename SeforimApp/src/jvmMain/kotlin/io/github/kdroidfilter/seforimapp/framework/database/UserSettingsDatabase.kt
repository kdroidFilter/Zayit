package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import java.io.File

/** The user database: the usual one, or the end-to-end harness's own (see [E2e.userDataDir]). */
fun getUserSettingsDatabasePath(): String =
    E2e.userDataDir?.let { dir -> File(dir.apply { mkdirs() }, "user_settings.db").absolutePath }
        ?: defaultUserSettingsDatabasePath()
