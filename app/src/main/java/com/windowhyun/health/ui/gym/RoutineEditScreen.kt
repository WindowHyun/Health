package com.windowhyun.health.ui.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.windowhyun.health.domain.model.RoutineItem
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** 루틴 생성/수정 화면. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(
    onDone: () -> Unit,
    viewModel: RoutineEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "루틴 만들기" else "루틴 수정") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("저장") }
                },
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
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("루틴 이름 (예: 상체, 하체, 전신 A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("요일 지정 (선택)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "지정한 요일에는 홈 화면에 이 루틴이 바로 뜹니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(DayOfWeek.entries) { day ->
                            FilterChip(
                                selected = day in state.scheduledDays,
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.KOREAN)) },
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("운동 ${state.items.size}개", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showPicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("운동 추가", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            itemsIndexed(state.items, key = { index, item -> "${item.exercise.id}-$index" }) { index, item ->
                RoutineItemCard(
                    index = index,
                    item = item,
                    isFirst = index == 0,
                    isLast = index == state.items.lastIndex,
                    onMoveUp = { viewModel.moveItem(index, index - 1) },
                    onMoveDown = { viewModel.moveItem(index, index + 1) },
                    onRemove = { viewModel.removeItem(index) },
                    onSetsChange = { delta -> viewModel.changeSets(index, delta) },
                    onRestChange = { rest -> viewModel.changeRestSeconds(index, rest) },
                )
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        text = "운동을 추가하면 여기에 순서대로 표시됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = exercises,
            onPick = {
                viewModel.addExercise(it)
                showPicker = false
            },
            onCreate = { name, category, bodyPart ->
                viewModel.createAndAddExercise(name, category, bodyPart)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun RoutineItemCard(
    index: Int,
    item: RoutineItem,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (Int) -> Unit,
    onRestChange: (Int?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${item.exercise.bodyPart.label} · ${item.exercise.category.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "위로")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "아래로")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("기본 세트", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSetsChange(-1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "세트 줄이기")
                    }
                    Text(
                        text = "${item.defaultSets}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = { onSetsChange(1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "세트 늘리기")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("휴식시간", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = item.restSeconds?.let { "${it}초" } ?: "앱 기본값 사용",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onRestChange((item.restSeconds ?: 60) - 15) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "휴식 줄이기")
                    }
                    IconButton(onClick = { onRestChange((item.restSeconds ?: 60) + 15) }) {
                        Icon(Icons.Filled.Add, contentDescription = "휴식 늘리기")
                    }
                    if (item.restSeconds != null) {
                        androidx.compose.material3.TextButton(onClick = { onRestChange(null) }) {
                            Text("기본값")
                        }
                    }
                }
            }
        }
    }
}
