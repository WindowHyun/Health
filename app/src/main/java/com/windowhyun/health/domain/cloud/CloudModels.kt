package com.windowhyun.health.domain.cloud

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** 로그인한 사용자. 이메일과 이름은 화면에 "누구로 로그인했는지" 보여 주는 데만 쓴다. */
data class CloudUser(val uid: String, val email: String?, val displayName: String?)

/**
 * 클라우드에 올려 둔 백업 한 건의 정보. 목록에서 "언제, 무엇이 들어 있는 백업인지" 보여 주고,
 * 내려받은 파일이 온전한지(조각 수 · 해시) 확인하는 데 쓴다.
 */
data class CloudBackupInfo(
    val id: String,
    val createdAt: Long,
    val appVersion: String,
    val workoutCount: Int,
    val setCount: Int,
    val runCount: Int,
    val routineCount: Int,
    val customExerciseCount: Int,
    val userTemplateCount: Int,
    /** 압축한 뒤의 크기(바이트). */
    val sizeBytes: Long,
    val chunkCount: Int,
    /** 압축한 파일 전체의 SHA-256(16진수). 내려받은 뒤 같은지 확인한다. */
    val sha256: String,
)

/** 구글 계정으로 로그인. Firebase 설정이 앱에 없으면 [isAvailable] 이 false 다. */
interface CloudAccount {
    /** Firebase 설정(google-services.json)이 앱에 들어 있어 로그인을 쓸 수 있는가. */
    val isAvailable: Boolean

    /** 지금 로그인한 사용자. 로그아웃 상태면 null. */
    val user: StateFlow<CloudUser?>

    /**
     * 구글 계정을 골라 로그인한다. 화면을 띄워야 해서 Activity 컨텍스트가 필요하다.
     * 사용자가 계정 선택을 취소하면 false, 로그인하면 true. 실패하면 [CloudException].
     */
    suspend fun signIn(activityContext: Context): Boolean

    fun signOut()
}

/** 클라우드에 백업 조각을 두고 읽는 곳. 구현은 Firestore 이고, 테스트는 메모리로 대신한다. */
interface CloudBackupStore {
    /** 조각을 모두 쓴 **뒤에** 정보를 쓴다. 정보가 있는 백업은 언제나 조각이 다 있다. */
    suspend fun put(uid: String, info: CloudBackupInfo, chunks: List<ByteArray>)

    /** 최근 것부터. */
    suspend fun list(uid: String): List<CloudBackupInfo>

    /** 조각을 순서대로. 하나라도 없으면 [CloudException]. */
    suspend fun read(uid: String, info: CloudBackupInfo): List<ByteArray>

    suspend fun delete(uid: String, id: String)
}

/** 사용자에게 그대로 보여 줘도 되는 메시지를 가진 오류. */
class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause)
