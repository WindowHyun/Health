package com.windowhyun.health.ui.running

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.util.Counters

/**
 * 지도 배경이 비어 있을 때 그 이유를 알려 주기 위한 상태.
 *
 * 타일이 안 보이는 원인은 대부분 네트워크이지만, 타일 서버 오류일 수도 있다.
 * 빈 회색 화면만 보여 주면 사용자는 앱이 고장난 줄 알게 되므로
 * 짧게 기다렸다가 상황을 한 줄로 알려 준다.
 */
@Composable
fun rememberMapTileStatus(hasRoute: Boolean): State<String?> {
    val context = LocalContext.current
    val message = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasRoute) {
        message.value = null
        if (!hasRoute) return@LaunchedEffect

        val errorsBefore = Counters.tileDownloadErrors
        // 타일을 받아 볼 시간을 준다. 이 시간 안에 안 되면 안내를 띄운다.
        delay(CHECK_DELAY_MILLIS)

        message.value = when {
            !context.isOnline() ->
                "오프라인이라 지도 배경을 받지 못했습니다. 경로만 표시합니다."

            Counters.tileDownloadErrors > errorsBefore ->
                "지도 서버에서 배경을 받지 못했습니다(오류 " +
                    "${Counters.tileDownloadErrors - errorsBefore}건). 경로만 표시합니다."

            else -> null
        }
    }

    return message
}

private fun Context.isOnline(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private const val CHECK_DELAY_MILLIS = 4_000L
