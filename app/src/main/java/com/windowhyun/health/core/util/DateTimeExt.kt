package com.windowhyun.health.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
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

/** 해당 날짜가 속한 주의 시작(월요일). */
fun LocalDate.startOfWeek(): LocalDate {
    val firstDayOfWeek = WeekFields.of(Locale.KOREAN).firstDayOfWeek
    var date = this
    while (date.dayOfWeek != firstDayOfWeek) {
        date = date.minusDays(1)
    }
    return date
}

/** 해당 날짜가 속한 주의 끝(일요일). */
fun LocalDate.endOfWeek(): LocalDate = startOfWeek().plusDays(6)
