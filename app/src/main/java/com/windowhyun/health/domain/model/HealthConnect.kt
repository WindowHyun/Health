package com.windowhyun.health.domain.model

/** 이 기기에서 Health Connect 를 쓸 수 있는가. */
enum class HealthConnectAvailability {
    AVAILABLE,

    /** 앱이 없거나 오래돼서 설치/업데이트가 필요하다(안드로이드 13 이하). */
    NEEDS_UPDATE,

    /** 이 기기는 지원하지 않는다. */
    UNAVAILABLE,
}

enum class HealthSessionKind { RUN, STRENGTH }

/**
 * Health Connect 로 내보낼 운동 한 번. 안드로이드 타입 없이 다뤄서 규칙을 JVM 테스트로 확인한다.
 *
 * [clientId] 는 같은 기록을 다시 보내도 중복되지 않게 하는 이름표다(기록마다 하나로 고정).
 * [fingerprint] 는 내용이 바뀌었는지 알아보는 값이다. 바뀐 기록만 다시 보낸다.
 */
data class HealthSession(
    val clientId: String,
    val kind: HealthSessionKind,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double = 0.0,
    val calories: Int = 0,
    val steps: Int = 0,
    val fingerprint: String,
)

/** 러닝 시간대의 심박. */
data class HeartRateSummary(
    val averageBpm: Int,
    val maxBpm: Int,
)

/** 사용자가 허용해 준 권한. */
data class HealthPermissionState(
    /** 운동 · 거리 · 걸음 · 칼로리 쓰기를 모두 허용했다. 하나라도 빠지면 내보낼 수 없다. */
    val canWrite: Boolean = false,
    val canReadWeight: Boolean = false,
    val canReadHeartRate: Boolean = false,
)

/** Health Connect 와 이야기하는 곳. Android SDK 에 기대는 부분을 가둬 테스트에서는 가짜로 바꾼다. */
interface HealthConnectGateway {
    fun availability(): HealthConnectAvailability

    suspend fun permissionState(): HealthPermissionState

    /** 같은 [HealthSession.clientId] 가 이미 있으면 덮어쓴다. */
    suspend fun upsert(sessions: List<HealthSession>)

    /** [clientIds] 로 보냈던 기록을 지운다. 없는 것은 무시한다. */
    suspend fun delete(clientIds: List<String>)

    /** 최근 [withinDays] 일 안에 기록된 가장 최근 체중(kg). 없으면 null. */
    suspend fun latestWeightKg(withinDays: Int): Double?

    /** [startMillis, endMillis] 의 평균 · 최대 심박. 기록이 없으면 null. */
    suspend fun heartRate(startMillis: Long, endMillis: Long): HeartRateSummary?
}

/** 어떤 기록을 이미 보냈는지 적어 두는 장부. 지운 기록을 Health Connect 에서도 지우는 데 쓴다. */
interface HealthConnectLedger {
    /** clientId -> 보낼 때의 fingerprint. */
    suspend fun read(): Map<String, String>

    suspend fun write(entries: Map<String, String>)
}

/** Health Connect 를 쓸 수 없는 기기에서의 게이트웨이. 아무것도 하지 않는다. */
object UnavailableHealthConnect : HealthConnectGateway {
    override fun availability() = HealthConnectAvailability.UNAVAILABLE
    override suspend fun permissionState() = HealthPermissionState()
    override suspend fun upsert(sessions: List<HealthSession>) = Unit
    override suspend fun delete(clientIds: List<String>) = Unit
    override suspend fun latestWeightKg(withinDays: Int): Double? = null
    override suspend fun heartRate(startMillis: Long, endMillis: Long): HeartRateSummary? = null
}
