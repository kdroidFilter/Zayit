package io.github.kdroidfilter.seforimapp.framework.platform

actual val isLinuxDesktop: Boolean = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)

actual fun environmentVariable(name: String): String? = System.getenv(name)
