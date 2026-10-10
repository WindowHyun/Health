package com.windowhyun.health.ui.settings

import kotlin.math.roundToInt
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.Hairline
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.windowhyun.health.domain.model.AppSettings
import com.windowhyun.health.domain.model.PipSpace
import com.windowhyun.health.domain.model.PipSpacePosition
import com.windowhyun.health.domain.model.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 설정. 홈 우측 상단 버튼으로만 들어온다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    healthConnectViewModel: HealthConnectViewModel = hiltViewModel(),
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 32.dp),
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

            item { SettingSectionTitle("러닝") }

            item {
                SwitchRow(
                    title = "자동 일시정지",
                    subtitle = "멈추면 시간도 멈추고, 다시 움직이면 이어서 기록합니다.",
                    checked = settings.autoPauseRun,
                    onCheckedChange = viewModel::setAutoPauseRun,
                )
            }

            item {
                SwitchRow(
                    title = "진동 안내",
                    subtitle = "구간(Lap)마다, 목표를 채웠을 때, 자동 일시정지될 때 진동으로 알려 줍니다.",
                    checked = settings.runVibrationCues,
                    onCheckedChange = viewModel::setRunVibrationCues,
                )
            }

            item {
                SwitchRow(
                    title = "음성 안내",
                    subtitle = "구간마다 거리 · 페이스 · 시간을 읽어 주고, 목표 달성과 인터벌 전환도 알려 줍니다. 이어폰을 끼고 달릴 때 편합니다.",
                    checked = settings.runVoiceCues,
                    onCheckedChange = viewModel::setRunVoiceCues,
                )
            }

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

            item { SettingSectionTitle("PiP 자리") }

            item {
                SwitchRow(
                    title = "PiP 자리 비우기",
                    subtitle = "유튜브 같은 앱을 PiP(작은 창)로 띄우면 화면 아래나 위를 덮습니다. 켜면 그 크기만큼 앱 화면 끝을 비워 버튼과 숫자가 가려지지 않게 합니다. 홈 화면 오른쪽 위 버튼으로도 켜고 끌 수 있습니다.",
                    checked = settings.pipSpaceEnabled,
                    onCheckedChange = viewModel::setPipSpaceEnabled,
                )
            }

            item {
                ChipRow(
                    title = "비울 곳",
                    options = PipSpacePosition.entries.map { it to it.label },
                    selected = settings.pipSpacePosition,
                    onSelect = viewModel::setPipSpacePosition,
                )
            }

            item {
                ChipRow(
                    title = "크기",
                    options = PipSpace.PRESETS.map { (label, dp) -> dp to label },
                    selected = settings.pipSpaceHeightDp,
                    onSelect = viewModel::setPipSpaceHeightDp,
                )
            }

            item {
                StepperRow(
                    title = "높이 맞추기",
                    subtitle = "PiP 창의 위치와 크기는 앱이 알 수 없어서 직접 맞춰야 합니다. 켠 채로 조절하면 바로 반영됩니다.",
                    value = "${settings.pipSpaceHeightDp}dp",
                    onMinus = { viewModel.setPipSpaceHeightDp(settings.pipSpaceHeightDp - PipSpace.STEP_DP) },
                    onPlus = { viewModel.setPipSpaceHeightDp(settings.pipSpaceHeightDp + PipSpace.STEP_DP) },
                )
            }

            item { SettingSectionTitle("주간 목표") }

            item {
                StepperRow(
                    title = "헬스",
                    subtitle = "정하면 홈에서 이번 주 진행을 볼 수 있습니다.",
                    value = if (settings.weeklyWorkoutGoal == 0) "끔" else "주 ${settings.weeklyWorkoutGoal}회",
                    onMinus = { viewModel.setWeeklyWorkoutGoal(settings.weeklyWorkoutGoal - 1) },
                    onPlus = { viewModel.setWeeklyWorkoutGoal(settings.weeklyWorkoutGoal + 1) },
                )
            }

            item {
                val unit = settings.distanceUnit
                // 한 번에 5(km 또는 mile)씩. 목표는 늘 미터로 저장한다.
                val step = unit.toMeters(WEEKLY_RUN_STEP).roundToInt()
                StepperRow(
                    title = "러닝 거리",
                    value = if (settings.weeklyRunGoalMeters == 0) {
                        "끔"
                    } else {
                        "주 ${unit.fromMeters(settings.weeklyRunGoalMeters.toDouble()).roundToInt()}${unit.label}"
                    },
                    onMinus = { viewModel.setWeeklyRunGoalMeters(settings.weeklyRunGoalMeters - step) },
                    onPlus = { viewModel.setWeeklyRunGoalMeters(settings.weeklyRunGoalMeters + step) },
                )
            }

            item { SettingSectionTitle("화면") }

            item {
                ChipRow(
                    title = "테마",
                    options = ThemeMode.entries.map { it to it.label },
                    selected = settings.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }

            item { SettingSectionTitle("Health Connect") }
            item { HealthConnectSection(healthConnectViewModel) }

            item { SettingSectionTitle("데이터") }
            item { DataSection(backupViewModel) }
        }
    }
}

/**
 * Health Connect 연동. 끝난 러닝과 헬스 운동을 다른 건강 앱과 나눌 수 있게 내보내고,
 * 원하면 Health Connect 의 최근 체중을 가져와 러닝 칼로리 계산에 쓴다.
 * 시계 · 다른 앱이 남긴 심박은 러닝 상세에서 보여 준다.
 */
@Composable
private fun HealthConnectSection(viewModel: HealthConnectViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val requestPermissions = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    // 허용 화면이나 Health Connect 설정에서 돌아오면 권한이 바뀌었을 수 있다.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    when (state.availability) {
        com.windowhyun.health.domain.model.HealthConnectAvailability.UNAVAILABLE -> Text(
            text = "이 기기에서는 Health Connect 를 쓸 수 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        com.windowhyun.health.domain.model.HealthConnectAvailability.NEEDS_UPDATE -> Column {
            Text(
                text = "Health Connect 앱을 설치하거나 업데이트해야 합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            TextButton(onClick = { openHealthConnectInStore(context) }) { Text("설치 · 업데이트") }
        }

        com.windowhyun.health.domain.model.HealthConnectAvailability.AVAILABLE -> Column {
            SwitchRow(
                title = "Health Connect 로 내보내기",
                subtitle = healthConnectStatus(state),
                checked = state.exporting,
                onCheckedChange = { on ->
                    if (!on) {
                        viewModel.setExportEnabled(false)
                    } else if (state.permissions.canWrite) {
                        viewModel.setExportEnabled(true)
                    } else {
                        requestPermissions.launch(viewModel.permissionsToRequest)
                    }
                },
            )
            if (state.exporting) {
                SwitchRow(
                    title = "체중 가져오기",
                    subtitle = "Health Connect 의 최근 체중을 러닝 칼로리 계산에 씁니다. 설정의 체중이 그 값으로 바뀝니다.",
                    checked = state.weightImporting,
                    onCheckedChange = { on ->
                        if (!on) {
                            viewModel.setImportWeight(false)
                        } else if (state.permissions.canReadWeight) {
                            viewModel.setImportWeight(true)
                        } else {
                            requestPermissions.launch(viewModel.permissionsToRequest)
                            viewModel.setImportWeight(true)
                        }
                    },
                )
                TextButton(onClick = viewModel::syncNow, enabled = !state.syncing) {
                    Text(if (state.syncing) "동기화 중..." else "지금 동기화")
                }
            }
        }
    }
}

/** 스위치 아래 한 줄: 지금 어떤 상태인지(권한 · 실패 · 마지막 동기화). */
internal fun healthConnectStatus(state: HealthConnectUiState): String = when {
    state.permissionDenied || state.needsPermission ->
        "권한이 꺼져 있습니다. 켜면 허용 화면이 열립니다."
    state.lastError != null -> "동기화하지 못했습니다: ${state.lastError}"
    state.exporting && state.lastSyncAt > 0 -> "마지막 동기화 ${formatBackupTime(state.lastSyncAt)}"
    state.exporting -> "끝난 러닝과 헬스 운동을 보냅니다."
    else -> "끝난 러닝 · 헬스 운동을 다른 건강 앱과 나눕니다. 켜면 허용 화면이 열립니다."
}

private fun openHealthConnectInStore(context: android.content.Context) {
    val packageName = "com.google.android.apps.healthdata"
    val market = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
}

/**
 * 백업 / 복원 / CSV 내보내기.
 *
 * 서버가 없는 앱이라 기기를 잃어버리면 기록도 같이 사라진다.
 * 백업 파일이 유일한 복구 수단이므로 설정 맨 위가 아니라도 눈에 띄게 둔다.
 */
@Composable
private fun DataSection(viewModel: BackupViewModel) {
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

    val chooseAutoBackupFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::chooseAutoBackupFolder) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column {
        AutoBackupBlock(
            settings = settings,
            enabled = !state.working,
            onChooseFolder = { chooseAutoBackupFolder.launch(null) },
            onSetDays = viewModel::setAutoBackupEveryDays,
            onBackupNow = viewModel::runAutoBackupNow,
            onDisable = viewModel::disableAutoBackup,
        )
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
            ResultText(text = message, color = MaterialTheme.colorScheme.onSurface)
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

/**
 * 정해 둔 폴더에 주기적으로 백업. 앱을 켤 때 확인하고 최근 5개만 남긴다.
 * 꺼져 있을 때는 한 줄짜리 행 하나만 보이고, 켜면 간격과 결과가 펼쳐진다.
 */
@Composable
private fun AutoBackupBlock(
    settings: AppSettings,
    enabled: Boolean,
    onChooseFolder: () -> Unit,
    onSetDays: (Int) -> Unit,
    onBackupNow: () -> Unit,
    onDisable: () -> Unit,
) {
    val folder = settings.autoBackupFolderUri
    ActionRow(
        title = if (folder == null) "자동 백업" else "자동 백업 · 켜짐",
        subtitle = if (folder == null) {
            "고른 폴더에 백업 파일을 주기적으로 만듭니다. 최근 5개만 남깁니다."
        } else {
            "폴더: ${autoBackupFolderLabel(folder)} (눌러서 바꾸기)"
        },
        enabled = enabled,
        onClick = onChooseFolder,
    )
    if (folder == null) return

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("간격", style = MaterialTheme.typography.bodyMedium)
            AUTO_BACKUP_INTERVALS.forEach { (days, label) ->
                FilterChip(
                    selected = settings.autoBackupEveryDays == days,
                    onClick = { onSetDays(days) },
                    label = { Text(label) },
                )
            }
        }
        val last = if (settings.lastAutoBackupAt > 0) {
            "마지막 백업: ${formatBackupTime(settings.lastAutoBackupAt)}"
        } else {
            "아직 백업하지 않았습니다."
        }
        Text(
            text = last,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        settings.lastAutoBackupError?.let { error ->
            Text(
                text = "자동 백업 실패: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = onBackupNow, enabled = enabled) { Text("지금 백업") }
            TextButton(onClick = onDisable, enabled = enabled) { Text("끄기") }
        }
    }
    Hairline()
}

private val AUTO_BACKUP_INTERVALS = listOf(1 to "매일", 7 to "매주")

/** `primary:Backups/health` 처럼 보이는 폴더 위치를 사람이 읽기 좋게. */
internal fun autoBackupFolderLabel(folderUri: String): String =
    Uri.decode(folderUri).substringAfterLast("/tree/").substringBefore("/document/").ifBlank { folderUri }

internal fun formatBackupTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("M월 d일 HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(zone))

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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
            Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Hairline()
    }
}

/** 구역 제목. 위에 넉넉한 여백, 아래에 가는 선. 구역 안의 줄들은 선 없이 여백으로만 나눈다. */
@Composable
private fun SettingSectionTitle(title: String) {
    Column(modifier = Modifier.padding(top = 32.dp, bottom = 6.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Hairline(modifier = Modifier.padding(top = 10.dp))
    }
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 설명이 길어도 스위치에 붙지 않도록 오른쪽을 비워 둔다.
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
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
        Text(value, style = MaterialTheme.typography.titleLarge)
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
            .padding(vertical = 6.dp),
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
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

/** 러닝 목표를 한 번에 올리고 내리는 양(km 또는 mile). */
private const val WEEKLY_RUN_STEP = 5.0
