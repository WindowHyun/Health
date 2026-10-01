package com.windowhyun.health.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 기록에 붙는 메모 하나를 편집한다. 운동 결과 · 기록 상세 화면이 함께 쓴다.
 *
 * - 불러오기 전에 화면을 나가면 빈 메모를 저장해 원래 메모를 지워 버렸다. 불러온 뒤에,
 *   그리고 바뀐 경우에만 저장한다.
 * - 저장은 [appScope] 에서 한다. viewModelScope 는 뒤로 가기와 함께 취소되어 쓰기가
 *   끊길 수 있다.
 */
class MemoDraft(
    loadScope: CoroutineScope,
    private val appScope: CoroutineScope,
    load: suspend () -> String,
    private val store: suspend (String) -> Unit,
) {
    // null 은 "아직 불러오지 않음". 사용자가 먼저 입력하면 덮어쓰지 않는다.
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    /** DB 에 있는 메모. null 이면 아직 불러오지 않았다는 뜻이다. */
    private var saved: String? = null

    init {
        loadScope.launch {
            val stored = load()
            saved = stored
            _text.compareAndSet(null, stored)
        }
    }

    fun set(memo: String) {
        _text.value = memo
    }

    /** 메모가 바뀌었으면 저장한다. 여러 번 불려도 한 번만 쓴다. */
    fun save() {
        val stored = saved ?: return
        val current = _text.value ?: return
        if (current == stored) return
        saved = current
        appScope.launch { store(current) }
    }
}
