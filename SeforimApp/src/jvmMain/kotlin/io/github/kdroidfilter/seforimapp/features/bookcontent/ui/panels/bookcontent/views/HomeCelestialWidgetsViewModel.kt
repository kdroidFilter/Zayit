package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.zmanim.data.Place
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeCelestialWidgetsState(
    val userPlace: Place,
    val userCityLabel: String?
) {
    companion object {
        val preview = HomeCelestialWidgetsState(
            userPlace = DEFAULT_PLACE,
            userCityLabel = null
        )
    }
}

private val DEFAULT_PLACE = Place(31.7683, 35.2137, 800.0)

@ContributesIntoMap(AppScope::class)
@ViewModelKey(HomeCelestialWidgetsViewModel::class)
@Inject
class HomeCelestialWidgetsViewModel : ViewModel() {

    private val _state = MutableStateFlow(resolveState())
    val state: StateFlow<HomeCelestialWidgetsState> = _state.asStateFlow()

    private fun resolveState(): HomeCelestialWidgetsState {
        val country = AppSettings.getRegionCountry()
        val city = AppSettings.getRegionCity()
        val place = if (!country.isNullOrBlank() && !city.isNullOrBlank()) {
            worldPlaces[country]?.get(city)
        } else {
            null
        }

        return HomeCelestialWidgetsState(
            userPlace = place ?: DEFAULT_PLACE,
            userCityLabel = city?.takeIf { it.isNotBlank() }
        )
    }
}
