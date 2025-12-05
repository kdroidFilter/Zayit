package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_cycle_card_subtitle
import seforimapp.seforimapp.generated.resources.home_cycle_card_title
import seforimapp.seforimapp.generated.resources.home_cycle_chip_solar_noon
import seforimapp.seforimapp.generated.resources.home_cycle_chip_twilight
import seforimapp.seforimapp.generated.resources.home_lunar_card_subtitle
import seforimapp.seforimapp.generated.resources.home_lunar_card_title
import seforimapp.seforimapp.generated.resources.home_lunar_chip_day
import seforimapp.seforimapp.generated.resources.home_lunar_label_illumination
import seforimapp.seforimapp.generated.resources.home_lunar_label_moonrise
import seforimapp.seforimapp.generated.resources.home_lunar_label_moonset
import seforimapp.seforimapp.generated.resources.home_lunar_next_full_moon_label
import seforimapp.seforimapp.generated.resources.home_lunar_next_full_moon_value
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_detail
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_subtitle
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_title
import seforimapp.seforimapp.generated.resources.home_widget_card_noon_detail
import seforimapp.seforimapp.generated.resources.home_widget_card_noon_subtitle
import seforimapp.seforimapp.generated.resources.home_widget_card_noon_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_detail
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_subtitle
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_detail
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_subtitle
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_title
import seforimapp.seforimapp.generated.resources.home_widget_label_astronomical_dawn
import seforimapp.seforimapp.generated.resources.home_widget_label_night
import seforimapp.seforimapp.generated.resources.home_widget_label_noon
import seforimapp.seforimapp.generated.resources.home_widget_label_sunrise
import seforimapp.seforimapp.generated.resources.home_widget_label_sunset
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_detail
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_subtitle
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_title

private data class DayMarker(
    val label: StringResource,
    val time: String,
    val position: Float,
    val color: Color
)

private data class DayMomentCardData(
    val title: StringResource,
    val time: String,
    val subtitle: StringResource,
    val detail: StringResource,
    val accentStart: Color,
    val accentEnd: Color
)

private data class LunarCycleData(
    val dayValue: String,
    val illuminationPercent: Int,
    val moonriseTime: String,
    val moonsetTime: String,
    val nextFullMoonIn: String
)

@Composable
fun HomeCelestialWidgets(modifier: Modifier = Modifier) {
    val markers = listOf(
        DayMarker(Res.string.home_widget_label_astronomical_dawn, "04:57", 0.05f, Color(0xFFC084FC)),
        DayMarker(Res.string.home_widget_label_sunrise, "05:41", 0.18f, Color(0xFFFFD166)),
        DayMarker(Res.string.home_widget_label_noon, "12:01", 0.52f, Color(0xFFFFAD61)),
        DayMarker(Res.string.home_widget_label_sunset, "19:13", 0.78f, Color(0xFF7CB7FF)),
        DayMarker(Res.string.home_widget_label_night, "20:41", 0.94f, Color(0xFFAEB8FF))
    )

    val momentCards = listOf(
        DayMomentCardData(
            title = Res.string.home_widget_card_first_light_title,
            time = "04:57",
            subtitle = Res.string.home_widget_card_first_light_subtitle,
            detail = Res.string.home_widget_card_first_light_detail,
            accentStart = Color(0xFF8AB4F8),
            accentEnd = Color(0xFFC3DAFE)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_sunrise_title,
            time = "05:41",
            subtitle = Res.string.home_widget_card_sunrise_subtitle,
            detail = Res.string.home_widget_card_sunrise_detail,
            accentStart = Color(0xFFFFCA7A),
            accentEnd = Color(0xFFFFE0A3)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_noon_title,
            time = "12:01",
            subtitle = Res.string.home_widget_card_noon_subtitle,
            detail = Res.string.home_widget_card_noon_detail,
            accentStart = Color(0xFFFFA94D),
            accentEnd = Color(0xFFFFC58A)
        ),
        DayMomentCardData(
            title = Res.string.home_widget_card_sunset_title,
            time = "19:13",
            subtitle = Res.string.home_widget_card_sunset_subtitle,
            detail = Res.string.home_widget_card_sunset_detail,
            accentStart = Color(0xFF9CB9FF),
            accentEnd = Color(0xFFB6D4FF)
        )
    )

    val lunarCycle = LunarCycleData(
        dayValue = "10.3",
        illuminationPercent = 78,
        moonriseTime = "16:42",
        moonsetTime = "03:18",
        nextFullMoonIn = "4"
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val contentSpacing = 12.dp
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
//            DayCycleCard(markers = markers)

            Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {

                DayMomentsGrid(
                    cards = momentCards,
                    maxWidth = availableWidth,
                    horizontalSpacing = contentSpacing
                )
                VisibleStarsCard(
                    title = Res.string.home_widget_visible_stars_title,
                    time = "20:41",
                    subtitle = Res.string.home_widget_visible_stars_subtitle,
                    detail = Res.string.home_widget_visible_stars_detail
                )
                LunarCycleCard(data = lunarCycle)

            }
        }
    }
}

@Composable
private fun LunarCycleCard(data: LunarCycleData, modifier: Modifier = Modifier) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(22.dp)
    val background = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0D142E), Color(0xFF0D1C36)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF7FAFF), Color(0xFFEFF3FF)))
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E8F5)
    val chipBackground = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.9f)
    val chipBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFDCE4F6)
    val textColor = JewelTheme.globalColors.text.normal
    val secondary = textColor.copy(alpha = 0.76f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoonPhaseIcon(isDark = isDark)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.home_lunar_card_title),
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.home_lunar_card_subtitle),
                            color = secondary,
                            fontSize = 12.sp
                        )
                    }
                }
                PillChip(
                    text = stringResource(Res.string.home_lunar_chip_day, data.dayValue),
                    backgroundColor = chipBackground,
                    borderColor = chipBorder,
                    dotStart = Color(0xFF7C8FF5),
                    dotEnd = Color(0xFF4F6BDE)
                )
            }

            MoonIllustration(isDark = isDark)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.home_lunar_label_illumination),
                        color = secondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${data.illuminationPercent}%",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                IlluminationBar(
                    progress = data.illuminationPercent / 100f,
                    isDark = isDark
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MoonEventCard(
                    label = Res.string.home_lunar_label_moonrise,
                    value = data.moonriseTime,
                    accentStart = Color(0xFFA5C6FF),
                    accentEnd = Color(0xFFCBE0FF),
                    modifier = Modifier.weight(1f)
                )
                MoonEventCard(
                    label = Res.string.home_lunar_label_moonset,
                    value = data.moonsetTime,
                    accentStart = Color(0xFFB5B2FF),
                    accentEnd = Color(0xFFD7D4FF),
                    modifier = Modifier.weight(1f)
                )
            }

            NextFullMoonBar(
                label = stringResource(Res.string.home_lunar_next_full_moon_label),
                value = stringResource(Res.string.home_lunar_next_full_moon_value, data.nextFullMoonIn),
                isDark = isDark
            )
        }
    }
}

@Composable
private fun MoonIllustration(isDark: Boolean, modifier: Modifier = Modifier) {
    val glowGradient = if (isDark) {
        Brush.radialGradient(listOf(Color(0x33263A75), Color(0xFF0D142E)))
    } else {
        Brush.radialGradient(listOf(Color(0x33C7D9FF), Color(0xFFF6F8FF)))
    }
    val moonGradient = if (isDark) {
        Brush.radialGradient(listOf(Color(0xFFBCC8FF), Color(0xFF6878A7)))
    } else {
        Brush.radialGradient(listOf(Color(0xFFE9EDF8), Color(0xFFA7B8D7)))
    }
    val shadowColor = if (isDark) Color(0xFF0B1328) else Color(0xFFEEF1FB)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(170.dp)
                .background(glowGradient, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(122.dp)
                .background(moonGradient, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(122.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, shadowColor.copy(alpha = 0.9f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.White.copy(alpha = 0.35f), CircleShape)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White.copy(alpha = 0.28f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White.copy(alpha = 0.32f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun IlluminationBar(progress: Float, isDark: Boolean, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    val trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE0E7FF)
    val fillGradient = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF8DA2FF), Color(0xFFB4C6FF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF7A98FF), Color(0xFFB7C9FF)))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(fillGradient)
        )
    }
}

@Composable
private fun MoonEventCard(
    label: StringResource,
    value: String,
    accentStart: Color,
    accentEnd: Color,
    modifier: Modifier = Modifier
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(16.dp)
    val background = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0F172E), Color(0xFF0D1935)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF8FBFF), Color(0xFFEFF4FF)))
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFE1E8F5)
    val textColor = JewelTheme.globalColors.text.normal
    val secondary = textColor.copy(alpha = 0.75f)

    Column(
        modifier = modifier
            .height(90.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientDot(accentStart, accentEnd, size = 10.dp)
            Text(
                text = stringResource(label),
                color = secondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = value,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun NextFullMoonBar(label: String, value: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val background = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF1B1E4E), Color(0xFF232568)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFE9E4FF), Color(0xFFD8D3FF)))
    }
    val labelColor = if (isDark) Color(0xFFCBD4FF) else Color(0xFF4B5563)
    val valueColor = if (isDark) Color(0xFFCED7FF) else Color(0xFF4C37D8)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MoonPhaseIcon(isDark: Boolean, modifier: Modifier = Modifier) {
    val gradient = if (isDark) {
        Brush.radialGradient(listOf(Color(0xFFCCD7FF), Color(0xFF6C7CB2)))
    } else {
        Brush.radialGradient(listOf(Color(0xFFDDE6FF), Color(0xFF8AA9F7)))
    }
    val shadow = if (isDark) Color(0xFF0F162E) else Color(0xFFF6F8FF)

    Box(
        modifier = modifier
            .size(28.dp)
            .background(gradient, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
                .clip(CircleShape)
                .background(shadow)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}

@Composable
private fun DayCycleCard(markers: List<DayMarker>, modifier: Modifier = Modifier) {
    if (markers.isEmpty()) return
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(22.dp)
    val background = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0E132E), Color(0xFF0C1D34)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF6F8FF), Color(0xFFE9F0FF)))
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E8F5)
    val chipBackground = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.8f)
    val chipBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFDCE4F6)
    val barGradient = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF161C46),
                Color(0xFF1E3A8A),
                Color(0xFFFFC857),
                Color(0xFFD97706),
                Color(0xFF0B132B)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFB5C7FF),
                Color(0xFFFFE2A0),
                Color(0xFFF9C07B),
                Color(0xFF8EC5FF),
                Color(0xFFDEE8FF)
            )
        )
    }
    val textColor = JewelTheme.globalColors.text.normal

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientDot(
                        colorStart = Color(0xFFF0A6FF),
                        colorEnd = Color(0xFF7CD7F9),
                        size = 12.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.home_cycle_card_title),
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.home_cycle_card_subtitle),
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillChip(
                        text = stringResource(Res.string.home_cycle_chip_twilight, markers.first().time),
                        backgroundColor = chipBackground,
                        borderColor = chipBorder,
                        dotStart = Color(0xFF9F7AEA),
                        dotEnd = Color(0xFF60A5FA)
                    )
                    val noonTime = markers.getOrNull(2)?.time ?: ""
                    PillChip(
                        text = stringResource(Res.string.home_cycle_chip_solar_noon, noonTime),
                        backgroundColor = chipBackground,
                        borderColor = chipBorder,
                        dotStart = Color(0xFFFFB347),
                        dotEnd = Color(0xFFFFD186)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(barGradient)
                    .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sorted = markers.sortedBy { it.position }
                    var previous = 0f
                    sorted.forEachIndexed { index, marker ->
                        val gap = (marker.position - previous).coerceAtLeast(0f)
                        if (gap > 0f) {
                            Spacer(Modifier.weight(gap))
                        }
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(marker.color.copy(alpha = 0.95f))
                        )
                        previous = marker.position
                        if (index == sorted.lastIndex) {
                            val tail = (1f - previous).coerceAtLeast(0f)
                            if (tail > 0f) {
                                Spacer(Modifier.weight(tail))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                markers.forEach { marker ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        GradientDot(
                            colorStart = marker.color,
                            colorEnd = marker.color.copy(alpha = 0.9f),
                            size = 10.dp
                        )
                        Text(
                            text = marker.time,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stringResource(marker.label),
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMomentsGrid(
    cards: List<DayMomentCardData>,
    maxWidth: Dp,
    horizontalSpacing: Dp,
    modifier: Modifier = Modifier
) {
    val minCardWidth = 130.dp
    val twoColumnsMinWidth = minCardWidth * 2 + horizontalSpacing
    val columns = if (maxWidth >= twoColumnsMinWidth) 2 else 1
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(horizontalSpacing)
    ) {
        cards.chunked(columns).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                rowCards.forEach { card ->
                    DayMomentCard(
                        data = card,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayMomentCard(data: DayMomentCardData, modifier: Modifier = Modifier) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val background = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0F1B2F), Color(0xFF0B1827)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF8FBFF), Color(0xFFEFF4FF)))
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E7F5)
    val labelColor = JewelTheme.globalColors.text.normal.copy(alpha = 0.75f)

    Box(
        modifier = modifier
            .height(120.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(data.title),
                color = labelColor,
                fontSize = 12.sp
            )
            Text(
                text = data.time,
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientDot(data.accentStart, data.accentEnd)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(data.subtitle),
                        color = JewelTheme.globalColors.text.normal,
                        fontSize = 13.sp
                    )
                    Text(
                        text = stringResource(data.detail),
                        color = labelColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VisibleStarsCard(
    title: StringResource,
    time: String,
    subtitle: StringResource,
    detail: StringResource,
    modifier: Modifier = Modifier
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val background = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF0D1935),
                Color(0xFF0F2D63),
                Color(0xFF0B1F45)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFD7E4FF),
                Color(0xFFB4CCFF),
                Color(0xFF9AB8FF)
            )
        )
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E8F5)
    val textColor = JewelTheme.globalColors.text.normal
    val secondary = textColor.copy(alpha = 0.76f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(title),
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = time,
                color = Color(0xFFFCD34D),
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientDot(Color(0xFFFBBF24), Color(0xFFF59E0B), size = 12.dp)
                    GradientDot(Color(0xFFFBBF24), Color(0xFFF59E0B), size = 12.dp)
                    GradientDot(Color(0xFFFBBF24), Color(0xFFF59E0B), size = 12.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(subtitle),
                        color = textColor,
                        fontSize = 13.sp
                    )
                    Text(
                        text = stringResource(detail),
                        color = secondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PillChip(
    text: String,
    backgroundColor: Color,
    borderColor: Color,
    dotStart: Color,
    dotEnd: Color,
    modifier: Modifier = Modifier
) {
    val chipTextColor = if (JewelTheme.isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientDot(dotStart, dotEnd, size = 10.dp)
        Text(
            text = text,
            color = chipTextColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GradientDot(
    colorStart: Color,
    colorEnd: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.radialGradient(listOf(colorStart, colorEnd)),
                shape = CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
    )
}

@Preview
@Composable
private fun HomeCelestialWidgetsPreview() {
    PreviewContainer {
        HomeCelestialWidgets()
    }
}
