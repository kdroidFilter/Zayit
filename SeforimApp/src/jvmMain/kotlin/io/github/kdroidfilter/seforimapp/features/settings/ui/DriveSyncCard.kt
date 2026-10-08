package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.backup.drive.DriveSyncError
import io.github.kdroidfilter.seforimapp.backup.drive.DriveSyncState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.drive_backing_up
import seforimapp.seforimapp.generated.resources.drive_backup_now
import seforimapp.seforimapp.generated.resources.drive_cancel
import seforimapp.seforimapp.generated.resources.drive_connect
import seforimapp.seforimapp.generated.resources.drive_connected
import seforimapp.seforimapp.generated.resources.drive_connected_as
import seforimapp.seforimapp.generated.resources.drive_connecting
import seforimapp.seforimapp.generated.resources.drive_description
import seforimapp.seforimapp.generated.resources.drive_disconnect
import seforimapp.seforimapp.generated.resources.drive_error_backup
import seforimapp.seforimapp.generated.resources.drive_error_connect
import seforimapp.seforimapp.generated.resources.drive_error_no_backup
import seforimapp.seforimapp.generated.resources.drive_error_permission
import seforimapp.seforimapp.generated.resources.drive_error_restore
import seforimapp.seforimapp.generated.resources.drive_error_revoked
import seforimapp.seforimapp.generated.resources.drive_error_unavailable
import seforimapp.seforimapp.generated.resources.drive_last_backup
import seforimapp.seforimapp.generated.resources.drive_no_backup_yet
import seforimapp.seforimapp.generated.resources.drive_pending_remote
import seforimapp.seforimapp.generated.resources.drive_restore
import seforimapp.seforimapp.generated.resources.drive_restore_staged
import seforimapp.seforimapp.generated.resources.drive_restore_warning
import seforimapp.seforimapp.generated.resources.drive_restoring
import seforimapp.seforimapp.generated.resources.drive_title
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_no
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_yes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val backupDateFormatter =
    // Numeric, as the notes' dates: a localized style would follow the JVM locale, not the app's Hebrew
    DateTimeFormatter.ofPattern("dd.MM.yy · HH:mm").withZone(ZoneId.systemDefault())

private fun formatBackupDate(epochMs: Long): String = backupDateFormatter.format(Instant.ofEpochMilli(epochMs))

@Composable
internal fun DriveSyncCard(
    state: DriveSyncState,
    onConnect: () -> Unit,
    onCancelConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var confirmRestore by remember { mutableStateOf(false) }
    val account = state.account
    val busy = state.operation != null

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(Res.string.drive_title), fontSize = 15.sp)
                Text(
                    text = account?.let { accountSummary(it.email, it.lastBackupEpochMs) } ?: stringResource(Res.string.drive_description),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            when {
                state.isConnecting ->
                    OutlinedButton(onClick = onCancelConnect) { Text(stringResource(Res.string.drive_cancel)) }
                account == null ->
                    OutlinedButton(onClick = onConnect) { Text(stringResource(Res.string.drive_connect)) }
                else ->
                    OutlinedButton(onClick = onDisconnect, enabled = !busy) { Text(stringResource(Res.string.drive_disconnect)) }
            }
        }

        if (state.isConnecting) {
            Text(text = stringResource(Res.string.drive_connecting), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        }

        account?.pendingRemoteBackupEpochMs?.let {
            InlineWarningBanner(
                text = stringResource(Res.string.drive_pending_remote, formatBackupDate(it)),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (account != null && !confirmRestore) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackup, enabled = !busy) {
                    val backingUp = state.operation == DriveSyncState.Operation.BackingUp
                    Text(stringResource(if (backingUp) Res.string.drive_backing_up else Res.string.drive_backup_now))
                }
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !busy) {
                    val restoring = state.operation == DriveSyncState.Operation.Restoring
                    Text(stringResource(if (restoring) Res.string.drive_restoring else Res.string.drive_restore))
                }
            }
        }

        if (confirmRestore) {
            InlineErrorBanner(text = stringResource(Res.string.drive_restore_warning), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(
                    onClick = {
                        confirmRestore = false
                        onRestore()
                    },
                ) { Text(stringResource(Res.string.settings_reset_confirm_yes)) }
                OutlinedButton(onClick = { confirmRestore = false }) { Text(stringResource(Res.string.settings_reset_confirm_no)) }
            }
        }

        if (state.restoreStaged) {
            InlineSuccessBanner(text = stringResource(Res.string.drive_restore_staged), modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { error ->
            InlineErrorBanner(text = stringResource(error.message()), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun accountSummary(
    email: String?,
    lastBackupEpochMs: Long?,
): String {
    val who = email?.let { stringResource(Res.string.drive_connected_as, it) } ?: stringResource(Res.string.drive_connected)
    val last =
        lastBackupEpochMs?.let { stringResource(Res.string.drive_last_backup, formatBackupDate(it)) }
            ?: stringResource(Res.string.drive_no_backup_yet)
    return "$who · $last"
}

private fun DriveSyncError.message() =
    when (this) {
        DriveSyncError.ConnectFailed -> Res.string.drive_error_connect
        DriveSyncError.PermissionNotGranted -> Res.string.drive_error_permission
        DriveSyncError.DriveUnavailable -> Res.string.drive_error_unavailable
        DriveSyncError.BackupFailed -> Res.string.drive_error_backup
        DriveSyncError.RestoreFailed -> Res.string.drive_error_restore
        DriveSyncError.NoBackup -> Res.string.drive_error_no_backup
        DriveSyncError.Revoked -> Res.string.drive_error_revoked
    }
