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
