package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.nucleus.window.DecoratedDialog
import io.github.kdroidfilter.nucleus.window.DialogTitleBar
import io.github.kdroidfilter.nucleus.window.NucleusDecoratedWindowTheme
import io.github.kdroidfilter.nucleus.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils.buildThemeDefinition
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsNavHost
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingsSidebar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

@Composable
fun SettingsWindow(onClose: () -> Unit) {
    SettingsWindowView(
        onClose = onClose,
    )
}

@Composable
private fun SettingsWindowView(onClose: () -> Unit) {
    val isDark = ThemeUtils.isDarkTheme()
    val themeDefinition = buildThemeDefinition()

    NucleusDecoratedWindowTheme(isDark = isDark, titleBarStyle = ThemeUtils.buildCustomTitleBarStyle()) {
        IntUiTheme(
            theme = themeDefinition,
            styling = ComponentStyling.default(),
        ) {
            val settingsDialogState =
                rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(700.dp, 500.dp))
            DecoratedDialog(
                onCloseRequest = onClose,
                title = stringResource(Res.string.settings),
                icon = painterResource(Res.drawable.AppIcon),
                state = settingsDialogState,
                visible = true,
                resizable = true,
            ) {
                val background = JewelTheme.globalColors.panelBackground
                LaunchedEffect(window, background) { window.background = java.awt.Color(background.toArgb()) }

                val windowViewModelOwner = rememberWindowViewModelStoreOwner()
                CompositionLocalProvider(
                    LocalWindowViewModelStoreOwner provides windowViewModelOwner,
                    LocalViewModelStoreOwner provides windowViewModelOwner,
                ) {
                    DialogTitleBar(
                        modifier = Modifier.newFullscreenControls(),
                        gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Transparent,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                AllIconsKeys.General.Settings,
                                contentDescription = null,
                                tint = JewelTheme.globalColors.text.normal,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(stringResource(Res.string.settings))
                        }
                    }
                    // IntelliJ-like layout: sidebar + content with header and bottom action bar
                    val navController = rememberNavController()
                    Column(
                        modifier =
                            Modifier
                                .trackActivation()
                                .fillMaxSize()
                                .background(JewelTheme.globalColors.panelBackground)
                                .padding(16.dp),
                    ) {
                        Row(modifier = Modifier.weight(1f)) {
                            SettingsSidebar(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .width(120.dp),
                                navController = navController,
                            )

                            // Vertical separator between the menu and content
                            Divider(
                                orientation = Orientation.Vertical,
                                color = JewelTheme.globalColors.borders.disabled,
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 8.dp),
                            )

                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(start = 16.dp),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SettingsNavHost(navController = navController)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Divider(orientation = Orientation.Horizontal)
                        Spacer(Modifier.height(8.dp))

                        // Bottom action bar aligned to the end
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DefaultButton(onClick = onClose) { Text(stringResource(Res.string.settings_close)) }
                        }
                    }
                }
            }
        }
    } // NucleusDecoratedWindowTheme
}
