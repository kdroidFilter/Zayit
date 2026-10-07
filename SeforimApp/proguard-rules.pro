-keepclasseswithmembers public class io.github.kdroidfilter.seforimapp.MainKt {
    public static void main(java.lang.String[]);
}

-dontwarn kotlinx.coroutines.debug.*

# ProGuard 7.10 return-type specialization emits invalid bytecode (VerifyError in
# androidx.compose.ui.text.ParagraphKt: narrows the return to SkiaParagraph but keeps a Paragraph checkcast)
-optimizations !method/specialization/returntype

-keep class kotlinx.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keepclassmembers class * implements com.sun.jna.* { public *; }
-dontwarn com.sun.jna.**

# Keep specific JNA Platform classes used in the project
-keep class com.sun.jna.platform.** { *; }
-keep class com.sun.jna.win32.** { *; }
-dontwarn com.sun.jna.platform.**


-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations
-dontnote kotlinx.serialization.SerializationKt


# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Keep Ktor Kotlinx Serialization provider loaded via ServiceLoader
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }

# Keep SQLite JDBC driver and any Driver implementations discoverable by DriverManager
-keep class org.sqlite.** { *; }
-keep class * implements java.sql.Driver { *; }
-dontwarn org.sqlite.**

# Keep GStreamer Java bindings (avoid enum unboxing/optimization)
-keep class org.freedesktop.gstreamer.** { *; }
-keep enum org.freedesktop.gstreamer.** { *; }
-dontwarn org.freedesktop.gstreamer.**

# Coil, OkHttp, and Okio are used for AsyncImage. Keep them to prevent
# release-only issues where fetchers/decoders or compose adapters are removed.
-keep class coil3.** { *; }
-keep class coil3.compose.** { *; }
-keep class coil3.network.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn coil3.**
-dontwarn okhttp3.**
-dontwarn okio.**

# FileKit dialogs (folder/file pickers). Keep providers and dialog implementations
# as they may be loaded reflectively (or via ServiceLoader) and get stripped.
-keep class io.github.vinceglb.filekit.** { *; }
-dontwarn io.github.vinceglb.filekit.**

# D-Bus bindings may be used on Linux via portals. Keep to be safe in release.
-keep class org.freedesktop.dbus.** { *; }
-dontwarn org.freedesktop.dbus.**
#################################### SLF4J #####################################
-dontwarn org.slf4j.**

# Prevent runtime crashes from use of class.java.getName()
-dontwarn javax.naming.**

# Ignore warnings and Don't obfuscate for now
-dontobfuscate
-ignorewarnings

-keep class sun.misc.Unsafe { *; }
-dontnote sun.misc.Unsafe

-keep class com.sun.jna** { *; }
-dontnote com.sun.jna**

-keep class androidx.compose.ui.input.key.KeyEvent_desktopKt { *; }
-dontnote androidx.compose.ui.input.key.KeyEvent_desktopKt

-keep class androidx.compose.ui.input.key.KeyEvent_skikoKt { *; }
-dontnote androidx.compose.ui.input.key.KeyEvent_skikoKt
-dontwarn androidx.compose.ui.input.key.KeyEvent_skikoKt

-dontnote org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.**
-dontwarn org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.**

-dontnote org.jetbrains.jewel.foundation.lazy.**
-dontwarn org.jetbrains.jewel.foundation.lazy.**

-dontnote org.jetbrains.jewel.foundation.util.**
-dontwarn org.jetbrains.jewel.foundation.util.**

# Preserve sealed interface metadata so R8/ProGuard doesn't break sealed hierarchies (Java 17)
-keepattributes PermittedSubclasses

# Keep Jewel painter classes to prevent ICCE with sealed interface PainterHint
-keep class org.jetbrains.jewel.ui.painter.** { *; }
-dontwarn org.jetbrains.jewel.ui.painter.**

# --- Fix crash: org.nibor.autolink.LinkType not an enum (ProGuard altering enums) ---
# Keep Autolink library and its enums intact so EnumSet.* works at runtime.
-keep class org.nibor.autolink.** { *; }
-keep enum org.nibor.autolink.** { *; }
-dontwarn org.nibor.autolink.**

# Keep CommonMark autolink extension classes used by Jewel Markdown
-keep class org.commonmark.ext.autolink.** { *; }
-dontwarn org.commonmark.ext.autolink.**


# --- Fix crash: Lucene MMapDirectory provider removed by R8/ProGuard in release builds ---
# Lucene's MMapDirectory reflectively loads MemorySegmentIndexInputProvider (Class.forName).
# When code shrinking is enabled, that provider (and related classes) can be removed
# because there are no direct references. Keep Lucene store classes to prevent
# LinkageError/ClassNotFoundException at runtime.
-keep class org.apache.lucene.store.MemorySegmentIndexInputProvider { *; }
-keep class org.apache.lucene.store.MMapDirectory { *; }
-keep class org.apache.lucene.store.** { *; }
-dontwarn org.apache.lucene.**


# Lucene analyzer factories discovered via SPI or reflection
-keep class org.apache.lucene.analysis.util.*Factory { *; }



# --- Fix crash: Lucene Codec SPI providers removed in release builds ---
# Lucene discovers Codec implementations via Java ServiceLoader/NamedSPILoader.
# When shrinking is enabled, concrete codec implementations (e.g., Lucene103Codec)
# can be removed because they are not directly referenced in code, causing:
# ServiceConfigurationError: org.apache.lucene.codecs.Codec: Provider ... not found
# Keep all codec implementations and explicitly the versioned default codec.
-keep class org.apache.lucene.codecs.Codec { *; }
-keep class org.apache.lucene.codecs.** { *; }
-keep class * extends org.apache.lucene.codecs.Codec { *; }
-keep class org.apache.lucene.codecs.lucene103.Lucene103Codec { *; }
# If using a different Lucene version, also keep the corresponding codec package/class
# e.g., lucene90.Lucene90Codec, lucene100.Lucene100Codec, lucene101.Lucene101Codec, etc.
# -keep class org.apache.lucene.codecs.lucene90.Lucene90Codec { *; }
# -keep class org.apache.lucene.codecs.lucene100.Lucene100Codec { *; }
# -keep class org.apache.lucene.codecs.lucene101.Lucene101Codec { *; }



# -----------------------------------------------------------------------------
# Completely disable shrinking/obfuscation for ALL Lucene classes (user request)
# Rationale: Lucene uses SPI/ServiceLoader (e.g., Codecs, Analyzers), reflection,
# and versioned packages. To avoid release-only crashes, keep everything under
# the Lucene namespace intact.
# -----------------------------------------------------------------------------
-keep class org.apache.lucene.** { *; }
-keep interface org.apache.lucene.** { *; }
# Already present above, but keep it close to these rules for clarity:
-dontwarn org.apache.lucene.**


# --- Fix crash: Jsoup Entities$EscapeMode is not an enum (ProGuard altering enums) ---
# Jsoup's cleaner and output settings rely on enums (e.g., Entities$EscapeMode) resolved
# at runtime via Enum.valueOf. If R8/ProGuard rewrites or unboxes these enums, it will crash
# with: IllegalArgumentException: org.jsoup.nodes.Entities$EscapeMode is not an enum class
# Keep Jsoup classes and especially its enums intact.
-keep class org.jsoup.** { *; }
-keep enum org.jsoup.** { *; }
-dontwarn org.jsoup.**

# --- Fix crash: HebrewDateFormatter EnumMap NPE (ProGuard altering enums) ---
# Zmanim's HebrewDateFormatter builds EnumMap<JewishCalendar.Parsha, String> at runtime.
# If R8/ProGuard rewrites or unboxes these enums, EnumMap will crash with:
# "Cannot read the array length because this.keyUniverse is null".
-keep class io.github.kdroidfilter.kosherkotlin.** { *; }
-keep enum io.github.kdroidfilter.kosherkotlin.** { *; }
-dontwarn io.github.kdroidfilter.kosherkotlin.**

# --- Fix: Community enum used with valueOf() for Kiddush Levana opinion selection ---
# The Community enum is resolved at runtime via Enum.valueOf(code) where code is stored
# in AppSettings. If R8/ProGuard optimizes or unboxes this enum, valueOf() will fail.
-keep enum io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community { *; }


# Filament JNI: filament-c resolves classes, methods and fields by name in JNI_OnLoad
# (e.g. FilaCallback.invoke(JJ)V), so nothing in the bindings may be shrunk or optimized away
-keep class io.github.erkko68.filament.** { *; }

# Nucleus launchers: not covered by the plugin's default rules (launcher-linux goes through D-Bus reflection)
-keep class dev.nucleusframework.launcher.windows.** { *; }
-keep class dev.nucleusframework.launcher.linux.** { *; }

# --- Fix: panel resize cursor (PointerIcon backed by AWT Cursor) not applied in release ---
# ComposeSceneMediator.setPointerIcon checks `pointerIcon instanceof AwtCursor` and then
# calls getCursor(). ProGuard optimization (class merging/inlining of this thin wrapper)
# breaks that check, so custom cursors built via PointerIcon(java.awt.Cursor) — e.g. the
# E_RESIZE/N_RESIZE handles in SplitPanes — silently fall back to the default arrow.
# Keep the desktop PointerIcon/AwtCursor classes intact so the instanceof + getCursor path works.
-keep class androidx.compose.ui.input.pointer.AwtCursor { *; }
-keep class androidx.compose.ui.input.pointer.PointerIcon_desktopKt { *; }
-keep class androidx.compose.ui.input.pointer.PointerIcon { *; }
-keep class androidx.compose.ui.input.pointer.PointerIcon$Companion { *; }

# --- Sentry crash reporting SDK ---
# Sentry uses reflection for serialization and event processing.
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

