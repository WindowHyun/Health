package com.windowhyun.health.data.catalog

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.domain.model.Exercise
import com.windowhyun.health.domain.model.Routine
import com.windowhyun.health.domain.model.RoutineItem
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 내 루틴을 파일로 내보내기: 다시 불러올 수 있는 파일이 나와야 한다. */
class RoutineExportTest {

    private val date = LocalDate.of(2026, 10, 14)

    private fun exercise(name: String, builtIn: Boolean = true, tracking: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS, rest: Int? = null) =
        Exercise(id = name.hashCode().toLong(), name = name, category = ExerciseCategory.BARBELL, bodyPart = BodyPart.LEG, isBuiltIn = builtIn, defaultRestSeconds = rest, trackingType = tracking)

    private fun item(name: String, index: Int, sets: Int = 3, rest: Int? = null, group: Int = 0, builtIn: Boolean = true) =
        RoutineItem(exercise = exercise(name, builtIn), orderIndex = index, defaultSets = sets, restSeconds = rest, supersetGroup = group)

    private fun routine(name: String, vararg items: RoutineItem, days: Set<DayOfWeek> = emptySet()) =
        Routine(id = 1, name = name, scheduledDays = days, items = items.toList())

    /** 가져오기와 같은 엄격한 규칙(종목 이름 확인 포함)으로 다시 읽는다. */
    private fun reread(json: String, known: Set<String>) = CatalogReader.read(
        json,
        ImportContext(limits = ImportLimits.EXTERNAL, markExercisesBuiltIn = false, checkExerciseReferences = true, knownExerciseNames = known),
    )

    @Test
    fun `exports routines as one template that reads back as the same routines`() {
        val upper = routine(
            "상체 A",
            item("벤치프레스", 0, sets = 4, rest = 120),
            item("케이블 푸시다운", 1, sets = 3, rest = 60, group = 1),
            item("바벨 컬", 2, sets = 3, rest = 75, group = 1),
            item("플랭크", 3, sets = 2),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )
        val lower = routine("하체", item("스쿼트", 0, sets = 5))

        val result = RoutineExport.build(listOf(upper, lower), date)

        assertThat(result.exportedRoutines).isEqualTo(2)
        assertThat(result.skipped).isEmpty()
        val report = reread(result.json, known = setOf("벤치프레스", "케이블 푸시다운", "바벨 컬", "플랭크", "스쿼트"))
        assertThat(report.errors).isEmpty()
        val template = report.templates.single()
        assertThat(template.id).isEqualTo("my_routines_20261014")
        assertThat(template.title).isEqualTo("내 루틴 (2026-10-14)")
        assertThat(template.days.map { it.routineName }).containsExactly("상체 A", "하체").inOrder()
        val day = template.days.first()
        assertThat(day.exercises).containsExactly("벤치프레스" to 4, "케이블 푸시다운" to 3, "바벨 컬" to 3, "플랭크" to 2).inOrder()
        assertThat(day.restSeconds).containsExactly("벤치프레스", 120, "케이블 푸시다운", 60, "바벨 컬", 75)
        assertThat(day.supersets).containsExactly(listOf("케이블 푸시다운", "바벨 컬"))
        assertThat(day.scheduledDays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
    }

    @Test
    fun `items are exported in their order not in list order`() {
        val r = routine("순서", item("나", 1), item("가", 0), item("다", 2))

        val day = reread(RoutineExport.build(listOf(r), date).json, setOf("가", "나", "다")).templates.single().days.single()

        assertThat(day.exercises.map { it.first }).containsExactly("가", "나", "다").inOrder()
    }

    @Test
    fun `custom exercises travel in the file and built in ones do not`() {
        val r = routine("내 운동", item("스쿼트", 0), item("내가 만든 운동", 1, builtIn = false))

        val result = RoutineExport.build(listOf(r), date)

        assertThat(result.customExercises).isEqualTo(1)
        val report = reread(result.json, known = setOf("스쿼트"))
        assertThat(report.errors).isEmpty()
        assertThat(report.exercises.map { it.name }).containsExactly("내가 만든 운동")
        assertThat(report.exercises.single().isBuiltIn).isFalse()
    }

    @Test
    fun `a custom exercise used by several routines is written once`() {
        val a = routine("가", item("내 운동", 0, builtIn = false))
        val b = routine("나", item("내 운동", 0, builtIn = false))

        assertThat(RoutineExport.build(listOf(a, b), date).customExercises).isEqualTo(1)
    }

    @Test
    fun `custom exercise settings are kept`() {
        val custom = Exercise(1, "버티기", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, isBuiltIn = false, defaultRestSeconds = 45, trackingType = ExerciseTrackingType.TIME)
        val r = routine("코어", RoutineItem(exercise = custom, orderIndex = 0, defaultSets = 3))

        val e = reread(RoutineExport.build(listOf(r), date).json, emptySet()).exercises.single()

        assertThat(e.trackingType).isEqualTo(ExerciseTrackingType.TIME)
        assertThat(e.defaultRestSeconds).isEqualTo(45)
        assertThat(e.category).isEqualTo(ExerciseCategory.BODYWEIGHT)
        assertThat(e.bodyPart).isEqualTo(BodyPart.CORE)
    }

    @Test
    fun `routines the file format cannot hold are skipped and named`() {
        val ok = routine("정상", item("스쿼트", 0))
        val empty = routine("비어 있음")
        val twice = routine("두 번", item("스쿼트", 0), item("스쿼트", 1))
        val badName = routine("이름이 이상함", item(" 앞공백", 0))
        val tooLongName = routine("긴 이름", item("가".repeat(41), 0))
        val badSets = routine("세트 이상", item("스쿼트", 0, sets = 21))
        val badRest = routine("휴식 이상", item("스쿼트", 0, rest = 4))
        val tooMany = routine("너무 많음", *(0..30).map { item("종목$it", it) }.toTypedArray())

        val result = RoutineExport.build(listOf(ok, empty, twice, badName, tooLongName, badSets, badRest, tooMany), date)

        assertThat(result.exportedRoutines).isEqualTo(1)
        assertThat(result.skipped).containsExactly("비어 있음", "두 번", "이름이 이상함", "긴 이름", "세트 이상", "휴식 이상", "너무 많음").inOrder()
        assertThat(reread(result.json, setOf("스쿼트")).errors).isEmpty()
    }

    @Test
    fun `nothing to export gives an empty result`() {
        val none = RoutineExport.build(emptyList(), date)
        val onlyBad = RoutineExport.build(listOf(routine("비어 있음")), date)

        assertThat(none.json).isEmpty()
        assertThat(none.exportedRoutines).isEqualTo(0)
        assertThat(onlyBad.json).isEmpty()
        assertThat(onlyBad.skipped).containsExactly("비어 있음")
    }

    /** 루틴 이름은 자유롭게 지을 수 있어서, 파일 규칙(길이 · 공백 · 중복)에 맞게 다듬는다. */
    @Test
    fun `routine names are cleaned and made unique`() {
        val long = "가".repeat(60)
        val rs = listOf(
            routine("같은 이름", item("스쿼트", 0)),
            routine("같은 이름", item("스쿼트", 0)),
            routine("  공백  ", item("스쿼트", 0)),
            routine(long, item("스쿼트", 0)),
            routine(long, item("스쿼트", 0)),
        )

        val names = reread(RoutineExport.build(rs, date).json, setOf("스쿼트")).templates.single().days.map { it.routineName }

        assertThat(names).hasSize(5)
        assertThat(names).containsNoDuplicates()
        assertThat(names[0]).isEqualTo("같은 이름")
        assertThat(names[1]).isEqualTo("같은 이름 (2)")
        assertThat(names[2]).isEqualTo("공백")
        assertThat(names.all { it.length <= 40 }).isTrue()
    }

    @Test
    fun `a name made only of control characters cannot be exported`() {
        val result = RoutineExport.build(listOf(routine("\u0007\u0008", item("스쿼트", 0))), date)

        assertThat(result.exportedRoutines).isEqualTo(0)
        assertThat(result.skipped).hasSize(1)
    }

    @Test
    fun `more than 14 routines are split into several templates with distinct ids`() {
        val rs = (1..30).map { routine("루틴 $it", item("스쿼트", 0)) }

        val report = reread(RoutineExport.build(rs, date).json, setOf("스쿼트"))

        assertThat(report.errors).isEmpty()
        assertThat(report.templates.map { it.days.size }).containsExactly(14, 14, 2).inOrder()
        assertThat(report.templates.map { it.id }).containsExactly("my_routines_20261014", "my_routines_20261014_2", "my_routines_20261014_3").inOrder()
        assertThat(report.templates.map { it.title }).containsExactly("내 루틴 (2026-10-14) 1", "내 루틴 (2026-10-14) 2", "내 루틴 (2026-10-14) 3").inOrder()
    }

    @Test
    fun `superset runs are found by adjacent equal group numbers`() {
        val r = routine(
            "슈퍼셋",
            item("가", 0, group = 1),
            item("나", 1, group = 1),
            item("다", 2, group = 0),
            item("라", 3, group = 2),
            item("마", 4, group = 3),
            item("바", 5, group = 3),
            item("사", 6, group = 3),
            item("아", 7, group = 4),
        )

        val day = reread(RoutineExport.build(listOf(r), date).json, setOf("가", "나", "다", "라", "마", "바", "사", "아")).templates.single().days.single()

        // 번호 2 와 4 는 혼자라 묶음이 아니다.
        assertThat(day.supersets).containsExactly(listOf("가", "나"), listOf("마", "바", "사")).inOrder()
    }

    @Test
    fun `two separate runs with the same group number become two supersets`() {
        val r = routine(
            "떨어진 묶음",
            item("가", 0, group = 1), item("나", 1, group = 1),
            item("다", 2, group = 0),
            item("라", 3, group = 1), item("마", 4, group = 1),
        )

        val day = reread(RoutineExport.build(listOf(r), date).json, setOf("가", "나", "다", "라", "마")).templates.single().days.single()

        assertThat(day.supersets).containsExactly(listOf("가", "나"), listOf("라", "마")).inOrder()
    }

    @Test
    fun `the description counts the routines`() {
        val report = reread(RoutineExport.build(listOf(routine("가", item("스쿼트", 0)), routine("나", item("스쿼트", 0))), date).json, setOf("스쿼트"))

        assertThat(report.templates.single().description).contains("루틴 2개")
    }
}
