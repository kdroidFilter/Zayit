package io.github.kdroidfilter.seforimapp.framework.update

import dev.nucleusframework.core.runtime.ExecutableRuntime
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo

/** How this copy of the app was installed, as far as its updates are concerned. */
object InstallInfo {
    /** False for the Microsoft Store package (MSIX): the Store updates it and the app never checks. */
    val selfUpdates: Boolean by lazy { !(PlatformInfo.isWindows && ExecutableRuntime.isAppX()) }

    /** True when a deb/rpm install carries the passwordless update helper and its polkit policy. */
    val hasPasswordlessUpdateHelper: Boolean by lazy { PlatformInfo.isLinux && hasLinuxUpdateHelper() }
}
