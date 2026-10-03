package com.windowhyun.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.windowhyun.health.core.designsystem.theme.healthColors
import com.windowhyun.health.ui.components.Hairline
import com.windowhyun.health.ui.navigation.HealthNavHost
import com.windowhyun.health.ui.navigation.TopLevelDestination

/**
 * 앱 뼈대. 하단 네비게이션은 최상위 4개 탭에서만 보이고,
 * 운동 진행 같은 전체화면에서는 숨긴다(조작 실수를 줄이기 위함).
 */
@Composable
fun HealthApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevelRoutes = remember { TopLevelDestination.entries.map { it.route }.toSet() }
    val showBottomBar = currentDestination?.route in topLevelRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                HealthBottomBar(
                    isSelected = { destination ->
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    },
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            HealthNavHost(navController = navController)
        }
    }
}

/**
 * 하단 탭. 화면 캡처에서도 같은 모양을 그릴 수 있게 내비게이션 상태와 떼어 둔다.
 *
 * 기본 NavigationBar 의 알약 모양 선택 표시 대신, 선택된 탭 위에 라임 선을 긋고 글자를 굵게 한다.
 * 선택은 색만으로 알리지 않는다(글자 굵기 · 색 · 선이 함께 바뀐다).
 */
@Composable
internal fun HealthBottomBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = MaterialTheme.healthColors
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .selectableGroup(),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = isSelected(destination)
                val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                    Spacer(Modifier.height(10.dp))
                    Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = tint,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
