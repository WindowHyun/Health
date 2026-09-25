package com.windowhyun.health.core.model

/** 운동 부위. DB 에는 name 문자열로 저장된다. */
enum class BodyPart(val label: String) {
    CHEST("가슴"),
    BACK("등"),
    SHOULDER("어깨"),
    ARM("팔"),
    LEG("하체"),
    CORE("코어"),
    FULL_BODY("전신"),
    CARDIO("유산소"),
}

/** 운동 종류(사용 기구). */
enum class ExerciseCategory(val label: String) {
    BARBELL("바벨"),
    DUMBBELL("덤벨"),
    MACHINE("머신"),
    CABLE("케이블"),
    BODYWEIGHT("맨몸"),
    KETTLEBELL("케틀벨"),
    CARDIO("유산소"),
    OTHER("기타"),
}

/** 중량 단위. 저장은 항상 kg, 표시할 때만 변환한다. */
enum class WeightUnit(val label: String, val perKg: Double) {
    KG("kg", 1.0),
    LB("lb", 2.2046226218),
    ;

    fun fromKg(kg: Double): Double = kg * perKg

    fun toKg(value: Double): Double = value / perKg
}

/** 거리 단위. 저장은 항상 meter. */
enum class DistanceUnit(val label: String, val perMeter: Double) {
    KM("km", 0.001),
    MILE("mile", 0.000621371192),
    ;

    fun fromMeters(meters: Double): Double = meters * perMeter

    fun toMeters(value: Double): Double = value / perMeter
}

/**
 * 세트 종류.
 *
 * 워밍업은 기록에는 남기지만 볼륨·반복·PR 집계에서 뺀다.
 * 섞어서 세면 총 볼륨과 개인 기록이 실제보다 부풀려진다.
 */
enum class SetType(val label: String, val shortLabel: String) {
    WARMUP("워밍업", "W"),
    NORMAL("본세트", ""),
    DROP("드롭세트", "D"),
    FAILURE("실패세트", "F"),
    ;

    /** 집계에 넣는 세트인가. */
    val countsTowardVolume: Boolean get() = this != WARMUP

    /** 화면에서 눌렀을 때 넘어갈 다음 종류. */
    fun next(): SetType = entries[(ordinal + 1) % entries.size]
}

/**
 * 운동을 무엇으로 기록하는지.
 *
 * 플랭크처럼 시간으로 재는 운동을 중량x횟수로만 기록하면 아예 남길 수가 없다.
 */
enum class ExerciseTrackingType(val label: String) {
    /** 중량 x 횟수. 대부분의 웨이트. */
    WEIGHT_REPS("중량 × 횟수"),

    /** 횟수만. 푸시업·풀업 같은 맨몸운동. */
    REPS_ONLY("횟수"),

    /** 시간. 플랭크·행잉 등. */
    TIME("시간"),
    ;

    val usesWeight: Boolean get() = this == WEIGHT_REPS
    val usesReps: Boolean get() = this == WEIGHT_REPS || this == REPS_ONLY
    val usesDuration: Boolean get() = this == TIME
}
