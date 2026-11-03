package com.windowhyun.health.service

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** 러닝 중 위치 수신이 끊겨도 포기하지 않고 다시 붙는 것. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunServiceResilienceTest {

    @Test
    fun `the wait before retrying grows and stops at thirty seconds`() {
        assertThat(locationRetryDelayMillis(0)).isEqualTo(2_000)
        assertThat(locationRetryDelayMillis(1)).isEqualTo(4_000)
        assertThat(locationRetryDelayMillis(5)).isEqualTo(12_000)
        assertThat(locationRetryDelayMillis(14)).isEqualTo(30_000)
        assertThat(locationRetryDelayMillis(10_000)).isEqualTo(30_000)
    }

    /** 두 번 실패한 뒤 붙으면, 그 뒤의 위치가 그대로 전달된다. */
    @Test
    fun `a location stream that fails is started again`() = runTest {
        var starts = 0
        val source: Flow<Int> = flow {
            starts++
            if (starts <= 2) throw IOException("끊김 $starts")
            emit(1)
            emit(2)
        }
        val errors = mutableListOf<String>()

        val received = source.retryForever { errors += it.message.orEmpty() }.toList()

        assertThat(received).containsExactly(1, 2).inOrder()
        assertThat(starts).isEqualTo(3)
        assertThat(errors).containsExactly("끊김 1", "끊김 2").inOrder()
        // 기다린 시간: 2초 + 4초
        assertThat(currentTime).isEqualTo(6_000)
    }

    /** 위치를 받아 처리하다 난 오류(수집하는 쪽)는 다시 시작으로 덮지 않는다. */
    @Test
    fun `an error from the collector is not swallowed`() = runTest {
        val source: Flow<Int> = flow { emit(1) }

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                source.retryForever().collect { throw IllegalStateException("처리 오류") }
            }
        }
    }
}
