package com.windowhyun.health.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 미리 찍어 둔 러닝 코스. 지점들은 "위도,경도;위도,경도" 한 줄로 저장한다(코스는 수십~수백 점이라 별도 표가 필요 없다).
 */
@Entity(tableName = "course")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val points: String,
)
