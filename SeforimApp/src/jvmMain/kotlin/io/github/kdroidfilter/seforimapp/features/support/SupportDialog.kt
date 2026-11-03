package io.github.kdroidfilter.seforimapp.features.support

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.components.HeartIcon
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.support_app_mishkan
import seforimapp.seforimapp.generated.resources.support_app_mishkan_description
import seforimapp.seforimapp.generated.resources.support_app_mishkan_name
import seforimapp.seforimapp.generated.resources.support_app_shechita
import seforimapp.seforimapp.generated.resources.support_app_shechita_description
import seforimapp.seforimapp.generated.resources.support_app_shechita_name
import seforimapp.seforimapp.generated.resources.support_app_teiva
import seforimapp.seforimapp.generated.resources.support_app_teiva_description
import seforimapp.seforimapp.generated.resources.support_app_teiva_name
import seforimapp.seforimapp.generated.resources.support_close
import seforimapp.seforimapp.generated.resources.support_dialog_apps
import seforimapp.seforimapp.generated.resources.support_dialog_call
import seforimapp.seforimapp.generated.resources.support_dialog_headline
import seforimapp.seforimapp.generated.resources.support_dialog_investment
import seforimapp.seforimapp.generated.resources.support_dialog_title
import seforimapp.seforimapp.generated.resources.support_open_in_store

/** A paid app of the publisher on the Microsoft Store, identified by its Store product id. */
private data class StoreApp(
    val productId: String,
    val icon: DrawableResource,
    val name: StringResource,
    val description: StringResource,
)

private val storeApps =
    listOf(
        StoreApp(
            "9N5FCSX9G9W7",
            Res.drawable.support_app_teiva,
            Res.string.support_app_teiva_name,
            Res.string.support_app_teiva_description,
        ),
        StoreApp(
            "9NPBK0PV2LJ4",
            Res.drawable.support_app_mishkan,
            Res.string.support_app_mishkan_name,
            Res.string.support_app_mishkan_description,
        ),
        StoreApp(
            "9MX2955KD2SF",
            Res.drawable.support_app_shechita,
            Res.string.support_app_shechita_name,
            Res.string.support_app_shechita_description,
        ),
    )

/** The app's page in the Microsoft Store app. */
private fun storeUri(productId: String) = "ms-windows-store://pdp/?ProductId=$productId"

/** Invites to support the work by buying one of the publisher's paid apps on the Microsoft Store. */
@Composable
fun NucleusApplicationScope.SupportDialog(onClose: () -> Unit) {
    IntUiTheme(
        theme = ThemeUtils.buildThemeDefinition(),
        styling = ThemeUtils.buildComponentStyling(),
    ) {
        val dialogState =
            rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(560.dp, 680.dp))
        JewelDecoratedDialog(
            onCloseRequest = onClose,
            title = stringResource(Res.string.support_dialog_title),
            icon = painterResource(Res.drawable.AppIcon),
            state = dialogState,
            visible = true,
            resizable = false,
        ) {
            JewelDialogTitleBar(
                modifier = Modifier.newFullscreenControls(),
                gradientStartColor = ThemeUtils.titleBarGradientColor(),
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        HeartIcon,
                        contentDescription = null,
                        tint = JewelTheme.globalColors.text.normal,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(stringResource(Res.string.support_dialog_title))
                }
            }

            Column(
                modifier =
                    Modifier
                        .trackActivation()
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.support_dialog_headline),
                        textAlign = TextAlign.Center,
                        fontSize = JewelTheme.typography.h2TextStyle.fontSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                    listOf(
                        Res.string.support_dialog_investment,
                        Res.string.support_dialog_apps,
                        Res.string.support_dialog_call,
                    ).forEach { paragraph ->
                        Text(
                            text = stringResource(paragraph),
                            modifier = Modifier.fillMaxWidth(),
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    storeApps.forEach { StoreAppCard(it) }
                }

                Spacer(Modifier.height(8.dp))
                Divider(orientation = Orientation.Horizontal)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onClose) {
                        Text(stringResource(Res.string.support_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreAppCard(app: StoreApp) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(app.icon),
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(app.name), fontWeight = FontWeight.SemiBold)
            Text(stringResource(app.description), color = JewelTheme.globalColors.text.info)
        }
        DefaultButton(onClick = { UrlOpener.open(storeUri(app.productId)) }) {
            Text(stringResource(Res.string.support_open_in_store))
        }
    }
}
