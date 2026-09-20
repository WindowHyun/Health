package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 헬스 운동 1회 기록(세션).
 *
 * 운동을 시작할 때 endTime = null 상태로 먼저 만들어 두기 때문에
 * 앱이 종료되어도 진행 중이던 운동을 이어서 할 수 있다.
 *
 * @param date epochDay. 캘린더 조회를 위해 별도 컬럼으로 둔다.
 * @param durationSeconds 실제 운동시간(초). 진행 중이면 0.
 */
@Entity(
    tableName = "workout",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId"), Index("date"), Index("endTime")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long?,
    /** 루틴이 지워져도 기록에 이름은 남도록 스냅샷으로 저장한다. */
    val routineName: String?,
    val date: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val memo: String? = null,
)
