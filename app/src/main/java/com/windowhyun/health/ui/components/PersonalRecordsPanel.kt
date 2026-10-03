package com.windowhyun.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.core.model.PersonalRecord
import com.windowhyun.health.core.model.PersonalRecordType
import com.windowhyun.health.core.model.WeightUnit
import com.windowhyun.health.core.util.formatPersonalRecordValue
import com.windowhyun.health.core.util.formatWeight

/**
 * 새 개인 기록 묶음. 기록마다 라임 상자를 따로 두면 많을 때 화면이 라임으로 가득 차서,
 * 라임 면 하나에 줄로 나열한다. 운동 결과와 운동 기록 상세가 함께 쓴다.
 */
@Composable
fun PersonalRecordsPanel(
    records: List<PersonalRecord>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val onAccent = MaterialTheme.healthColors.onAccent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.healthColors.accent)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        records.forEachIndexed { index, record ->
            if (index > 0) HorizontalDivider(color = onAccent.copy(alpha = 0.18f))
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.exerciseName, style = MaterialTheme.typography.titleSmall, color = onAccent)
                    Text(
                        text = record.type.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = onAccent.copy(alpha = 0.75f),
                    )
                    record.previousValue?.let { previous ->
                        Text(
                            text = "이전 " + formatPersonalRecordValue(record.type, previous, weightUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = onAccent.copy(alpha = 0.75f),
                        )
                    }
                    if (record.type == PersonalRecordType.MAX_ESTIMATED_ONE_RM &&
                        record.weightKg != null && record.reps != null
                    ) {
                        Text(
                            text = "${formatWeight(record.weightKg, weightUnit)} × ${record.reps} 기준 (Epley)",
                            style = MaterialTheme.typography.bodySmall,
                            color = onAccent.copy(alpha = 0.75f),
                        )
                    }
                }
                Text(
                    text = formatPersonalRecordValue(record.type, record.value, weightUnit),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = onAccent,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
