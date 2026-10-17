package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SessionShell
import io.github.kdroidfilter.seforimapp.framework.session.TabThumbnailStore

/** The session of the desktop's windows and virtual desktops. [desktopManager] is lazy: it is built from the session. */
class DesktopSessionShell(
    private val desktopManager: Lazy<DesktopManager>,
    private val thumbnails: TabThumbnailStore,
) : SessionShell {
    // The end-to-end harness must never overwrite or restore the user's session
    override val isSessionDisabled: Boolean get() = E2e.enabled

    override fun buildDesktopsState(): DesktopsState = desktopManager.value.buildDesktopsState()

    override fun forgetTabsExcept(tabIds: Set<String>) = thumbnails.prune(tabIds)
}
