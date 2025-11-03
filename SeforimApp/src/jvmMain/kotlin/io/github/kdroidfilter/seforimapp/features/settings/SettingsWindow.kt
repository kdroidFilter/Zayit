package io.github.kdroidfilter.seforimapp.features.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils.buildThemeDefinition
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsNavHost
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingsSidebar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.settings
import seforimapp.seforimapp.generated.resources.settings_cancel
import seforimapp.seforimapp.generated.resources.settings_category_fonts
import seforimapp.seforimapp.generated.resources.settings_category_general
import seforimapp.seforimapp.generated.resources.settings_category_profile
import seforimapp.seforimapp.generated.resources.settings_category_region
import seforimapp.seforimapp.generated.resources.settings_ok

@Composable
fun SettingsWindow(onClose: () -> Unit) {
    SettingsWindowView(
        onClose = onClose,
    )
}

@Composable
private fun SettingsWindowView(
    onClose: () -> Unit,

) {
    val themeDefinition = buildThemeDefinition()

    IntUiTheme(
        theme = themeDefinition, styling = ComponentStyling.default().decoratedWindow(
                titleBarStyle = ThemeUtils.pickTitleBarStyle(),
            )
    ) {
        val settingsWindowState = remember { getCenteredWindowState(1000, 700) }
        DecoratedWindow(
            onCloseRequest = onClose,
            title = stringResource(Res.string.settings),
            icon = painterResource(Res.drawable.AppIcon),
            state = settingsWindowState,
            visible = true,
            resizable = true,
        ) {
            val background = JewelTheme.globalColors.panelBackground
            LaunchedEffect(window, background) { window.background = java.awt.Color(background.toArgb()) }

            val isMac = getOperatingSystem() == OperatingSystem.MACOS
            val isWindows = getOperatingSystem() == OperatingSystem.WINDOWS
            TitleBar(modifier = Modifier.newFullscreenControls()) {
                Box(
                    modifier = Modifier.fillMaxWidth(if (isMac) 0.9f else 1f)
                        .padding(start = if (isWindows) 70.dp else 0.dp)
                ) {
                    val centerOffset = 40.dp
                    Row(
                        modifier = Modifier.align(Alignment.Center).offset(x = centerOffset),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            AllIconsKeys.General.Settings,
                            contentDescription = null,
                            tint = JewelTheme.globalColors.text.normal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(stringResource(Res.string.settings))
                    }
                }
            }
            // IntelliJ-like layout: sidebar + content with header and bottom action bar
            val navController = rememberNavController()
            Column(
                modifier = Modifier
                    .trackActivation()
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    SettingsSidebar(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(260.dp),
                        navController = navController
                    )

                    // Vertical separator between the menu and content
                    Divider(
                        orientation = Orientation.Vertical,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp)
                    ) {
                        // Content header resembling IntelliJ Settings breadcrumb
                        val currentRoute = navController.currentBackStackEntryFlow
                            .collectAsState(initial = navController.currentBackStackEntry)
                            .value?.destination?.route.orEmpty()
                        val headerText = when {
                            currentRoute.contains("General") -> stringResource(Res.string.settings_category_general)
                            currentRoute.contains("Profile") -> stringResource(Res.string.settings_category_profile)
                            currentRoute.contains("Region") -> stringResource(Res.string.settings_category_region)
                            currentRoute.contains("Fonts") -> stringResource(Res.string.settings_category_fonts)
                            else -> stringResource(Res.string.settings)
                        }

                        Text(
                            text = headerText,
                            fontSize = JewelTheme.typography.h2TextStyle.fontSize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Divider(orientation = Orientation.Horizontal)
                        Spacer(Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            SettingsNavHost(navController = navController)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Divider(orientation = Orientation.Horizontal)
                Spacer(Modifier.height(8.dp))

                // Bottom action bar aligned to the end (Confirm applies)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onClose) { Text(stringResource(Res.string.settings_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    DefaultButton(onClick = onClose) { Text(stringResource(Res.string.settings_ok)) }
                }
            }
        }
    }
}
