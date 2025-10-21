package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.runtime.Immutable

@Immutable
data class AvailableDiskSpaceState(
    val hasEnoughSpace: Boolean = false,
    val availableDiskSpace: Long = 0,
    val remainingDiskSpaceAfter15Gb: Long = 0
)
