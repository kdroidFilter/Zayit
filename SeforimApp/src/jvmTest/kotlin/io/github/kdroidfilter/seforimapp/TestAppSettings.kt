package io.github.kdroidfilter.seforimapp

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import java.util.Properties

/** In-memory [AppSettings] for tests (never touches the user's real preferences). */
fun testAppSettings(): AppSettings = AppSettings(PropertiesSettings(Properties()))
