package com.windowhyun.health.data.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.windowhyun.health.domain.cloud.CloudAccount
import com.windowhyun.health.domain.cloud.CloudException
import com.windowhyun.health.domain.cloud.CloudUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase 로그인(구글 계정).
 *
 * Firebase 설정(google-services.json)이 앱에 없으면 [isAvailable] 이 false 이고 Firebase 는 건드리지 않는다
 * (설정 없이 Firebase 를 부르면 앱이 죽는다). 그래서 설정 파일 없이도 앱은 그대로 빌드되고 동작한다.
 */
@Singleton
class FirebaseCloudAccount @Inject constructor(
    @ApplicationContext private val context: Context,
) : CloudAccount {

    override val isAvailable: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    private val _user = MutableStateFlow<CloudUser?>(null)
    override val user: StateFlow<CloudUser?> = _user.asStateFlow()

    init {
        if (isAvailable) {
            val auth = FirebaseAuth.getInstance()
            _user.value = auth.currentUser?.toCloudUser()
            auth.addAuthStateListener { _user.value = it.currentUser?.toCloudUser() }
        }
    }

    override suspend fun signIn(activityContext: Context): Boolean {
        if (!isAvailable) throw CloudException("Firebase 설정이 앱에 없어요. docs/CLOUD.md 를 보고 google-services.json 을 넣어 주세요.")
        val webClientId = webClientId()
            ?: throw CloudException("Firebase 설정에 웹 클라이언트 ID 가 없어요. 콘솔에서 구글 로그인을 켜고 google-services.json 을 다시 받아 주세요.")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            return false // 사용자가 계정 선택을 닫았다.
        } catch (e: NoCredentialException) {
            throw CloudException("이 기기에 로그인할 수 있는 구글 계정이 없어요. 설정에서 구글 계정을 추가해 주세요.", e)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "구글 로그인 실패", e)
            throw CloudException("구글 로그인에 실패했어요(${e.type}). ${e.message.orEmpty()}".trim(), e)
        }

        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw CloudException("지원하지 않는 로그인 방식이 돌아왔어요.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        try {
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase 로그인 실패", e)
            throw CloudException("Firebase 로그인에 실패했어요. ${e.message.orEmpty()}".trim(), e)
        }
        return true
    }

    override fun signOut() {
        if (isAvailable) FirebaseAuth.getInstance().signOut()
    }

    /** google-services 플러그인이 설정 파일에서 만들어 둔 웹 클라이언트 ID. 없으면 null. */
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    private fun FirebaseUser.toCloudUser() = CloudUser(uid = uid, email = email, displayName = displayName)

    private companion object {
        const val TAG = "FirebaseCloudAccount"
    }
}
