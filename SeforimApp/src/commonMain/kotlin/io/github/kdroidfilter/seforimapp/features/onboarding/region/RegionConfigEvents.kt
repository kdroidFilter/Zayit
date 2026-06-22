package io.github.kdroidfilter.seforimapp.features.onboarding.region

sealed interface RegionConfigEvents {
    data class SelectCountry(
        val index: Int,
    ) : RegionConfigEvents

    data class SelectCity(
        val index: Int,
    ) : RegionConfigEvents
}
