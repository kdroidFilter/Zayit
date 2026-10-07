package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.zmanim.data.ISRAEL_COUNTRY_NAME
import io.github.kdroidfilter.seforimapp.features.zmanim.data.Place
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUserLocation(
    val userPlace: Place,
    val userCityLabel: String?,
    val inIsrael: Boolean,
) {
    companion object {
        val preview =
            HomeUserLocation(
                userPlace = DEFAULT_PLACE,
                userCityLabel = null,
                inIsrael = true,
            )
    }
}

private val DEFAULT_PLACE = Place(31.7683, 35.2137, 800.0, "Asia/Jerusalem")

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class HomeUserLocationViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(resolveState())
    val state: StateFlow<HomeUserLocation> = _state.asStateFlow()

    private fun resolveState(): HomeUserLocation {
        val country = appSettings.getRegionCountry()
        val city = appSettings.getRegionCity()
        val place =
            if (!country.isNullOrBlank() && !city.isNullOrBlank()) {
                worldPlaces[country]?.get(city)
            } else {
                null
            }

        return HomeUserLocation(
            userPlace = place ?: DEFAULT_PLACE,
            userCityLabel = city?.takeIf { it.isNotBlank() },
            inIsrael = country == ISRAEL_COUNTRY_NAME,
        )
    }
}
