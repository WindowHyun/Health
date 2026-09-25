package com.windowhyun.health.ui.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.ui.components.ConfirmDialog
import com.windowhyun.health.ui.components.EmptyMessage
import kotlinx.coroutines.flow.collectLatest
import java.time.format.TextStyle
import java.util.Locale

/** 헬스 탭: 루틴 목록. 여기서 바로 운동을 시작할 수 있다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    viewModel: RoutineListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    val templateViewModel: RoutineTemplateViewModel = hiltViewModel()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.startedWorkoutId.collectLatest { onStartWorkout(it) }
    }

    LaunchedEffect(Unit) {
        templateViewModel.applied.collectLatest { result ->
            showTemplateSheet = false
            val message = if (result.createdRoutineNames.isEmpty()) {
                "루틴을 만들지 못했습니다. 종목이 먼저 있어야 합니다."
            } else {
                "'${result.template.title}' 추가됨 · ${result.createdRoutineNames.joinToString(", ")}" +
                    if (result.missingExerciseNames.isNotEmpty()) {
                        " (없는 종목 ${result.missingExerciseNames.size}개 제외)"
                    } else {
                        ""
                    }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("헬스") }) },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRoutine,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("루틴 만들기") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.activeWorkout != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("진행 중인 운동이 있습니다")
                            Button(onClick = { viewModel.startWorkout(null) }) { Text("이어하기") }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.startWorkout(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("루틴 없이 바로 시작", modifier = Modifier.padding(start = 8.dp))
                }
            }

            item {
                OutlinedButton(
                    onClick = { showTemplateSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Text("유명한 루틴 템플릿에서 추가", modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (!state.loading && state.routines.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.Filled.FitnessCenter,
                        title = "루틴이 없습니다",
                        description = "상체 / 하체 / 전신처럼 자주 하는 운동 묶음을 만들어 두면\n운동 시작이 한 번의 터치로 끝납니다.",
                    )
                }
            }

            items(state.routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    startEnabled = state.activeWorkout == null,
                    onStart = { viewModel.startWorkout(routine.id) },
                    onEdit = { onEditRoutine(routine.id) },
                    onDelete = { routineToDelete = routine },
                )
            }
        }
    }

    if (showTemplateSheet) {
        RoutineTemplateSheet(
            onPick = { template -> templateViewModel.applyTemplate(template) },
            onDismiss = { showTemplateSheet = false },
        )
    }

    routineToDelete?.let { routine ->
        ConfirmDialog(
            title = "루틴 삭제",
            message = "'${routine.name}' 루틴을 삭제할까요? 이미 저장된 운동 기록은 남습니다.",
            confirmLabel = "삭제",
            onConfirm = {
                viewModel.deleteRoutine(routine.id)
                routineToDelete = null
            },
            onDismiss = { routineToDelete = null },
        )
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "운동 ${routine.exerciseCount}개 · ${routine.totalSets}세트",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (routine.scheduledDays.isNotEmpty()) {
                        val days = routine.scheduledDays
                            .sortedBy { it.value }
                            .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.KOREAN) }
                        Text(
                            text = days,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "루틴 수정") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "루틴 삭제") }
            }

            if (routine.items.isNotEmpty()) {
                Text(
                    text = routine.items.joinToString(", ") { it.exercise.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = onStart,
                enabled = startEnabled && routine.items.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("운동 시작", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
