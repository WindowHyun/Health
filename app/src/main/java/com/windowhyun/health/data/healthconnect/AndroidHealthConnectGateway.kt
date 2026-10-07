package com.windowhyun.health.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.windowhyun.health.domain.model.HealthConnectAvailability
import com.windowhyun.health.domain.model.HealthConnectGateway
import com.windowhyun.health.domain.model.HealthPermissionState
import com.windowhyun.health.domain.model.HealthSession
import com.windowhyun.health.domain.model.HealthSessionKind
import com.windowhyun.health.domain.model.HeartRateSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** 앱이 Health Connect 에 요청하는 권한. 허용 화면(설정)과 확인하는 곳(게이트웨이)이 같은 목록을 쓴다. */
object HealthConnectPermissions {
    private val write = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    )
    val readWeight: String = HealthPermission.getReadPermission(WeightRecord::class)
    val readHeartRate: String = HealthPermission.getReadPermission(HeartRateRecord::class)

    /** 내보내기에 필요한 것(전부). */
    val exportPermissions: Set<String> = write

    /** 체중 가져오기에 필요한 것. */
    val weightPermissions: Set<String> = setOf(readWeight)

    /** 모두. 처음 허용 화면에서 한꺼번에 묻는다(심박은 러닝 상세에 보여 주는 데 쓴다). */
    val all: Set<String> = write + readWeight + readHeartRate

    fun stateFrom(granted: Set<String>) = HealthPermissionState(
        canWrite = granted.containsAll(write),
        canReadWeight = readWeight in granted,
        canReadHeartRate = readHeartRate in granted,
    )
}

/**
 * Health Connect SDK 로 읽고 쓴다.
 *
 * 한 운동을 여러 기록으로 나눠 쓴다(운동 세션 + 거리 + 걸음 + 칼로리). 이름표는 세션 이름표에
 * 종류를 붙여 만들어서, 지울 때도 같은 규칙으로 찾는다.
 */
@Singleton
class AndroidHealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectGateway {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_UPDATE
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    override suspend fun permissionState(): HealthPermissionState =
        HealthConnectPermissions.stateFrom(client.permissionController.getGrantedPermissions())

    override suspend fun upsert(sessions: List<HealthSession>) {
        if (sessions.isEmpty()) return
        val version = System.currentTimeMillis()
        client.insertRecords(sessions.flatMap { it.toRecords(version) })
    }

    override suspend fun delete(clientIds: List<String>) {
        if (clientIds.isEmpty()) return
        val all = RECORD_KINDS.mapValues { (_, suffix) -> clientIds.map { it + suffix } }
        for ((type, ids) in all) {
            client.deleteRecords(type, recordIdsList = emptyList(), clientRecordIdsList = ids)
        }
    }

    override suspend fun latestWeightKg(withinDays: Int): Double? {
        val now = Instant.now()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(withinDays.toLong(), ChronoUnit.DAYS), now),
                ascendingOrder = false,
                pageSize = 1,
            ),
        )
        return response.records.firstOrNull()?.weight?.inKilograms
    }

    override suspend fun heartRate(startMillis: Long, endMillis: Long): HeartRateSummary? {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf<AggregateMetric<Long>>(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX),
                timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis)),
            ),
        )
        val average = result[HeartRateRecord.BPM_AVG] ?: return null
        val max = result[HeartRateRecord.BPM_MAX] ?: average
        return HeartRateSummary(averageBpm = average.toInt(), maxBpm = max.toInt())
    }

    private fun HealthSession.toRecords(version: Long): List<Record> {
        val start = Instant.ofEpochMilli(startMillis)
        val end = Instant.ofEpochMilli(endMillis)
        val startOffset = offsetAt(start)
        val endOffset = offsetAt(end)
        // 폰으로 기록한 러닝은 "직접 기록", 헬스는 사용자가 입력한 값이라 "수동 입력"으로 알린다.
        fun metadata(suffix: String) = when (kind) {
            HealthSessionKind.RUN -> Metadata(
                clientRecordId = clientId + suffix,
                clientRecordVersion = version,
                device = Device(type = Device.TYPE_PHONE),
                recordingMethod = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED,
            )
            HealthSessionKind.STRENGTH -> Metadata(
                clientRecordId = clientId + suffix,
                clientRecordVersion = version,
                recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY,
            )
        }

        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = startOffset,
                endTime = end,
                endZoneOffset = endOffset,
                exerciseType = when (kind) {
                    HealthSessionKind.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                    HealthSessionKind.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                },
                title = title,
                metadata = metadata(SUFFIX_SESSION),
            ),
        )
        if (distanceMeters > 0) {
            records += DistanceRecord(
                startTime = start, startZoneOffset = startOffset, endTime = end, endZoneOffset = endOffset,
                distance = Length.meters(distanceMeters),
                metadata = metadata(SUFFIX_DISTANCE),
            )
        }
        if (steps > 0) {
            records += StepsRecord(
                startTime = start, startZoneOffset = startOffset, endTime = end, endZoneOffset = endOffset,
                count = steps.toLong(),
                metadata = metadata(SUFFIX_STEPS),
            )
        }
        if (calories > 0) {
            records += TotalCaloriesBurnedRecord(
                startTime = start, startZoneOffset = startOffset, endTime = end, endZoneOffset = endOffset,
                energy = Energy.kilocalories(calories.toDouble()),
                metadata = metadata(SUFFIX_CALORIES),
            )
        }
        return records
    }

    private fun offsetAt(instant: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(instant)

    private companion object {
        const val SUFFIX_SESSION = ""
        const val SUFFIX_DISTANCE = "-distance"
        const val SUFFIX_STEPS = "-steps"
        const val SUFFIX_CALORIES = "-calories"

        /** 지울 때 종류별로 찾는 이름표 꼬리. */
        val RECORD_KINDS: Map<KClass<out Record>, String> = mapOf(
            ExerciseSessionRecord::class to SUFFIX_SESSION,
            DistanceRecord::class to SUFFIX_DISTANCE,
            StepsRecord::class to SUFFIX_STEPS,
            TotalCaloriesBurnedRecord::class to SUFFIX_CALORIES,
        )
    }
}
