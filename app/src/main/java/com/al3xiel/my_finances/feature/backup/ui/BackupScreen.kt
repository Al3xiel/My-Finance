package com.al3xiel.my_finances.feature.backup.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** S70: lanza los selectores del sistema (SAF) y el permiso de notificaciones; el resto vive en [BackupContent]. */
@Composable
fun BackupScreen(modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        viewModel.onAction(BackupUiAction.CreatePickerResult(uri?.toString()))
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onAction(BackupUiAction.RestorePickerResult(uri?.toString()))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(BackupUiAction.PermissionResult(it))
    }

    LaunchedEffect(state.stage) {
        when (state.stage) {
            BackupStage.CREATE_PICKING -> {
                viewModel.onAction(BackupUiAction.CreatePickerOpened)
                createLauncher.launch("respaldo-${LocalDate.now()}.fbak")
            }
            BackupStage.RESTORE_PICKING -> {
                viewModel.onAction(BackupUiAction.RestorePickerOpened)
                openLauncher.launch(arrayOf("*/*"))
            }
            else -> Unit
        }
    }
    LaunchedEffect(state.requestPermission) {
        if (state.requestPermission) {
            viewModel.onAction(BackupUiAction.PermissionRequestLaunched)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackupContent(
        state = state,
        onAction = viewModel::onAction,
        hasPermission = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        modifier = modifier,
    )
}

private const val BACKUP_MIME_TYPE = "application/octet-stream"
