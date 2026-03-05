package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.icons.BootstrapPersonLines
import io.github.kdroidfilter.seforimapp.icons.LucideInspectionPanel
import io.github.kdroidfilter.seforimapp.icons.MaterialIconsDisplay_settings
import io.github.kdroidfilter.seforimapp.icons.RadixFontRoman
import io.github.kdroidfilter.seforimapp.icons.TablerInfoSquare
import io.github.kdroidfilter.seforimapp.icons.TablerLicense
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_category_about
import seforimapp.seforimapp.generated.resources.settings_category_conditions
import seforimapp.seforimapp.generated.resources.settings_category_display
import seforimapp.seforimapp.generated.resources.settings_category_fonts
import seforimapp.seforimapp.generated.resources.settings_category_general
import seforimapp.seforimapp.generated.resources.settings_category_profile

private data class SettingsItem(
    val label: String,
    val destination: SettingsDestination,
    val icon: ImageVector,
)

@Composable
fun SettingsSidebar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val backStack = navController.currentBackStackEntryAsState()
    val currentRoute =
        backStack.value
            ?.destination
            ?.route
            .orEmpty()

    val allItems =
        listOf(
            SettingsItem(
                label = stringResource(Res.string.settings_category_general),
                destination = SettingsDestination.General,
                icon = LucideInspectionPanel,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_display),
                destination = SettingsDestination.Display,
                icon = MaterialIconsDisplay_settings,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_profile),
                destination = SettingsDestination.Profile,
                icon = BootstrapPersonLines,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_fonts),
                destination = SettingsDestination.Fonts,
                icon = RadixFontRoman,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_about),
                destination = SettingsDestination.About,
                icon = TablerInfoSquare,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_conditions),
                destination = SettingsDestination.Conditions,
                icon = TablerLicense,
            ),
        )

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(allItems) { item ->
                val selected =
                    when (item.destination) {
                        is SettingsDestination.General -> currentRoute.contains("General")
                        is SettingsDestination.Display -> currentRoute.contains("Display")
                        is SettingsDestination.Profile -> currentRoute.contains("Profile")
                        is SettingsDestination.Fonts -> currentRoute.contains("Fonts")
                        is SettingsDestination.About -> currentRoute.contains("About")
                        is SettingsDestination.Conditions -> currentRoute.contains("Conditions")
                    }
                SidebarItem(
                    label = item.label,
                    icon = item.icon,
                    selected = selected,
                    onClick = { navController.navigate(item.destination) },
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val selectedBackground = accent.copy(alpha = 0.15f)
    val bg = if (selected) selectedBackground else JewelTheme.globalColors.panelBackground
    val iconTint = if (selected) JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.normal

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(
                        if (selected) accent else Color.Transparent,
                    ),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
