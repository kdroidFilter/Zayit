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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_category_fonts
import seforimapp.seforimapp.generated.resources.settings_category_general
import seforimapp.seforimapp.generated.resources.settings_category_profile
import seforimapp.seforimapp.generated.resources.settings_category_region

private data class SettingsItem(val label: String, val destination: SettingsDestination)

@Composable
fun SettingsSidebar(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    val backStack = navController.currentBackStackEntryAsState()
    val currentRoute = backStack.value?.destination?.route.orEmpty()

    val allItems = listOf(
        SettingsItem(label = stringResource(Res.string.settings_category_general), destination = SettingsDestination.General),
        SettingsItem(label = stringResource(Res.string.settings_category_profile), destination = SettingsDestination.Profile),
        SettingsItem(label = stringResource(Res.string.settings_category_region), destination = SettingsDestination.Region),
        SettingsItem(label = stringResource(Res.string.settings_category_fonts), destination = SettingsDestination.Fonts),
    )

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(allItems) { item ->
                val selected = when (item.destination) {
                    is SettingsDestination.General -> currentRoute.contains("General")
                    is SettingsDestination.Profile -> currentRoute.contains("Profile")
                    is SettingsDestination.Region -> currentRoute.contains("Region")
                    is SettingsDestination.Fonts -> currentRoute.contains("Fonts")
                }
                SidebarItem(
                    label = item.label,
                    selected = selected,
                    onClick = { navController.navigate(item.destination) }
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) JewelTheme.globalColors.panelBackground else JewelTheme.globalColors.panelBackground
    // Add a slim indicator and emphasize the selected item label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(if (selected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.panelBackground)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
