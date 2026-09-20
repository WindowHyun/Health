package com.windowhyun.health.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory

/**
 * 운동 종목 사전. 기본 제공 종목 + 사용자가 추가한 종목이 함께 들어간다.
 */
@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    val bodyPart: BodyPart,
    /** 기본 제공 종목 여부. false 면 사용자 생성. */
    @ColumnInfo(defaultValue = "0")
    val isBuiltIn: Boolean = false,
    /** 이 종목의 기본 휴식시간(초). null 이면 앱 설정값을 사용한다. */
    val defaultRestSeconds: Int? = null,
)
