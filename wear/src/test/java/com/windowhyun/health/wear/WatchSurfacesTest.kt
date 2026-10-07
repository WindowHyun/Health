package com.windowhyun.health.wear

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.shared.RestSnapshot
import com.windowhyun.health.shared.RunSnapshot
import com.windowhyun.health.shared.WatchRunStatus
import com.windowhyun.health.shared.WearProtocol
import com.windowhyun.health.shared.WorkoutSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 워치페이스 칩(알림) · 타일 · 앱이 꺼져 있어도 깨어나는 서비스. 실제 시계 없이 확인할 수 있는 만큼 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchSurfacesTest {

    private lateinit var context: Context
    private val now = 10_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun manager() = context.getSystemService(NotificationManager::class.java)

    private fun posted(): Notification? = shadowOf(manager()).getNotification(OngoingNotifier.NOTIFICATION_ID)

    private fun run(
        status: WatchRunStatus = WatchRunStatus.TRACKING,
        sentAt: Long = now,
    ) = RunSnapshot(
        status = status, distanceMeters = 2_000.0, elapsedSeconds = 600, currentPaceSecPerKm = 300.0, sentAtMillis = sentAt,
    )

    private fun snapshots(
        run: RunSnapshot = RunSnapshot(),
        rest: RestSnapshot = RestSnapshot.None,
        workout: WorkoutSnapshot = WorkoutSnapshot.None,
    ) = WatchSnapshots(run, rest, workout)

    private fun refresh(snapshots: WatchSnapshots?, at: Long = now) = runBlocking {
        refreshWatchSurfaces(context, read = { snapshots }, nowMillis = at)
    }

    // ----- 진행 중 칩 -----

    @Test
    fun `a run puts a chip on the watch face and counts by itself`() {
        refresh(snapshots(run = run()))

        val notification = posted()!!
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_WORKOUT)
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("러닝")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("2.00km")
        // 시간은 시스템 스톱워치가 센다: 보낸 시각에서 600초 전이 0 이다.
        assertThat(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(notification.`when`).isEqualTo(now - 600_000)
        assertThat(notification.contentIntent).isNotNull()
    }

    @Test
    fun `a rest counts down to its end and disappears by itself when it ends`() {
        val rest = RestSnapshot(active = true, totalSeconds = 90, endsAtMillis = now + 45_000, sentAtMillis = now)

        refresh(snapshots(rest = rest))

        val notification = posted()!!
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("휴식")
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(notification.`when`).isEqualTo(now + 45_000)
        // 끝나는 시각 직후에 알림이 스스로 사라지도록 시간 제한을 건다.
        assertThat(notification.timeoutAfter).isEqualTo(45_000 + 2_000)
    }

    @Test
    fun `a set shows the exercise on the chip`() {
        val set = WorkoutSnapshot(
            active = true, setId = 4, exerciseName = "벤치프레스", setNumber = 2, setCount = 4, reps = 8, sentAtMillis = now,
        )

        refresh(snapshots(workout = set))

        val notification = posted()!!
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("벤치프레스")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("2/4세트")
    }

    @Test
    fun `the chip goes away when the run ends`() {
        refresh(snapshots(run = run()))
        assertThat(posted()).isNotNull()

        refresh(snapshots(run = run(WatchRunStatus.FINISHED)))

        assertThat(posted()).isNull()
    }

    /** 폰이 죽어 소식이 끊기면 지나간 러닝이 워치페이스에 영원히 남지 않는다. */
    @Test
    fun `the chip disappears when the phone goes quiet`() {
        refresh(snapshots(run = run(sentAt = now)))
        assertThat(posted()).isNotNull()

        refresh(snapshots(run = run(sentAt = now)), at = now + WearProtocol.STALE_MILLIS + 1)

        assertThat(posted()).isNull()
    }

    /** Play 서비스가 잠깐 읽기를 실패해도(소식이 없는 것이 아니다) 칩을 치우지 않는다. */
    @Test
    fun `a failed read leaves the chip alone`() {
        refresh(snapshots(run = run()))

        refresh(null)

        assertThat(posted()).isNotNull()
    }

    @Test
    fun `without the notification permission nothing is posted and nothing breaks`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        refresh(snapshots(run = run()))

        assertThat(posted()).isNull()
    }

    @Test
    fun `a quiet run chip times out but a rest times out when it ends`() {
        val quiet = OngoingContent(OngoingContent.Kind.RUN, "러닝", "", tileLines = emptyList())
        val rest = OngoingContent(
            OngoingContent.Kind.REST, "휴식", "", OngoingContent.Clock.Countdown(now + 10_000), tileLines = emptyList(),
        )

        assertThat(OngoingNotifier.timeoutMillis(quiet, now)).isEqualTo(OngoingNotifier.QUIET_TIMEOUT_MILLIS)
        assertThat(OngoingNotifier.timeoutMillis(rest, now)).isEqualTo(12_000)
        // 이미 끝난 휴식이 음수 시간 제한을 만들지 않는다.
        assertThat(OngoingNotifier.timeoutMillis(rest, now + 99_000)).isEqualTo(2_000)
    }

    // ----- 타일 -----

    private fun textsOf(element: LayoutElementBuilders.LayoutElement): List<String> = when (element) {
        is LayoutElementBuilders.Text -> listOf(element.text!!.value)
        is LayoutElementBuilders.Box -> element.contents.flatMap(::textsOf)
        is LayoutElementBuilders.Column -> element.contents.flatMap(::textsOf)
        else -> emptyList()
    }

    private fun firstColumn(element: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.Column? = when (element) {
        is LayoutElementBuilders.Column -> element
        is LayoutElementBuilders.Box -> element.contents.firstNotNullOfOrNull(::firstColumn)
        else -> null
    }

    @Test
    fun `the tile stacks the lines`() {
        val layout = HealthTileService.layout(context, listOf("러닝", "2.00km", "10:00 · 5'00\""))

        assertThat(textsOf(layout)).containsExactly("러닝", "2.00km", "10:00 · 5'00\"").inOrder()
    }

    @Test
    fun `tapping the tile opens the app`() {
        val layout = HealthTileService.layout(context, listOf("Health", "폰에서 운동을 시작하세요"))

        val action = firstColumn(layout)!!.modifiers!!.clickable!!.onClick as ActionBuilders.LaunchAction
        assertThat(action.androidActivity!!.className).isEqualTo(WatchActivity::class.java.name)
        assertThat(action.androidActivity!!.packageName).isEqualTo(context.packageName)
    }

    @Test
    fun `the tile builds with a layout and a resource version`() {
        val tile = HealthTileService.buildTile(context, listOf("Health", "폰에서 운동을 시작하세요"))

        assertThat(tile.resourcesVersion).isNotEmpty()
        assertThat(tile.tileTimeline!!.timelineEntries).hasSize(1)
    }

    // ----- 서비스 등록 -----

    /** 서비스를 매니페스트에 안 적으면 앱이 꺼진 뒤에는 폰 소식을 받을 수 없다. 눈에 안 띄는 실수라 시험한다. */
    @Test
    fun `phone state changes wake the listener service`() {
        val intent = Intent("com.google.android.gms.wearable.DATA_CHANGED")
            .setData(Uri.parse("wear://node-id${WearProtocol.PATH_RUN_STATE}"))
            .setPackage(context.packageName)

        val services = context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)

        assertThat(services.map { it.serviceInfo.name }).contains(PhoneStateListenerService::class.java.name)
    }

    @Test
    fun `every state path is delivered to the listener service`() {
        listOf(WearProtocol.PATH_RUN_STATE, WearProtocol.PATH_REST_STATE, WearProtocol.PATH_WORKOUT_STATE).forEach { path ->
            val intent = Intent("com.google.android.gms.wearable.DATA_CHANGED")
                .setData(Uri.parse("wear://node-id$path"))
                .setPackage(context.packageName)
            val services = context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
            assertThat(services.map { it.serviceInfo.name }).contains(PhoneStateListenerService::class.java.name)
        }
    }

    @Test
    fun `the tile is offered to the system`() {
        val intent = Intent("androidx.wear.tiles.action.BIND_TILE_PROVIDER").setPackage(context.packageName)

        val services = context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)

        assertThat(services.map { it.serviceInfo.name }).contains(HealthTileService::class.java.name)
        assertThat(services.first { it.serviceInfo.name == HealthTileService::class.java.name }.serviceInfo.permission)
            .isEqualTo("com.google.android.wearable.permission.BIND_TILE_PROVIDER")
    }
}
