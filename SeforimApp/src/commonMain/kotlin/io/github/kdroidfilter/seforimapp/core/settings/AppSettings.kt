@@
 private const val KEY_THEME = "theme.mode"
 private const val KEY_THEME_STYLE = "theme.style"
 private const val KEY_ACCENT_COLOR = "theme.accent"
+private const val KEY_NATIVE_TEXT_SELECTION_MACOS = "ui.native_text_selection_macos"
@@
+fun getUseNativeMacTextSelection(defaultValue: Boolean = false): Boolean {
+    return settings.getBoolean(KEY_NATIVE_TEXT_SELECTION_MACOS, defaultValue)
+}
+
+fun setUseNativeMacTextSelection(value: Boolean) {
+    settings.putBoolean(KEY_NATIVE_TEXT_SELECTION_MACOS, value)
+}
