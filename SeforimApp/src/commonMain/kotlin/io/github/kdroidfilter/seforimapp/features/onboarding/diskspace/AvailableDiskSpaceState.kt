package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.runtime.Immutable

@Immutable
data class AvailableDiskSpaceState(
    val isLoading: Boolean = true,
    val hasEnoughSpace: Boolean = false,
    val availableDiskSpace: Long = 0,
    val remainingDiskSpaceAfterInstall: Long = 0,
    val totalDiskSpace: Long = 0,
)
