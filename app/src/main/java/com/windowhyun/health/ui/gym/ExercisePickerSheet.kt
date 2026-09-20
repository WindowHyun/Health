package com.windowhyun.health.ui.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.domain.model.Exercise

/**
 * 운동 종목 선택 시트. 부위 필터 + 검색 + 새 종목 추가를 한 화면에서 처리한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onCreate: (String, ExerciseCategory, BodyPart) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var bodyPartFilter by remember { mutableStateOf<BodyPart?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(ExerciseCategory.BARBELL) }
    var newBodyPart by remember { mutableStateOf(BodyPart.CHEST) }

    val filtered = exercises.filter { exercise ->
        (bodyPartFilter == null || exercise.bodyPart == bodyPartFilter) &&
            (query.isBlank() || exercise.name.contains(query.trim(), ignoreCase = true))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("운동 추가", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("운동 검색") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = bodyPartFilter == null,
                        onClick = { bodyPartFilter = null },
                        label = { Text("전체") },
                    )
                }
                items(BodyPart.entries) { part ->
                    FilterChip(
                        selected = bodyPartFilter == part,
                        onClick = { bodyPartFilter = if (bodyPartFilter == part) null else part },
                        label = { Text(part.label) },
                    )
                }
            }

            if (creating) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("새 운동 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("부위", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BodyPart.entries) { part ->
                            FilterChip(
                                selected = newBodyPart == part,
                                onClick = { newBodyPart = part },
                                label = { Text(part.label) },
                            )
                        }
                    }
                    Text("종류", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExerciseCategory.entries) { category ->
                            FilterChip(
                                selected = newCategory == category,
                                onClick = { newCategory = category },
                                label = { Text(category.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onCreate(newName, newCategory, newBodyPart)
                                newName = ""
                                creating = false
                            },
                            enabled = newName.isNotBlank(),
                        ) { Text("추가하고 넣기") }
                        TextButton(onClick = { creating = false }) { Text("취소") }
                    }
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("목록에 없는 운동 직접 추가", modifier = Modifier.padding(start = 4.dp))
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.id }) { exercise ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(exercise) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${exercise.bodyPart.label} · ${exercise.category.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "검색 결과가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
