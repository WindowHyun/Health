package com.windowhyun.health.ui.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.domain.usecase.RoutineRecommender

/**
 * 맞춤 루틴 추천.
 *
 * 고를 때마다 바로 다시 계산해 아래에 미리 보여 준다. "추천 받기" 를 따로 누를
 * 필요가 없고, 결과를 보면서 부위를 바꿔 볼 수 있다. 계산은 기기 안에서 한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineRecommendationForm(
    availableExerciseNames: Set<String>,
    onApply: (plan: RoutineRecommender.Plan, daysPerWeek: Int) -> Unit,
    onBack: () -> Unit,
) {
    var focus by remember { mutableStateOf(emptySet<BodyPart>()) }
    var excluded by remember { mutableStateOf(emptySet<BodyPart>()) }
    var days by remember { mutableIntStateOf(3) }
    var level by remember { mutableStateOf(RoutineRecommender.Level.BEGINNER) }

    val plan = remember(focus, excluded, days, level, availableExerciseNames) {
        RoutineRecommender.recommend(
            RoutineRecommender.Preferences(
                focus = focus,
                excluded = excluded,
                daysPerWeek = days,
                level = level,
            ),
            availableExerciseNames,
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "템플릿 목록으로")
                }
                Text(
                    text = "맞춤 루틴 추천",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "원하는 부위와 빼고 싶은 부위를 고르면 운동 원칙에 맞춰 짜 드립니다. " +
                    "인터넷 없이 폰 안에서 계산합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ChipGroup(title = "집중하고 싶은 부위") {
                RoutineRecommender.selectableParts.forEach { part ->
                    FilterChip(
                        selected = part in focus,
                        onClick = {
                            // 한 부위가 "집중"과 "제외"에 동시에 있을 수는 없다.
                            focus = focus.toggle(part)
                            excluded = excluded - part
                        },
                        label = { Text(part.label) },
                    )
                }
            }
        }

        item {
            ChipGroup(title = "빼고 싶은 부위") {
                RoutineRecommender.selectableParts.forEach { part ->
                    FilterChip(
                        selected = part in excluded,
                        onClick = {
                            excluded = excluded.toggle(part)
                            focus = focus - part
                        },
                        label = { Text(part.label) },
                    )
                }
            }
        }

        item {
            ChipGroup(title = "주당 운동 횟수") {
                RoutineRecommender.dayRange.forEach { count ->
                    FilterChip(
                        selected = days == count,
                        onClick = { days = count },
                        label = { Text("주 ${count}회") },
                    )
                }
            }
        }

        item {
            ChipGroup(title = "운동 경력") {
                RoutineRecommender.Level.entries.forEach { option ->
                    FilterChip(
                        selected = level == option,
                        onClick = { level = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("추천 결과", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                plan.notes.forEach { note ->
                    Text(
                        text = "· $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(plan.days, key = { it.name }) { day -> PlanDayCard(day) }

        item {
            Button(
                onClick = { onApply(plan, days) },
                enabled = !plan.isEmpty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    if (plan.isEmpty) "만들 수 있는 루틴이 없습니다" else "이대로 루틴 ${plan.days.size}개 만들기",
                )
            }
            Text(
                text = "만든 뒤에는 보통 루틴처럼 종목과 세트를 고칠 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun PlanDayCard(day: RoutineRecommender.RecommendedDay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            day.exercises.forEach { exercise ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        // 집중 부위 종목은 색으로 구분한다.
                        color = if (exercise.isFocus) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (exercise.isFocus) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${exercise.bodyPart.label} · ${exercise.sets}세트",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
