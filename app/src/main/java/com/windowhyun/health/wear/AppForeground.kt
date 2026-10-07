package com.windowhyun.health.wear

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 폰 앱 화면이 지금 사용자 눈앞에 있는지.
 *
 * 안드로이드 12 부터는 앱이 화면에 없을 때 위치를 쓰는 서비스를 시작할 수 없다. 시계에서 온 "러닝 시작"을
 * 바로 처리할 수 있는지, 아니면 사용자가 알림을 눌러야 하는지 가르는 데 쓴다.
 */
object AppForeground {
    @Volatile
    private var startedActivities = 0

    val isForeground: Boolean get() = startedActivities > 0

    /** 테스트에서 화면이 떠 있는지 정해 둔다. */
    internal fun setStartedActivitiesForTest(count: Int) {
        startedActivities = count
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
