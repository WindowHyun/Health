package com.windowhyun.health.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val PREFS = "permission_prompts"
private const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

/**
 * 알림 권한을 물어야 하는가. Android 13 부터 알림은 권한이 필요하고, 한 번 물은 뒤에는 다시 묻지 않는다.
 * (거절한 사람에게 운동을 시작할 때마다 묻는 것은 방해라서다.)
 */
internal fun shouldAskNotificationPermission(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted && !alreadyAsked

/**
 * 휴식 타이머 알림을 위해 알림 권한을 한 번만 묻는다.
 *
 * 휴식 타이머는 화면을 끄고 쉬는 동안 알려 주는 것이 목적인데, 권한이 없으면 알림이 뜨지 않는다.
 * 권한 요청이 러닝 시작 화면에만 있어서 러닝을 안 하는 사람은 한 번도 받을 기회가 없었다.
 * 거절해도 운동 기록과 진동에는 영향이 없다.
 */
@Composable
fun AskNotificationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (shouldAskNotificationPermission(Build.VERSION.SDK_INT, granted, prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false))) {
            // 먼저 적어 둔다. 창이 뜬 채 화면이 돌아가 다시 그려져도 두 번 묻지 않는다.
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
