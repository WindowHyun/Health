package com.windowhyun.health.wear

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 시계 앱의 하나뿐인 화면. */
class WatchActivity : ComponentActivity() {

    private val viewModel: WatchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WatchViewModel(application, PhoneDataSource(applicationContext)) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !hasAskedForNotifications()
        ) {
            // 워치페이스 칩(알림)을 띄우려면 필요하다. 거절해도 앱은 그대로 쓸 수 있어서 결과는 따로 받지 않는다.
            // 한 번만 묻는다. 거절한 사람에게 앱을 열 때마다 다시 묻지 않는다.
            markAskedForNotifications()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        // 화면이 안 보이는 동안에는 시간을 새로 세지 않는다(손목 배터리). 그때의 휴식 끝 진동은 알람이 맡는다.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.setScreenVisible(true)
                    Lifecycle.Event.ON_STOP -> viewModel.setScreenVisible(false)
                    else -> Unit
                }
            },
        )

        // 휴식 타이머가 돌 때는 화면이 꺼지지 않게 한다(남은 시간을 보려고 손목을 다시 드는 일을 줄인다).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.restVisible }
                    .distinctUntilChanged()
                    .collect { resting ->
                        if (resting) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
            }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            WatchApp(
                state = state,
                onCommand = viewModel::send,
                onRequestStop = viewModel::requestStop,
                onConfirmStop = viewModel::confirmStop,
                onCancelStop = viewModel::cancelStop,
                onCompleteSet = viewModel::completeSet,
                onStartRun = viewModel::startRun,
            )
        }
    }

    private fun hasAskedForNotifications(): Boolean =
        runCatching { getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ASKED_NOTIFICATIONS, false) }
            .getOrDefault(false)

    private fun markAskedForNotifications() {
        runCatching { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply() }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
        const val PREFS = "watch"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
