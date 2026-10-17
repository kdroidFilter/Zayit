package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup

/**
 * A Home widget's own settings, each saved under `home_widget.<widgetId>.<name>`; given as its [HomeWidget.settings],
 * reset all at once from its options dialog.
 */
abstract class WidgetSettings(
    widgetId: String,
) : SettingsGroup(AppSettings.HOME_WIDGET_SETTINGS_PREFIX + widgetId)
