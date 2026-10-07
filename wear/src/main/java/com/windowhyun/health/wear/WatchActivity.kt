package com.windowhyun.health.wear

import android.app.Application
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
            )
        }
    }
}
