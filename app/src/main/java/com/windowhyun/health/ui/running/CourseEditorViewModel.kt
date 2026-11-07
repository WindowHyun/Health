package com.windowhyun.health.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windowhyun.health.domain.course.CourseGeometry
import com.windowhyun.health.domain.model.Course
import com.windowhyun.health.domain.model.CoursePoint
import com.windowhyun.health.domain.repository.CourseRepository
import com.windowhyun.health.domain.repository.LocationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class CourseEditorUiState(
    val points: List<CoursePoint> = emptyList(),
    val name: String = "",
    /** 지도를 옮겨 보여 줄 현재 위치. 못 구하면 null(기본 위치 그대로). */
    val center: CoursePoint? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val distanceMeters: Double get() = CourseGeometry.length(points)
    val canSave: Boolean get() = points.size >= 2 && !saving
}

/** 지도를 눌러 코스를 찍고 저장한다. */
@HiltViewModel
class CourseEditorViewModel @Inject constructor(
    private val courses: CourseRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(CourseEditorUiState())
    val state: StateFlow<CourseEditorUiState> = _state.asStateFlow()

    init {
        // 지금 있는 곳 근처에서 코스를 찍는 일이 대부분이라, 위치를 얻으면 지도를 그리로 옮긴다. 못 얻어도 괜찮다.
        viewModelScope.launch {
            if (!locationTracker.hasLocationPermission() || !locationTracker.isLocationAvailable()) return@launch
            val sample = runCatching {
                withTimeoutOrNull(LOCATION_WAIT_MILLIS) { locationTracker.locationUpdates(1_000L).first() }
            }.getOrNull() ?: return@launch
            _state.update { it.copy(center = CoursePoint(sample.latitude, sample.longitude)) }
        }
    }

    fun addPoint(point: CoursePoint) = _state.update {
        if (it.points.size >= MAX_POINTS) it.copy(error = "지점은 최대 ${MAX_POINTS}개까지 찍을 수 있어요.")
        else it.copy(points = it.points + point, error = null)
    }

    fun undo() = _state.update { it.copy(points = it.points.dropLast(1), error = null) }

    fun clear() = _state.update { it.copy(points = emptyList(), error = null) }

    fun setName(name: String) = _state.update { it.copy(name = name.take(CourseNameLimit)) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val name = current.name.ifBlank { "코스 ${formatKm(current.distanceMeters)}" }
                courses.save(Course(name = name, points = current.points))
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "코스를 저장하지 못했어요.") }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    companion object {
        const val MAX_POINTS = 500
        const val CourseNameLimit = 40
        private const val LOCATION_WAIT_MILLIS = 6_000L

        fun formatKm(meters: Double) = String.format(java.util.Locale.US, "%.1fkm", meters / 1000.0)
    }
}
