package com.windowhyun.health.data.local

import androidx.room.TypeConverter
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory

/** enum <-> String 변환. 값 이름으로 저장해 순서가 바뀌어도 안전하다. */
class Converters {
    @TypeConverter
    fun bodyPartToString(value: BodyPart): String = value.name

    @TypeConverter
    fun stringToBodyPart(value: String): BodyPart =
        runCatching { BodyPart.valueOf(value) }.getOrDefault(BodyPart.FULL_BODY)

    @TypeConverter
    fun categoryToString(value: ExerciseCategory): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): ExerciseCategory =
        runCatching { ExerciseCategory.valueOf(value) }.getOrDefault(ExerciseCategory.OTHER)
}
