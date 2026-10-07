package com.windowhyun.health.service

import android.content.Context
import com.windowhyun.health.domain.model.RunGoal
import com.windowhyun.health.domain.model.RunInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ViewModel 이 Context 를 들지 않고 서비스를 제어할 수 있게 해 주는 얇은 래퍼.
 */
@Singleton
class RunServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : RunControl {
    fun start(goal: RunGoal, interval: RunInterval? = null) = RunTrackingService.start(context, goal, interval)

    override fun pause() = RunTrackingService.sendAction(context, RunTrackingService.ACTION_PAUSE)

    override fun resume() = RunTrackingService.sendAction(context, RunTrackingService.ACTION_RESUME)

    override fun stop() = RunTrackingService.sendAction(context, RunTrackingService.ACTION_STOP)
}

/** 러닝 일시정지 · 재개 · 종료. 시계에서 온 명령을 처리하는 쪽이 서비스 대신 이것만 알면 되게 한다. */
interface RunControl {
    fun pause()
    fun resume()
    fun stop()
}
