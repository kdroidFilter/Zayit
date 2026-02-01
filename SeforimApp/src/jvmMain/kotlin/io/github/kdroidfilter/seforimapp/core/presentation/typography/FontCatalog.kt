package io.github.kdroidfilter.seforimapp.core.presentation.typography

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.StringResource
import seforimapp.seforimapp.generated.resources.AharoniCLM_Bold
import seforimapp.seforimapp.generated.resources.AharoniCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.AharoniCLM_Book
import seforimapp.seforimapp.generated.resources.AharoniCLM_BookOblique
import seforimapp.seforimapp.generated.resources.BonaNovaSC_Bold
import seforimapp.seforimapp.generated.resources.BonaNovaSC_Italic
import seforimapp.seforimapp.generated.resources.BonaNovaSC_Regular
import seforimapp.seforimapp.generated.resources.Cardo_Bold
import seforimapp.seforimapp.generated.resources.Cardo_Italic
import seforimapp.seforimapp.generated.resources.Cardo_Regular
import seforimapp.seforimapp.generated.resources.DavidCLM_Bold
import seforimapp.seforimapp.generated.resources.DavidCLM_BoldItalic
import seforimapp.seforimapp.generated.resources.DavidCLM_Medium
import seforimapp.seforimapp.generated.resources.DavidCLM_MediumItalic
import seforimapp.seforimapp.generated.resources.DrugulinCLM_Bold
import seforimapp.seforimapp.generated.resources.ElliniaCLM_Bold
import seforimapp.seforimapp.generated.resources.ElliniaCLM_BoldItalic
import seforimapp.seforimapp.generated.resources.ElliniaCLM_Light
import seforimapp.seforimapp.generated.resources.ElliniaCLM_LightItalic
import seforimapp.seforimapp.generated.resources.FrankRuehlCLM_Bold
import seforimapp.seforimapp.generated.resources.FrankRuehlCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.FrankRuehlCLM_Medium
import seforimapp.seforimapp.generated.resources.FrankRuehlCLM_MediumOblique
import seforimapp.seforimapp.generated.resources.HadasimCLM_Bold
import seforimapp.seforimapp.generated.resources.HadasimCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.HadasimCLM_Regular
import seforimapp.seforimapp.generated.resources.HadasimCLM_RegularOblique
import seforimapp.seforimapp.generated.resources.KeterAramTsova
import seforimapp.seforimapp.generated.resources.KeterYG_Bold
import seforimapp.seforimapp.generated.resources.KeterYG_BoldOblique
import seforimapp.seforimapp.generated.resources.KeterYG_Medium
import seforimapp.seforimapp.generated.resources.KeterYG_MediumOblique
import seforimapp.seforimapp.generated.resources.LibertinusSerif_Bold
import seforimapp.seforimapp.generated.resources.LibertinusSerif_BoldItalic
import seforimapp.seforimapp.generated.resources.LibertinusSerif_Italic
import seforimapp.seforimapp.generated.resources.LibertinusSerif_Regular
import seforimapp.seforimapp.generated.resources.LibertinusSerif_SemiBold
import seforimapp.seforimapp.generated.resources.LibertinusSerif_SemiBoldItalic
import seforimapp.seforimapp.generated.resources.MiriamCLM_Bold
import seforimapp.seforimapp.generated.resources.MiriamCLM_Book
import seforimapp.seforimapp.generated.resources.MiriamLibre_VariableFont_wght
import seforimapp.seforimapp.generated.resources.MiriamMonoCLM_Bold
import seforimapp.seforimapp.generated.resources.MiriamMonoCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.MiriamMonoCLM_Book
import seforimapp.seforimapp.generated.resources.MiriamMonoCLM_BookOblique
import seforimapp.seforimapp.generated.resources.NachlieliCLM_Bold
import seforimapp.seforimapp.generated.resources.NachlieliCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.NachlieliCLM_Light
import seforimapp.seforimapp.generated.resources.NachlieliCLM_LightOblique
import seforimapp.seforimapp.generated.resources.RashiAmiti
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.ShofarDemi_Bold
import seforimapp.seforimapp.generated.resources.ShofarDemi_BoldOblique
import seforimapp.seforimapp.generated.resources.ShofarRegular
import seforimapp.seforimapp.generated.resources.ShofarRegularOblique
import seforimapp.seforimapp.generated.resources.SimpleCLM_Bold
import seforimapp.seforimapp.generated.resources.SimpleCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.SimpleCLM_Medium
import seforimapp.seforimapp.generated.resources.SimpleCLM_MediumOblique
import seforimapp.seforimapp.generated.resources.StamAshkenazCLM
import seforimapp.seforimapp.generated.resources.StamSefaradCLM
import seforimapp.seforimapp.generated.resources.TaameyAshkenaz_Bold
import seforimapp.seforimapp.generated.resources.TaameyAshkenaz_BoldOblique
import seforimapp.seforimapp.generated.resources.TaameyAshkenaz_Medium
import seforimapp.seforimapp.generated.resources.TaameyAshkenaz_MediumOblique
import seforimapp.seforimapp.generated.resources.TaameyDavidCLM_Bold
import seforimapp.seforimapp.generated.resources.TaameyDavidCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.TaameyDavidCLM_Medium
import seforimapp.seforimapp.generated.resources.TaameyDavidCLM_MediumOblique
import seforimapp.seforimapp.generated.resources.TaameyFrankCLM_Bold
import seforimapp.seforimapp.generated.resources.TaameyFrankCLM_BoldOblique
import seforimapp.seforimapp.generated.resources.TaameyFrankCLM_Medium
import seforimapp.seforimapp.generated.resources.TaameyFrankCLM_MediumOblique
import seforimapp.seforimapp.generated.resources.Tinos_Bold
import seforimapp.seforimapp.generated.resources.Tinos_BoldItalic
import seforimapp.seforimapp.generated.resources.Tinos_Italic
import seforimapp.seforimapp.generated.resources.Tinos_Regular
import seforimapp.seforimapp.generated.resources.YehudaCLM_Bold
import seforimapp.seforimapp.generated.resources.YehudaCLM_Light
import seforimapp.seforimapp.generated.resources.font_aharoni_clm
import seforimapp.seforimapp.generated.resources.font_bona_nova_sc
import seforimapp.seforimapp.generated.resources.font_cardo
import seforimapp.seforimapp.generated.resources.font_david_clm
import seforimapp.seforimapp.generated.resources.font_drugulin_clm
import seforimapp.seforimapp.generated.resources.font_ellinia_clm
import seforimapp.seforimapp.generated.resources.font_frank_ruehl_clm
import seforimapp.seforimapp.generated.resources.font_frank_ruhl_libre
import seforimapp.seforimapp.generated.resources.font_hadasim_clm
import seforimapp.seforimapp.generated.resources.font_keter_aram_tsova
import seforimapp.seforimapp.generated.resources.font_keter_yg
import seforimapp.seforimapp.generated.resources.font_libertinus_serif
import seforimapp.seforimapp.generated.resources.font_miriam_clm
import seforimapp.seforimapp.generated.resources.font_miriam_libre
import seforimapp.seforimapp.generated.resources.font_miriam_mono_clm
import seforimapp.seforimapp.generated.resources.font_nachlieli_clm
import seforimapp.seforimapp.generated.resources.font_noto_rashi_hebrew
import seforimapp.seforimapp.generated.resources.font_noto_serif_hebrew
import seforimapp.seforimapp.generated.resources.font_rashi_amiti
import seforimapp.seforimapp.generated.resources.font_shofar
import seforimapp.seforimapp.generated.resources.font_simple_clm
import seforimapp.seforimapp.generated.resources.font_stam_ashkenaz_clm
import seforimapp.seforimapp.generated.resources.font_stam_sefarad_clm
import seforimapp.seforimapp.generated.resources.font_taamey_ashkenaz
import seforimapp.seforimapp.generated.resources.font_taamey_david_clm
import seforimapp.seforimapp.generated.resources.font_taamey_frank_clm
import seforimapp.seforimapp.generated.resources.font_tinos
import seforimapp.seforimapp.generated.resources.font_yehuda_clm
import seforimapp.seforimapp.generated.resources.frankruhllibre
import seforimapp.seforimapp.generated.resources.notorashihebrew
import seforimapp.seforimapp.generated.resources.notoserifhebrew

/**
 * Central catalog of font families available in the app, with stable codes and localized labels.
 * Codes are persisted in settings; do not change them without a migration.
 */
@Immutable
data class FontOption(
    val code: String,
    val label: StringResource,
)

object FontCatalog {
    // Public list of options in a stable order (no Composable calls here)
    val options: List<FontOption> =
        listOf(
            FontOption(code = "notoserifhebrew", label = Res.string.font_noto_serif_hebrew),
            FontOption(code = "notorashihebrew", label = Res.string.font_noto_rashi_hebrew),
            FontOption(code = "frankruhllibre", label = Res.string.font_frank_ruhl_libre),
            FontOption(code = "miriamlibre", label = Res.string.font_miriam_libre),
            FontOption(code = "cardo", label = Res.string.font_cardo),
            FontOption(code = "tinos", label = Res.string.font_tinos),
            FontOption(code = "libertinusserif", label = Res.string.font_libertinus_serif),
            FontOption(code = "bonanovasc", label = Res.string.font_bona_nova_sc),
            FontOption(code = "keteraramtsova", label = Res.string.font_keter_aram_tsova),
            FontOption(code = "keteryg", label = Res.string.font_keter_yg),
            FontOption(code = "rashiamiti", label = Res.string.font_rashi_amiti),
            FontOption(code = "shofar", label = Res.string.font_shofar),
            FontOption(code = "taameyashkenaz", label = Res.string.font_taamey_ashkenaz),
            FontOption(code = "taameydavidclm", label = Res.string.font_taamey_david_clm),
            FontOption(code = "taameyfrankclm", label = Res.string.font_taamey_frank_clm),
            // Culmus fonts
            FontOption(code = "aharoniclm", label = Res.string.font_aharoni_clm),
            FontOption(code = "davidclm", label = Res.string.font_david_clm),
            FontOption(code = "drugulinclm", label = Res.string.font_drugulin_clm),
            FontOption(code = "elliniaclm", label = Res.string.font_ellinia_clm),
            FontOption(code = "frankruehlclm", label = Res.string.font_frank_ruehl_clm),
            FontOption(code = "hadasimclm", label = Res.string.font_hadasim_clm),
            FontOption(code = "miriamclm", label = Res.string.font_miriam_clm),
            FontOption(code = "miriammonoclm", label = Res.string.font_miriam_mono_clm),
            FontOption(code = "nachlieliclm", label = Res.string.font_nachlieli_clm),
            FontOption(code = "simpleclm", label = Res.string.font_simple_clm),
            FontOption(code = "stamashkenazclm", label = Res.string.font_stam_ashkenaz_clm),
            FontOption(code = "stamsefaradclm", label = Res.string.font_stam_sefarad_clm),
            FontOption(code = "yehudaclm", label = Res.string.font_yehuda_clm),
        )

    @Composable
    fun familyFor(code: String): FontFamily =
        when (code) {
            "notoserifhebrew" -> FontFamily(Font(resource = Res.font.notoserifhebrew, weight = FontWeight.Normal))
            "notorashihebrew" -> FontFamily(Font(resource = Res.font.notorashihebrew, weight = FontWeight.Normal))
            "frankruhllibre" -> FontFamily(Font(resource = Res.font.frankruhllibre, weight = FontWeight.Normal))
            "miriamlibre" ->
                FontFamily(
                    Font(resource = Res.font.MiriamLibre_VariableFont_wght, weight = FontWeight.Normal),
                    Font(resource = Res.font.MiriamLibre_VariableFont_wght, weight = FontWeight.Medium),
                    Font(resource = Res.font.MiriamLibre_VariableFont_wght, weight = FontWeight.SemiBold),
                    Font(resource = Res.font.MiriamLibre_VariableFont_wght, weight = FontWeight.Bold),
                )
            "cardo" ->
                FontFamily(
                    Font(resource = Res.font.Cardo_Regular, weight = FontWeight.Normal),
                    Font(resource = Res.font.Cardo_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.Cardo_Italic, style = FontStyle.Italic),
                )
            "tinos" ->
                FontFamily(
                    Font(resource = Res.font.Tinos_Regular, weight = FontWeight.Normal),
                    Font(resource = Res.font.Tinos_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.Tinos_Italic, style = FontStyle.Italic),
                    Font(resource = Res.font.Tinos_BoldItalic, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "libertinusserif" ->
                FontFamily(
                    Font(resource = Res.font.LibertinusSerif_Regular, weight = FontWeight.Normal),
                    Font(resource = Res.font.LibertinusSerif_Italic, style = FontStyle.Italic),
                    Font(resource = Res.font.LibertinusSerif_SemiBold, weight = FontWeight.SemiBold),
                    Font(resource = Res.font.LibertinusSerif_SemiBoldItalic, weight = FontWeight.SemiBold, style = FontStyle.Italic),
                    Font(resource = Res.font.LibertinusSerif_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.LibertinusSerif_BoldItalic, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "bonanovasc" ->
                FontFamily(
                    Font(resource = Res.font.BonaNovaSC_Regular, weight = FontWeight.Normal),
                    Font(resource = Res.font.BonaNovaSC_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.BonaNovaSC_Italic, style = FontStyle.Italic),
                )
            "keteraramtsova" ->
                FontFamily(
                    Font(resource = Res.font.KeterAramTsova, weight = FontWeight.Normal),
                )
            "keteryg" ->
                FontFamily(
                    Font(resource = Res.font.KeterYG_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.KeterYG_MediumOblique, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.KeterYG_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.KeterYG_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "rashiamiti" ->
                FontFamily(
                    Font(resource = Res.font.RashiAmiti, weight = FontWeight.Normal),
                )
            "shofar" ->
                FontFamily(
                    Font(resource = Res.font.ShofarRegular, weight = FontWeight.Normal),
                    Font(resource = Res.font.ShofarRegularOblique, style = FontStyle.Italic),
                    Font(resource = Res.font.ShofarDemi_Bold, weight = FontWeight.SemiBold),
                    Font(resource = Res.font.ShofarDemi_BoldOblique, weight = FontWeight.SemiBold, style = FontStyle.Italic),
                )
            "taameyashkenaz" ->
                FontFamily(
                    Font(resource = Res.font.TaameyAshkenaz_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.TaameyAshkenaz_MediumOblique, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.TaameyAshkenaz_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.TaameyAshkenaz_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "taameydavidclm" ->
                FontFamily(
                    Font(resource = Res.font.TaameyDavidCLM_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.TaameyDavidCLM_MediumOblique, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.TaameyDavidCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.TaameyDavidCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "taameyfrankclm" ->
                FontFamily(
                    Font(resource = Res.font.TaameyFrankCLM_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.TaameyFrankCLM_MediumOblique, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.TaameyFrankCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.TaameyFrankCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            // Culmus fonts
            "aharoniclm" ->
                FontFamily(
                    Font(resource = Res.font.AharoniCLM_Book, weight = FontWeight.Normal),
                    Font(resource = Res.font.AharoniCLM_BookOblique, weight = FontWeight.Normal, style = FontStyle.Italic),
                    Font(resource = Res.font.AharoniCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.AharoniCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "davidclm" ->
                FontFamily(
                    Font(resource = Res.font.DavidCLM_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.DavidCLM_MediumItalic, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.DavidCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.DavidCLM_BoldItalic, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "drugulinclm" ->
                FontFamily(
                    Font(resource = Res.font.DrugulinCLM_Bold, weight = FontWeight.Bold),
                )
            "elliniaclm" ->
                FontFamily(
                    Font(resource = Res.font.ElliniaCLM_Light, weight = FontWeight.Light),
                    Font(resource = Res.font.ElliniaCLM_LightItalic, weight = FontWeight.Light, style = FontStyle.Italic),
                    Font(resource = Res.font.ElliniaCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.ElliniaCLM_BoldItalic, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "frankruehlclm" ->
                FontFamily(
                    Font(resource = Res.font.FrankRuehlCLM_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.FrankRuehlCLM_MediumOblique, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.FrankRuehlCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.FrankRuehlCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "hadasimclm" ->
                FontFamily(
                    Font(resource = Res.font.HadasimCLM_Regular, weight = FontWeight.Normal),
                    Font(resource = Res.font.HadasimCLM_RegularOblique, weight = FontWeight.Normal, style = FontStyle.Italic),
                    Font(resource = Res.font.HadasimCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.HadasimCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "miriamclm" ->
                FontFamily(
                    Font(resource = Res.font.MiriamCLM_Book, weight = FontWeight.Normal),
                    Font(resource = Res.font.MiriamCLM_Bold, weight = FontWeight.Bold),
                )
            "miriammonoclm" ->
                FontFamily(
                    Font(resource = Res.font.MiriamMonoCLM_Book, weight = FontWeight.Normal),
                    Font(resource = Res.font.MiriamMonoCLM_BookOblique, weight = FontWeight.Normal, style = FontStyle.Italic),
                    Font(resource = Res.font.MiriamMonoCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.MiriamMonoCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "nachlieliclm" ->
                FontFamily(
                    Font(resource = Res.font.NachlieliCLM_Light, weight = FontWeight.Light),
                    Font(resource = Res.font.NachlieliCLM_LightOblique, weight = FontWeight.Light, style = FontStyle.Italic),
                    Font(resource = Res.font.NachlieliCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.NachlieliCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "simpleclm" ->
                FontFamily(
                    Font(resource = Res.font.SimpleCLM_Medium, weight = FontWeight.Medium),
                    Font(resource = Res.font.SimpleCLM_MediumOblique, weight = FontWeight.Medium, style = FontStyle.Italic),
                    Font(resource = Res.font.SimpleCLM_Bold, weight = FontWeight.Bold),
                    Font(resource = Res.font.SimpleCLM_BoldOblique, weight = FontWeight.Bold, style = FontStyle.Italic),
                )
            "stamashkenazclm" ->
                FontFamily(
                    Font(resource = Res.font.StamAshkenazCLM, weight = FontWeight.Normal),
                )
            "stamsefaradclm" ->
                FontFamily(
                    Font(resource = Res.font.StamSefaradCLM, weight = FontWeight.Normal),
                )
            "yehudaclm" ->
                FontFamily(
                    Font(resource = Res.font.YehudaCLM_Light, weight = FontWeight.Light),
                    Font(resource = Res.font.YehudaCLM_Bold, weight = FontWeight.Bold),
                )
            else -> FontFamily(Font(resource = Res.font.notoserifhebrew, weight = FontWeight.Normal))
        }
}
