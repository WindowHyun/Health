package com.windowhyun.health.data.cloud

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.windowhyun.health.domain.cloud.CloudBackupInfo
import com.windowhyun.health.domain.cloud.CloudBackupStore
import com.windowhyun.health.domain.cloud.CloudException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore 에 백업을 둔다. 구조(사용자마다 자기 문서만):
 *
 * ```
 * users/{uid}/backups/{id}               백업 정보(날짜 · 개수 · 크기 · 해시 · 조각 수)
 * users/{uid}/backups/{id}/chunks/{0000} 압축한 백업의 조각(Blob)
 * ```
 *
 * 읽기·쓰기 권한은 보안 규칙(firebase/firestore.rules)이 "로그인한 본인만" 으로 막는다.
 * Cloud Storage 대신 Firestore 만 쓰는 이유는, Storage 는 신규 프로젝트에서 결제 계정(Blaze)이 필요해서다.
 */
@Singleton
class FirestoreCloudBackupStore @Inject constructor() : CloudBackupStore {

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().also { firestore ->
            // 큰 조각을 기기에 한 번 더 저장해 두지 않도록 메모리 캐시만 쓴다. 이미 쓰고 있으면 설정을 바꿀 수 없어 그대로 둔다.
            runCatching {
                firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }

    private fun backups(uid: String) = db.collection("users").document(uid).collection("backups")

    override suspend fun put(uid: String, info: CloudBackupInfo, chunks: List<ByteArray>): Unit = network("올리는 중") {
        val doc = backups(uid).document(info.id)
        chunks.forEachIndexed { index, bytes ->
            doc.collection("chunks").document(chunkName(index))
                .set(mapOf("index" to index, "data" to Blob.fromBytes(bytes))).await()
        }
        // 조각을 다 쓴 뒤에 정보를 쓴다. 정보가 보이는 백업은 조각이 모두 있다.
        doc.set(CloudBackupFields.toMap(info)).await()
    }

    override suspend fun list(uid: String): List<CloudBackupInfo> = network("목록을 가져오는 중") {
        backups(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(LIST_LIMIT)
            .get(Source.SERVER).await().documents.mapNotNull { it.toInfo() }
    }

    override suspend fun read(uid: String, info: CloudBackupInfo): List<ByteArray> = network("내려받는 중") {
        val docs = backups(uid).document(info.id).collection("chunks").orderBy("index").get(Source.SERVER).await().documents
        if (docs.size != info.chunkCount) {
            throw CloudException("백업의 조각이 모자라요(${docs.size}/${info.chunkCount}). 다른 백업을 선택해 주세요.")
        }
        docs.map { it.getBlob("data")?.toBytes() ?: throw CloudException("백업의 조각을 읽지 못했어요.") }
    }

    override suspend fun delete(uid: String, id: String) = network("지우는 중") {
        val doc = backups(uid).document(id)
        // 정보를 먼저 지워 목록에서 사라지게 한 뒤 조각을 지운다(중간에 끊겨도 반쪽 백업이 목록에 남지 않는다).
        doc.delete().await()
        doc.collection("chunks").get(Source.SERVER).await().documents.forEach { it.reference.delete().await() }
    }

    private fun DocumentSnapshot.toInfo(): CloudBackupInfo? = CloudBackupFields.fromMap(id, data.orEmpty())

    /** 네트워크가 없으면 Firestore 는 응답을 오래 기다린다. 한도를 두고, 시간이 넘으면 이유를 알려 준다. */
    private suspend fun <T> network(what: String, block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MILLIS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw CloudException("클라우드와 연결되지 않아요($what). 네트워크를 확인해 주세요.", e)
    }

    private fun chunkName(index: Int) = index.toString().padStart(4, '0')

    private companion object {
        const val LIST_LIMIT = 20L
        const val TIMEOUT_MILLIS = 120_000L
    }
}

/** 백업 정보 ↔ Firestore 문서 필드. Firebase 에 의존하지 않아 단위 테스트로 확인한다. */
internal object CloudBackupFields {
    fun toMap(info: CloudBackupInfo): Map<String, Any> = mapOf(
        "createdAt" to info.createdAt,
        "appVersion" to info.appVersion,
        "workoutCount" to info.workoutCount,
        "setCount" to info.setCount,
        "runCount" to info.runCount,
        "routineCount" to info.routineCount,
        "customExerciseCount" to info.customExerciseCount,
        "userTemplateCount" to info.userTemplateCount,
        "sizeBytes" to info.sizeBytes,
        "chunkCount" to info.chunkCount,
        "sha256" to info.sha256,
    )

    /** 필수 필드가 없거나 모양이 틀린 문서는 백업으로 보지 않는다(null). */
    fun fromMap(id: String, data: Map<String, Any?>): CloudBackupInfo? {
        fun long(key: String) = (data[key] as? Number)?.toLong()
        fun int(key: String) = (data[key] as? Number)?.toInt()
        return CloudBackupInfo(
            id = id,
            createdAt = long("createdAt") ?: return null,
            appVersion = data["appVersion"] as? String ?: "",
            workoutCount = int("workoutCount") ?: 0,
            setCount = int("setCount") ?: 0,
            runCount = int("runCount") ?: 0,
            routineCount = int("routineCount") ?: 0,
            customExerciseCount = int("customExerciseCount") ?: 0,
            userTemplateCount = int("userTemplateCount") ?: 0,
            sizeBytes = long("sizeBytes") ?: return null,
            chunkCount = int("chunkCount") ?: return null,
            sha256 = data["sha256"] as? String ?: return null,
        )
    }
}
