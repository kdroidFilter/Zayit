@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

// The daily learning schedules of hebcal-learning, in one protobuf as the app's catalog: resources/limud/limud.pb
// (see NOTICE.txt there). An empty string stands for none.

@Serializable
internal class LimudSchedules(
    /** A day's mitzvos, from the cycle's first: "P1, N1, P2". */
    @ProtoNumber(1) val seferHamitzvos: List<String> = emptyList(),
    @ProtoNumber(2) val kitzur: List<KitzurMonth> = emptyList(),
    @ProtoNumber(3) val aruchHashulchan: List<AruchHashulchanDay> = emptyList(),
    @ProtoNumber(4) val chofetzChaimSimple: List<ChofetzChaimDay> = emptyList(),
    @ProtoNumber(5) val chofetzChaimLeap: List<ChofetzChaimDay> = emptyList(),
    @ProtoNumber(6) val shmirasHalashon: List<ShmirasHalashonDay> = emptyList(),
)

/** A Hebrew month's readings ([month] as KosherKotlin numbers it, Adar II after Adar): "133:17-133:21", "135:13-135:E". */
@Serializable
internal class KitzurMonth(
    @ProtoNumber(1) val month: Int = 0,
    @ProtoNumber(2) val readings: List<String> = emptyList(),
)

/** Its [section] (1 to 4) and "siman.seif-seif" or "siman.seif-siman.seif". */
@Serializable
internal class AruchHashulchanDay(
    @ProtoNumber(1) val section: Int = 0,
    @ProtoNumber(2) val reading: String = "",
)

/** Its three dates of the year, day and month each; [section], from [first] to [last] ("1.3", or "28" and "33,34"). */
@Serializable
internal class ChofetzChaimDay(
    @ProtoNumber(1) val dates: List<Int> = emptyList(),
    @ProtoNumber(2) val section: String = "",
    @ProtoNumber(3) val first: String = "",
    @ProtoNumber(4) val last: String = "",
)

/** Its date, in a year and in a leap year; its [book] (1 or 2) and [section], from [first] to [last]. */
@Serializable
internal class ShmirasHalashonDay(
    @ProtoNumber(1) val day: Int = 0,
    @ProtoNumber(2) val month: Int = 0,
    @ProtoNumber(3) val leapDay: Int = 0,
    @ProtoNumber(4) val leapMonth: Int = 0,
    @ProtoNumber(5) val book: Int = 0,
    @ProtoNumber(6) val section: String = "",
    @ProtoNumber(7) val first: String = "",
    @ProtoNumber(8) val last: String = "",
)

internal const val LIMUD_SCHEDULES_RESOURCE = "/limud/limud.pb"

internal val limudSchedules: LimudSchedules by lazy {
    val bytes =
        checkNotNull(LimudSchedules::class.java.getResourceAsStream(LIMUD_SCHEDULES_RESOURCE)) { "Missing $LIMUD_SCHEDULES_RESOURCE" }
            .use { it.readBytes() }
    ProtoBuf.decodeFromByteArray(LimudSchedules.serializer(), bytes)
}
