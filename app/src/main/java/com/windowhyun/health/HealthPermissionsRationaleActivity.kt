package com.windowhyun.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.windowhyun.health.core.designsystem.theme.HealthTheme

/**
 * Health Connect 가 "이 앱이 왜 이 권한을 쓰는가"를 보여 줄 때 여는 화면.
 * 이 화면이 없으면 Health Connect 가 앱을 허용 목록에 올려 주지 않는다.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    ) {
                        Text("Health Connect 사용 안내", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = RATIONALE,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val RATIONALE =
            "이 앱은 서버가 없고, 기록은 모두 이 기기 안에만 저장됩니다. Health Connect 연동은 사용자가 설정에서 직접 켠 경우에만 동작합니다.\n\n" +
                "내보내기: 끝난 러닝과 헬스 운동(시간, 거리, 걸음 수, 칼로리)을 Health Connect 에 기록해 다른 건강 앱과 나눌 수 있게 합니다. 앱에서 지운 기록은 Health Connect 에서도 지웁니다.\n\n" +
                "체중 읽기: 켜면 Health Connect 의 최근 체중을 가져와 러닝 칼로리 추정에 씁니다.\n\n" +
                "심박 읽기: 러닝 상세 화면에 그 시간대의 평균 · 최대 심박을 보여 주는 데에만 씁니다. 저장하지 않습니다.\n\n" +
                "권한은 Health Connect 설정에서 언제든 끌 수 있습니다."
    }
}
