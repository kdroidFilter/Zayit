package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.styling.Default
import org.jetbrains.jewel.intui.standalone.styling.Outlined
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.styling.outlined
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.component.styling.ComboBoxMetrics
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaMetrics
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import kotlin.time.Duration.Companion.milliseconds

private val roundedCorner = CornerSize(8.dp)
private val checkboxCorner = CornerSize(5.dp)

/**
 * Builds a [ComponentStyling] for the Islands theme with rounded corners
 * on all interactive components (buttons, text fields, combo boxes, checkboxes, tooltips).
 */
@OptIn(ExperimentalFoundationApi::class)
fun islandsComponentStyling(isDark: Boolean): ComponentStyling =
    if (isDark) {
        ComponentStyling.dark(
            defaultButtonStyle =
                ButtonStyle.Default.dark(
                    metrics = ButtonMetrics.default(cornerSize = roundedCorner),
                ),
            outlinedButtonStyle =
                ButtonStyle.Outlined.dark(
                    metrics = ButtonMetrics.outlined(cornerSize = roundedCorner),
                ),
            textFieldStyle =
                TextFieldStyle.dark(
                    metrics = TextFieldMetrics.defaults(cornerSize = roundedCorner),
                ),
            textAreaStyle =
                TextAreaStyle.dark(
                    metrics = TextAreaMetrics.defaults(cornerSize = roundedCorner),
                ),
            comboBoxStyle =
                ComboBoxStyle.Default.dark(
                    metrics = ComboBoxMetrics.default(cornerSize = roundedCorner),
                ),
            checkboxStyle =
                CheckboxStyle.dark(
                    metrics =
                        CheckboxMetrics.defaults(
                            outlineCornerSize = checkboxCorner,
                            outlineFocusedCornerSize = checkboxCorner,
                            outlineSelectedCornerSize = checkboxCorner,
                            outlineSelectedFocusedCornerSize = checkboxCorner,
                        ),
                ),
            tooltipStyle =
                TooltipStyle.dark(
                    intUiTooltipMetrics =
                        TooltipMetrics.defaults(
                            cornerSize = roundedCorner,
                            regularDisappearDelay = 10000.milliseconds,
                            fullDisappearDelay = 30000.milliseconds,
                        ),
                ),
        )
    } else {
        ComponentStyling.light(
            defaultButtonStyle =
                ButtonStyle.Default.light(
                    metrics = ButtonMetrics.default(cornerSize = roundedCorner),
                ),
            outlinedButtonStyle =
                ButtonStyle.Outlined.light(
                    metrics = ButtonMetrics.outlined(cornerSize = roundedCorner),
                ),
            textFieldStyle =
                TextFieldStyle.light(
                    metrics = TextFieldMetrics.defaults(cornerSize = roundedCorner),
                ),
            textAreaStyle =
                TextAreaStyle.light(
                    metrics = TextAreaMetrics.defaults(cornerSize = roundedCorner),
                ),
            comboBoxStyle =
                ComboBoxStyle.Default.light(
                    metrics = ComboBoxMetrics.default(cornerSize = roundedCorner),
                ),
            checkboxStyle =
                CheckboxStyle.light(
                    metrics =
                        CheckboxMetrics.defaults(
                            outlineCornerSize = checkboxCorner,
                            outlineFocusedCornerSize = checkboxCorner,
                            outlineSelectedCornerSize = checkboxCorner,
                            outlineSelectedFocusedCornerSize = checkboxCorner,
                        ),
                ),
            tooltipStyle =
                TooltipStyle.light(
                    intUiTooltipMetrics =
                        TooltipMetrics.defaults(
                            cornerSize = roundedCorner,
                            regularDisappearDelay = 10000.milliseconds,
                            fullDisappearDelay = 30000.milliseconds,
                        ),
                ),
        )
    }
