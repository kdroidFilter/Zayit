package io.github.kdroidfilter.seforimapp.logger

@Suppress("TooGenericExceptionCaught")
object SilenceLogs {
    @JvmStatic
    fun everything(
        hardMuteStdout: Boolean = false,
        hardMuteStderr: Boolean = false,
    ) {
        // 0) Mettre les props avant init des frameworks (si possible)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off") // SLF4J Simple
        System.setProperty("logging.level.root", "OFF") // Spring/Logback

        // 1) java.util.logging (JUL)
        try {
            val lm =
                java.util.logging.LogManager
                    .getLogManager()
            lm.reset() // retire les handlers
            val root =
                java.util.logging.Logger
                    .getLogger("")
            root.level = java.util.logging.Level.OFF
        } catch (_: Throwable) {
        }

        // 4) "Hard mute" : coupe aussi println/stacktraces si demandé
        if (hardMuteStdout) System.setOut(java.io.PrintStream(NullOut(), true))
        if (hardMuteStderr) System.setErr(java.io.PrintStream(NullOut(), true))
    }

    private class NullOut : java.io.OutputStream() {
        override fun write(b: Int) {}

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {}
    }
}
