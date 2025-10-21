package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

data class AvailableDiskSpaceState(
    val hasEnoughSpace: Boolean,
    val availableDiskSpace: Long,
    val remainingDiskSpaceAfter15Gb: Long,
) {
    companion object {
        val hasEnoughSpace = AvailableDiskSpaceState(true, 1000000000000, 1000000000000)
        val noEnoughSpace = AvailableDiskSpaceState(false, 1000000, 100000)
    }
}
