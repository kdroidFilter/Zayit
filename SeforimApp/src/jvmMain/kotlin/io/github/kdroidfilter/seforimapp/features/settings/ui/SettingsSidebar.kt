package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_category_fonts
import seforimapp.seforimapp.generated.resources.settings_category_general
import seforimapp.seforimapp.generated.resources.settings_category_profile
import seforimapp.seforimapp.generated.resources.settings_category_region

@Composable
fun SettingsSidebar(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    val backStack = navController.currentBackStackEntryAsState()
    val currentRoute = backStack.value?.destination?.route.orEmpty()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SidebarButton(
            text = stringResource(Res.string.settings_category_general),
            selected = currentRoute.contains("General"),
            onClick = { navController.navigate(SettingsDestination.General) }
        )
        SidebarButton(
            text = stringResource(Res.string.settings_category_profile),
            selected = currentRoute.contains("Profile"),
            onClick = { navController.navigate(SettingsDestination.Profile) }
        )
        SidebarButton(
            text = stringResource(Res.string.settings_category_region),
            selected = currentRoute.contains("Region"),
            onClick = { navController.navigate(SettingsDestination.Region) }
        )
        SidebarButton(
            text = stringResource(Res.string.settings_category_fonts),
            selected = currentRoute.contains("Fonts"),
            onClick = { navController.navigate(SettingsDestination.Fonts) }
        )
    }
}

@Composable
private fun SidebarButton(text: String, selected: Boolean, onClick: () -> Unit) {
    DefaultButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(text)
    }
}
