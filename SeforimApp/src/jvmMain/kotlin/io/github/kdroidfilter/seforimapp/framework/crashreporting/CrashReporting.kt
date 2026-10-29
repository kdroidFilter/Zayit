package io.github.kdroidfilter.seforimapp.framework.crashreporting

import dev.nucleusframework.core.runtime.NucleusApp
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.BuildConfig
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.SentryConfig
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.isDevEnv
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions

/**
 * Anonymous error reports (Sentry), on unless the user turns them off in the settings. Nothing that
 * identifies the user or the machine leaves it: no IP, no hostname, and the home folder (which holds
 * the user name) is replaced by `~` in every text of the report.
 */
@Inject
@SingleIn(AppScope::class)
class CrashReporting(
    private val appSettings: AppSettings,
) {
    /** Starts Sentry when the official build carries a DSN and the user has not turned the reports off. */
    fun start() {
        val enabled = appSettings.isErrorReportingEnabled()
        SentryConfig.sentryEnabled = enabled
        when {
            !enabled -> {
                if (Sentry.isEnabled()) Sentry.close()
                infoln { "Error reports turned off by the user; Sentry stopped." }
            }
            BuildConfig.SENTRY_DSN.isBlank() -> infoln { "Sentry DSN not configured; crash reporting disabled." }
            !Sentry.isEnabled() -> initSentry()
        }
    }

    /** Saves the user's choice and applies it right away. */
    fun setEnabled(enabled: Boolean) {
        appSettings.setErrorReportingEnabled(enabled)
        start()
    }

    private fun initSentry() {
        val sentryEnvironment =
            System
                .getenv("SENTRY_ENVIRONMENT")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "development"

        Sentry.init { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = sentryEnvironment
            options.release = NucleusApp.version
            options.isDebug = isDevEnv
            options.isSendDefaultPii = false
            options.isAttachServerName = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> anonymize(event) }
            // Otherwise the UncaughtExceptionHandlerIntegration captures the crash and swallows it:
            // in the GraalVM native image the envelope upload also fails (no CA trust store), so the
            // real stack trace is lost entirely. Printing it keeps it visible on the console.
            options.setPrintUncaughtStackTrace(true)
        }
        infoln { "Sentry initialized for environment '$sentryEnvironment'." }
    }

    internal companion object {
        private val homeDir: String? = System.getProperty("user.home")?.takeIf { it.length > 1 }

        /** Drops the event when the reports are off, otherwise strips what could identify the user. */
        fun anonymize(
            event: SentryEvent,
            home: String? = homeDir,
        ): SentryEvent? {
            if (!SentryConfig.sentryEnabled) return null
            event.serverName = null
            event.user = null
            event.message?.let { message ->
                message.formatted = message.formatted?.let { scrub(it, home) }
                message.message = message.message?.let { scrub(it, home) }
            }
            event.exceptions?.forEach { exception -> exception.value = exception.value?.let { scrub(it, home) } }
            event.extras?.forEach { (key, value) -> if (value is String) event.setExtra(key, scrub(value, home)) }
            event.breadcrumbs?.forEach { breadcrumb -> breadcrumb.message = breadcrumb.message?.let { scrub(it, home) } }
            return event
        }

        /** Replaces the home folder, in both separator styles on Windows, by `~`. */
        fun scrub(
            text: String,
            home: String?,
        ): String {
            if (home == null) return text
            return text
                .replace(home, "~", ignoreCase = true)
                .replace(home.replace('\\', '/'), "~", ignoreCase = true)
        }
    }
}
