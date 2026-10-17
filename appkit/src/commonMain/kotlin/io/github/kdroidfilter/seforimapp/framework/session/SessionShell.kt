package io.github.kdroidfilter.seforimapp.framework.session

/** What [SessionManager] asks of the app's shell (the desktop's windows and virtual desktops). */
interface SessionShell {
    /** True when the session must be neither saved nor restored (the desktop's end-to-end harness). */
    val isSessionDisabled: Boolean

    /** The desktops, their windows and tabs as they are now, to save. */
    fun buildDesktopsState(): DesktopsState

    /** Drops what the shell keeps for tabs (hover-card pictures) that no saved desktop holds any more. */
    fun forgetTabsExcept(tabIds: Set<String>)
}
