package com.windowhyun.health.data.seed

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.entity.ExerciseEntity

/**
 * 앱 최초 실행 시 넣어 두는 기본 운동 종목.
 * 사용자는 여기에 자유롭게 종목을 추가/수정할 수 있다.
 */
object DefaultExercises {

    private fun exercise(
        name: String,
        category: ExerciseCategory,
        bodyPart: BodyPart,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    ) = ExerciseEntity(
        name = name,
        category = category,
        bodyPart = bodyPart,
        isBuiltIn = true,
        trackingType = trackingType,
    )

    val all: List<ExerciseEntity> = listOf(
        // 가슴
        exercise("벤치프레스", ExerciseCategory.BARBELL, BodyPart.CHEST),
        exercise("인클라인 벤치프레스", ExerciseCategory.BARBELL, BodyPart.CHEST),
        exercise("덤벨 벤치프레스", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("덤벨 플라이", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("체스트 프레스 머신", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("케이블 크로스오버", ExerciseCategory.CABLE, BodyPart.CHEST),
        exercise("딥스", ExerciseCategory.BODYWEIGHT, BodyPart.CHEST, ExerciseTrackingType.REPS_ONLY),
        exercise("푸시업", ExerciseCategory.BODYWEIGHT, BodyPart.CHEST, ExerciseTrackingType.REPS_ONLY),
        // 등
        exercise("데드리프트", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("바벨로우", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("덤벨로우", ExerciseCategory.DUMBBELL, BodyPart.BACK),
        exercise("랫풀다운", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("시티드 케이블로우", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("풀업", ExerciseCategory.BODYWEIGHT, BodyPart.BACK, ExerciseTrackingType.REPS_ONLY),
        exercise("티바로우", ExerciseCategory.MACHINE, BodyPart.BACK),
        // 어깨
        exercise("오버헤드 프레스", ExerciseCategory.BARBELL, BodyPart.SHOULDER),
        exercise("덤벨 숄더프레스", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("사이드 레터럴 레이즈", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("벤트오버 레터럴 레이즈", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("페이스풀", ExerciseCategory.CABLE, BodyPart.SHOULDER),
        exercise("슈러그", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        // 팔
        exercise("바벨 컬", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("덤벨 컬", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("해머 컬", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("케이블 푸시다운", ExerciseCategory.CABLE, BodyPart.ARM),
        exercise("라잉 트라이셉스 익스텐션", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("클로즈그립 벤치프레스", ExerciseCategory.BARBELL, BodyPart.ARM),
        // 하체
        exercise("스쿼트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("프론트 스쿼트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("레그프레스", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("레그 익스텐션", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("레그 컬", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("루마니안 데드리프트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("런지", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("카프레이즈", ExerciseCategory.MACHINE, BodyPart.LEG),
        // 코어
        exercise("플랭크", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.TIME),
        exercise("크런치", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("행잉 레그레이즈", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("케이블 크런치", ExerciseCategory.CABLE, BodyPart.CORE),
        // 전신
        exercise("케틀벨 스윙", ExerciseCategory.KETTLEBELL, BodyPart.FULL_BODY),
        exercise("버피", ExerciseCategory.BODYWEIGHT, BodyPart.FULL_BODY, ExerciseTrackingType.REPS_ONLY),
    )
}
