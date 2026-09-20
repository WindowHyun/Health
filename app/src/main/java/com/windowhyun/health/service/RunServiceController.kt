package com.windowhyun.health.service

import android.content.Context
import com.windowhyun.health.domain.model.RunGoal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ViewModel 이 Context 를 들지 않고 서비스를 제어할 수 있게 해 주는 얇은 래퍼.
 */
@Singleton
class RunServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start(goal: RunGoal) = RunTrackingService.start(context, goal)

    fun pause() = RunTrackingService.sendAction(context, RunTrackingService.ACTION_PAUSE)

    fun resume() = RunTrackingService.sendAction(context, RunTrackingService.ACTION_RESUME)

    fun stop() = RunTrackingService.sendAction(context, RunTrackingService.ACTION_STOP)
}
