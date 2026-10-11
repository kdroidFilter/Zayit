package io.github.kdroidfilter.seforimapp.core.settings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/** How a setting's value is stored: as a string, the one type every store and the backup carry. */
class SettingCodec<T>(
    val encode: (T) -> String,
    /** Null for a value it can't read (hand edit, an entry since removed): the setting's default then. */
    val decode: (String) -> T?,
)

/** A typed setting saved under its own [key]; read, observed and written through [AppSettings]. */
class Setting<T> internal constructor(
    val key: String,
    val default: T,
    internal val codec: SettingCodec<T>,
)

/**
 * Settings saved under [prefix], declared as delegated properties named after their key:
 * `val showEarth by boolean(true)` is saved as `<prefix>.showEarth`. Reset all at once with [AppSettings.reset].
 */
abstract class SettingsGroup(
    val prefix: String,
) {
    private val declared = mutableListOf<Setting<*>>()

    /** Every setting of the group, in declaration order. */
    val all: List<Setting<*>> get() = declared

    protected fun <T> setting(
        default: T,
        codec: SettingCodec<T>,
    ) = PropertyDelegateProvider<SettingsGroup, ReadOnlyProperty<SettingsGroup, Setting<T>>> { _, property ->
        val setting = Setting("$prefix.${property.name}", default, codec).also(declared::add)
        ReadOnlyProperty { _, _ -> setting }
    }

    protected fun boolean(default: Boolean) = setting(default, SettingCodec(Boolean::toString, String::toBooleanStrictOrNull))

    protected fun float(default: Float) = setting(default, SettingCodec(Float::toString, String::toFloatOrNull))

    protected fun stringSet(default: Set<String>) = setting(default, SettingCodec({ it.joinToString(",") }, ::splitList))

    protected inline fun <reified E : Enum<E>> enumSet(default: Set<E>) =
        setting(
            default,
            SettingCodec(
                { set -> set.joinToString(",") { it.name } },
                { text -> splitList(text).mapNotNullTo(mutableSetOf()) { name -> enumValues<E>().find { it.name == name } } },
            ),
        )

    /** Null by default; an empty or unknown name reads as null. */
    protected inline fun <reified E : Enum<E>> enumOrNull() =
        setting<E?>(null, SettingCodec({ it?.name.orEmpty() }, { name -> enumValues<E>().find { it.name == name } }))

    protected inline fun <reified T> json(default: T) = setting(default, jsonCodec(serializer<T>()))

    protected fun <T> jsonCodec(serializer: KSerializer<T>) =
        SettingCodec<T>(
            { json.encodeToString(serializer, it) },
            { text -> runCatching { json.decodeFromString(serializer, text) }.getOrNull() },
        )

    protected companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun splitList(text: String): Set<String> = text.split(',').filterTo(mutableSetOf()) { it.isNotEmpty() }
    }
}
