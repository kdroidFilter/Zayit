package io.github.kdroidfilter.seforimapp.framework.crashreporting

import io.github.kdroidfilter.seforimapp.logger.SentryConfig
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CrashReportingTest {
    @AfterTest
    fun restore() {
        SentryConfig.sentryEnabled = true
    }

    @Test
    fun `scrub replaces the home folder in both separator styles`() {
        val home = """C:\Users\Moshe"""
        assertEquals(
            """open ~\AppData\x.db and ~/AppData/y.db""",
            CrashReporting.scrub("""open C:\Users\Moshe\AppData\x.db and C:/users/moshe/AppData/y.db""", home),
        )
        assertEquals("/tmp/x", CrashReporting.scrub("/tmp/x", home))
    }

    @Test
    fun `anonymize strips hostname, user and home paths`() {
        SentryConfig.sentryEnabled = true
        val event =
            SentryEvent().apply {
                serverName = "moshe-laptop"
                user = User().apply { ipAddress = "1.2.3.4" }
                message = Message().apply { formatted = "cannot read /home/moshe/.zayit/seforim.db" }
                exceptions = listOf(SentryException().apply { value = "/home/moshe/a: denied" })
                setExtra("logger.message", "at /home/moshe/b")
            }

        val result = CrashReporting.anonymize(event, home = "/home/moshe")

        assertSame(event, result)
        assertNull(event.serverName)
        assertNull(event.user)
        assertEquals("cannot read ~/.zayit/seforim.db", event.message?.formatted)
        assertEquals("~/a: denied", event.exceptions?.single()?.value)
        assertEquals("at ~/b", event.getExtra("logger.message"))
    }

    @Test
    fun `anonymize drops the event when reports are off`() {
        SentryConfig.sentryEnabled = false
        assertNull(CrashReporting.anonymize(SentryEvent(), home = "/home/moshe"))
    }
}
