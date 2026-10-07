package com.windowhyun.health.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.ceil

/**
 * 폰 앱과 시계 앱이 주고받는 것.
 *
 * - **상태**(폰 → 시계)는 Wearable Data Layer 의 데이터 항목으로 보낸다. 시계가 늦게 켜져도 마지막 상태를 읽을 수 있다.
 * - **명령**(시계 → 폰)은 메시지로 보낸다. 경로가 곧 명령이고 내용은 비어 있다.
 *
 * 시간은 "이 순간 몇 초" 대신 **절대 시각**(epoch ms)과 보낸 시각을 같이 보내서, 전달이 늦어져도 시계가 스스로 맞춰 센다.
 */
object WearProtocol {
    const val PATH_RUN_STATE = "/health/run_state"
    const val PATH_REST_STATE = "/health/rest_state"
    const val COMMAND_PREFIX = "/health/cmd/"

    /** 이 시간 넘게 새 상태가 안 오면 폰과 끊겼을 수 있다고 알린다. 폰은 진행 중에 이보다 자주 보낸다. */
    const val STALE_MILLIS = 60_000L

    /** 진행 중에 상태가 같아도 이 간격으로는 다시 보낸다(끊김을 알아챌 수 있게). */
    const val HEARTBEAT_MILLIS = 15_000L
}

@Serializable
enum class WatchRunStatus { IDLE, TRACKING, PAUSED, FINISHED }

/** 러닝 상태. 시계 화면이 필요한 것만 담는다. */
@Serializable
data class RunSnapshot(
    val status: WatchRunStatus = WatchRunStatus.IDLE,
    val distanceMeters: Double = 0.0,
    /** [sentAtMillis] 시점의 경과 시간(초). */
    val elapsedSeconds: Long = 0,
    val currentPaceSecPerKm: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    /** 멈춰서 자동으로 일시정지된 상태. */
    val autoPaused: Boolean = false,
    val signalLost: Boolean = false,
    /** 사용자가 마일 단위를 쓰는지(거리와 페이스를 그 단위로 보여 준다). */
    val useMiles: Boolean = false,
    /** 목표 달성률 0..1. 목표가 없으면 null. */
    val goalProgress: Float? = null,
    val sentAtMillis: Long = 0,
) {
    val isActive: Boolean get() = status == WatchRunStatus.TRACKING || status == WatchRunStatus.PAUSED

    /**
     * [nowMillis] 시점의 경과 시간. 기록 중일 때만 보낸 뒤 흐른 시간을 더한다
     * (일시정지 중에는 폰도 시간을 멈춘다). 시계가 폰보다 앞서 있어도 줄어들지는 않는다.
     */
    fun elapsedAt(nowMillis: Long): Long {
        if (status != WatchRunStatus.TRACKING) return elapsedSeconds
        return elapsedSeconds + ((nowMillis - sentAtMillis).coerceAtLeast(0) / 1_000)
    }

    /** 진행 중인데 한동안 새 상태가 없으면 폰과 끊겼을 수 있다. */
    fun isStale(nowMillis: Long): Boolean =
        isActive && nowMillis - sentAtMillis > WearProtocol.STALE_MILLIS
}

/** 세션의 휴식 타이머. 끝났거나 없으면 [active] 가 false. */
@Serializable
data class RestSnapshot(
    val active: Boolean = false,
    val totalSeconds: Int = 0,
    /** 끝나는 절대 시각(epoch ms). 일시정지 중에는 의미가 없다. */
    val endsAtMillis: Long = 0,
    val paused: Boolean = false,
    /** 일시정지 중 남은 시간(ms). */
    val pausedRemainingMillis: Long = 0,
    val sentAtMillis: Long = 0,
) {
    fun remainingMillis(nowMillis: Long): Long = when {
        !active -> 0
        paused -> pausedRemainingMillis.coerceAtLeast(0)
        else -> (endsAtMillis - nowMillis).coerceAtLeast(0)
    }

    /** 화면에 적는 남은 초. 0.3 초가 남았으면 1 초로 올려 적는다(0 은 정말 끝났을 때만). */
    fun remainingSeconds(nowMillis: Long): Int = ceil(remainingMillis(nowMillis) / 1_000.0).toInt()

    fun isFinished(nowMillis: Long): Boolean = active && !paused && nowMillis >= endsAtMillis

    /** 0..1. 1 이 가득 찬 상태에서 줄어든다. */
    fun fraction(nowMillis: Long): Float {
        if (!active || totalSeconds <= 0) return 0f
        return (remainingMillis(nowMillis) / (totalSeconds * 1_000f)).coerceIn(0f, 1f)
    }

    companion object {
        val None = RestSnapshot()
    }
}

/** 시계가 폰에 보내는 명령. */
@Serializable
enum class WatchCommand(val key: String) {
    @SerialName("run_pause") RUN_PAUSE("run_pause"),
    @SerialName("run_resume") RUN_RESUME("run_resume"),
    @SerialName("run_stop") RUN_STOP("run_stop"),
    @SerialName("rest_skip") REST_SKIP("rest_skip"),
    @SerialName("rest_add") REST_ADD("rest_add"),
    @SerialName("rest_sub") REST_SUB("rest_sub"),
    @SerialName("rest_toggle_pause") REST_TOGGLE_PAUSE("rest_toggle_pause"),
    ;

    val path: String get() = WearProtocol.COMMAND_PREFIX + key

    companion object {
        /** 메시지 경로로 명령을 찾는다. 모르는 경로(다른 버전이 보낸 것 등)는 null. */
        fun fromPath(path: String): WatchCommand? =
            entries.firstOrNull { it.path == path }
    }
}

/** 상태를 바이트로 바꾸고 되돌린다. 읽을 수 없는 내용은 앱이 죽지 않게 null 로 돌려준다. */
object WearCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: RunSnapshot): ByteArray = json.encodeToString(RunSnapshot.serializer(), snapshot).toByteArray()

    fun encode(snapshot: RestSnapshot): ByteArray = json.encodeToString(RestSnapshot.serializer(), snapshot).toByteArray()

    fun decodeRun(bytes: ByteArray?): RunSnapshot? = decode(bytes) { json.decodeFromString(RunSnapshot.serializer(), it) }

    fun decodeRest(bytes: ByteArray?): RestSnapshot? = decode(bytes) { json.decodeFromString(RestSnapshot.serializer(), it) }

    private inline fun <T> decode(bytes: ByteArray?, parse: (String) -> T): T? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            parse(bytes.decodeToString())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
