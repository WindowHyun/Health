package com.windowhyun.health.service

import com.windowhyun.health.core.model.DistanceUnit
import com.windowhyun.health.domain.model.IntervalPhase
import com.windowhyun.health.domain.model.RunCue

/**
 * 러닝 중 읽어 줄 문장을 만든다. 화면이나 안드로이드 없이 문장만 만들어서 JVM 테스트로 확인한다.
 *
 * 달리는 중에 듣는 말이라 짧게 한다. 숫자는 "5분 30초"처럼 읽기 쉬운 단위로 바꾼다.
 */
internal object RunVoiceScript {

    /** [cue] 에 맞는 문장. 읽을 필요가 없는 순간이면 null. */
    fun sentenceFor(cue: RunCue, unit: DistanceUnit): String? = when (cue) {
        is RunCue.LapCompleted -> lap(cue, unit)
        RunCue.GoalReached -> "목표를 달성했어요. 수고하셨어요."
        RunCue.AutoPaused -> "일시정지"
        RunCue.AutoResumed -> "다시 시작"
        is RunCue.IntervalChanged -> interval(cue)
        RunCue.IntervalsFinished -> "인터벌이 모두 끝났어요."
    }

    private fun lap(cue: RunCue.LapCompleted, unit: DistanceUnit): String? {
        val lap = cue.lap ?: return null
        val parts = mutableListOf<String>()
        parts += "${spokenDistance(cue.totalDistanceMeters, unit)} 지났어요."
        if (lap.paceSecPerKm > 0) {
            // 초/km 를 보여 주는 거리 단위(km 또는 마일)당 초로 바꾼다.
            val pace = lap.paceSecPerKm * 0.001 / unit.perMeter
            parts += "이번 구간 페이스 ${spokenDuration(pace.toLong())}."
        }
        parts += "총 시간 ${spokenDuration(cue.totalDurationSeconds)}."
        return parts.joinToString(" ")
    }

    private fun interval(cue: RunCue.IntervalChanged): String = when (cue.phase) {
        IntervalPhase.RUN -> "달리기 ${cue.round}번째, 전체 ${cue.rounds}번 중"
        IntervalPhase.WALK -> "걷기"
    }

    /** 3.00km -> "3킬로미터", 1.5km -> "1.5킬로미터", 마일이면 "마일". */
    fun spokenDistance(meters: Double, unit: DistanceUnit): String {
        val value = unit.fromMeters(meters)
        val rounded = Math.round(value * 100) / 100.0
        val text = if (rounded == Math.floor(rounded)) rounded.toLong().toString() else {
            // 소수는 읽기 편하게 한 자리만. 1.50 -> 1.5, 1.25 -> 1.25
            rounded.toString().trimEnd('0').trimEnd('.')
        }
        val label = if (unit == DistanceUnit.MILE) "마일" else "킬로미터"
        return "$text$label"
    }

    /** 330 -> "5분 30초", 3600 -> "1시간", 45 -> "45초". */
    fun spokenDuration(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        val parts = buildList {
            if (h > 0) add("${h}시간")
            if (m > 0) add("${m}분")
            if (s > 0 || (h == 0L && m == 0L)) add("${s}초")
        }
        return parts.joinToString(" ")
    }
}
