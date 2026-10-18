package io.github.kdroidfilter.seforimapp.framework.platform

private val osName = System.getProperty("os.name").orEmpty()

actual val isLinuxDesktop: Boolean = osName.startsWith("Linux", ignoreCase = true)

actual val isMacOsDesktop: Boolean = osName.startsWith("Mac", ignoreCase = true)

actual fun environmentVariable(name: String): String? = System.getenv(name)
