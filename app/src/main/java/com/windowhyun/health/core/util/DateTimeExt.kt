package com.windowhyun.health.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val koreanDateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val koreanFullDateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

fun LocalDate.toEpochDayLong(): Long = toEpochDay()

fun Long.epochDayToLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

fun Long.epochMillisToLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun LocalDate.formatKorean(): String = format(koreanDateFormatter)

fun LocalDate.formatKoreanFull(): String = format(koreanFullDateFormatter)

fun Long.formatTimeOfDay(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(timeFormatter)

/**
 * 한 주의 첫 요일: 일요일(한국 달력과 같다). 주간 요약과 캘린더가 함께 쓴다.
 *
 * 기기 언어 설정에서 읽으면 설정에 따라 요일이 바뀔 수 있어 고정해 둔다.
 */
val FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.SUNDAY

/** 해당 날짜가 속한 주의 시작(일요일). */
fun LocalDate.startOfWeek(): LocalDate {
    var date = this
    while (date.dayOfWeek != FIRST_DAY_OF_WEEK) {
        date = date.minusDays(1)
    }
    return date
}

/** 해당 날짜가 속한 주의 끝(토요일). */
fun LocalDate.endOfWeek(): LocalDate = startOfWeek().plusDays(6)

/**
 * 현재 날짜를 흘려 보내는 Flow. 날짜가 바뀔 때만 새 값을 낸다.
 *
 * ViewModel 이 만들어질 때 LocalDate.now() 를 한 번 읽어 두면, 앱을 켜 둔 채
 * 자정을 넘겼을 때 "오늘"이 어제로 남는다. 홈의 날짜·오늘의 루틴·주간 범위가
 * 모두 여기에 걸린다.
 */
fun currentDateFlow(
    zone: ZoneId = ZoneId.systemDefault(),
    checkIntervalMillis: Long = 60_000,
): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now(zone))
        delay(checkIntervalMillis)
    }
}.distinctUntilChanged()
