package io.github.kdroidfilter.seforimapp.framework.platform

/** True on a Linux desktop (not Android), whose OpenGL drivers limit the 3D views (see AppSettings). */
expect val isLinuxDesktop: Boolean

/** True on macOS (not iOS), where some Hebrew fonts lack a bold face (see FontCatalog). */
expect val isMacOsDesktop: Boolean

/** An environment variable of the process, or null (none on mobile in practice). */
expect fun environmentVariable(name: String): String?
