package com.windowhyun.health.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.windowhyun.health.ui.components.ConfirmDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatWeight
import com.windowhyun.health.domain.model.ThemeMode

/** 설정. 홈 우측 상단 버튼으로만 들어온다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SettingSectionTitle("운동") }

            item {
                StepperRow(
                    title = "기본 휴식시간",
                    value = "${settings.defaultRestSeconds}초",
                    onMinus = { viewModel.setDefaultRestSeconds(settings.defaultRestSeconds - 15) },
                    onPlus = { viewModel.setDefaultRestSeconds(settings.defaultRestSeconds + 15) },
                )
            }

            item {
                SwitchRow(
                    title = "세트 완료 시 휴식 타이머 자동 시작",
                    checked = settings.restTimerAutoStart,
                    onCheckedChange = viewModel::setRestAutoStart,
                )
            }

            item {
                SwitchRow(
                    title = "진동 알림",
                    subtitle = "휴식 타이머가 끝나면 진동으로 알려 줍니다.",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = viewModel::setVibration,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingSectionTitle("단위") }

            item {
                ChipRow(
                    title = "중량 단위",
                    options = WeightUnit.entries.map { it to it.label },
                    selected = settings.weightUnit,
                    onSelect = viewModel::setWeightUnit,
                )
            }

            item {
                ChipRow(
                    title = "거리 단위",
                    options = DistanceUnit.entries.map { it to it.label },
                    selected = settings.distanceUnit,
                    onSelect = viewModel::setDistanceUnit,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingSectionTitle("러닝") }

            item {
                StepperRow(
                    title = "자동 Lap 거리",
                    value = "${settings.autoLapMeters}m",
                    onMinus = { viewModel.setAutoLapMeters(settings.autoLapMeters - 100) },
                    onPlus = { viewModel.setAutoLapMeters(settings.autoLapMeters + 100) },
                )
            }

            item {
                StepperRow(
                    title = "체중",
                    subtitle = "러닝 칼로리 추정에 사용합니다.",
                    value = formatWeight(settings.bodyWeightKg, settings.weightUnit),
                    onMinus = { viewModel.setBodyWeightKg(settings.bodyWeightKg - 0.5) },
                    onPlus = { viewModel.setBodyWeightKg(settings.bodyWeightKg + 0.5) },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingSectionTitle("화면") }

            item {
                ChipRow(
                    title = "테마",
                    options = ThemeMode.entries.map { it to it.label },
                    selected = settings.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingSectionTitle("연동 / 데이터") }

            item {
                DisabledRow(
                    title = "Health Connect 연결",
                    subtitle = "Phase 4 에서 추가됩니다. 연결하지 않아도 앱 기록은 그대로 사용할 수 있습니다.",
                )
            }
            item { DataSection() }
        }
    }
}

/**
 * 백업 / 복원 / CSV 내보내기.
 *
 * 서버가 없는 앱이라 기기를 잃어버리면 기록도 같이 사라진다.
 * 백업 파일이 유일한 복구 수단이므로 설정 맨 위가 아니라도 눈에 띄게 둔다.
 */
@Composable
private fun DataSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }

    // 백업 파일을 application/json 으로 저장하지 않는 앱도 있어서 전체를 받는다.
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri }

    val createWorkoutCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportWorkoutCsv) }

    val createRunCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportRunCsv) }

    Column {
        ActionRow(
            title = "백업 파일 만들기",
            subtitle = "모든 기록과 설정을 JSON 파일 하나로 저장합니다.",
            enabled = !state.working,
            onClick = { createBackup.launch(viewModel.backupFileName()) },
        )
        ActionRow(
            title = "백업에서 복원",
            subtitle = "지금 기록을 모두 지우고 파일의 내용으로 바꿉니다.",
            enabled = !state.working,
            onClick = { openBackup.launch(arrayOf("*/*")) },
        )
        ActionRow(
            title = "헬스 기록 CSV 내보내기",
            subtitle = "엑셀이나 스프레드시트에서 볼 수 있습니다.",
            enabled = !state.working,
            onClick = { createWorkoutCsv.launch(viewModel.workoutCsvFileName()) },
        )
        ActionRow(
            title = "러닝 기록 CSV 내보내기",
            subtitle = "엑셀이나 스프레드시트에서 볼 수 있습니다.",
            enabled = !state.working,
            onClick = { createRunCsv.launch(viewModel.runCsvFileName()) },
        )

        if (state.working) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        state.message?.let { message ->
            ResultText(text = message, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let { error ->
            ResultText(text = error, color = MaterialTheme.colorScheme.error)
        }
    }

    pendingRestoreUri?.let { uri ->
        ConfirmDialog(
            title = "지금 기록을 모두 바꿀까요?",
            message = "복원하면 이 기기에 있는 운동·러닝·루틴 기록이 모두 지워지고 " +
                "백업 파일의 내용으로 바뀝니다. 되돌릴 수 없습니다.",
            confirmLabel = "복원",
            onConfirm = {
                pendingRestoreUri = null
                viewModel.restoreBackup(uri)
            },
            onDismiss = { pendingRestoreUri = null },
        )
    }
}

@Composable
private fun ResultText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "줄이기") }
        Text(value, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "늘리기") }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChipRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (option, label) ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun DisabledRow(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
