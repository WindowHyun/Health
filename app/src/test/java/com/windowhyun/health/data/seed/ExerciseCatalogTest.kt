package com.windowhyun.health.data.seed

import com.google.common.truth.Truth.assertThat
import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.core.util.BundledData
import com.windowhyun.health.core.util.CatalogFormatException
import org.junit.Assert.assertThrows
import org.junit.Test

/** 기본 종목 파일(exercises.json)을 읽는 규칙. 파일을 손으로 고치다 틀리면 어디가 왜 틀렸는지 알려 줘야 한다. */
class ExerciseCatalogTest {

    private fun file(vararg items: String) = """{"exercises":[${items.joinToString(",")}]}"""

    private fun item(
        name: String = "벤치프레스",
        category: String = "BARBELL",
        bodyPart: String = "CHEST",
        extra: String = "",
    ) = """{"name":"$name","category":"$category","bodyPart":"$bodyPart"$extra}"""

    private fun tracking(value: String) = ",\"trackingType\":\"$value\""

    /** 숫자가 아닌 값도 넣어 볼 수 있게 문자열 그대로 받는다. */
    private fun rest(value: String) = ",\"defaultRestSeconds\":$value"

    private fun rejects(json: String): String =
        assertThrows(CatalogFormatException::class.java) { ExerciseCatalog.parse(json, "test.json") }.message.orEmpty()

    @Test
    fun `reads name category and body part and marks it built in`() {
        val result = ExerciseCatalog.parse(file(item()))

        assertThat(result).hasSize(1)
        val e = result.single()
        assertThat(e.name).isEqualTo("벤치프레스")
        assertThat(e.category).isEqualTo(ExerciseCategory.BARBELL)
        assertThat(e.bodyPart).isEqualTo(BodyPart.CHEST)
        assertThat(e.isBuiltIn).isTrue()
        assertThat(e.id).isEqualTo(0L)
    }

    /** 적지 않으면 중량×횟수로 기록하고, 종목 자체의 기본 휴식은 없다(앱 설정을 따른다). */
    @Test
    fun `omitted tracking type is weight reps and omitted rest is null`() {
        val e = ExerciseCatalog.parse(file(item())).single()

        assertThat(e.trackingType).isEqualTo(ExerciseTrackingType.WEIGHT_REPS)
        assertThat(e.defaultRestSeconds).isNull()
    }

    @Test
    fun `tracking type and default rest are read when given`() {
        val e = ExerciseCatalog.parse(
            file(item(name = "플랭크", category = "BODYWEIGHT", bodyPart = "CORE", extra = tracking("TIME") + rest("45"))),
        ).single()

        assertThat(e.trackingType).isEqualTo(ExerciseTrackingType.TIME)
        assertThat(e.defaultRestSeconds).isEqualTo(45)
    }

    /** 파일 순서가 곧 처음 설치할 때 들어가는 순서다. */
    @Test
    fun `keeps the file order`() {
        val names = ExerciseCatalog.parse(file(item("나"), item("가"), item("다"))).map { it.name }

        assertThat(names).containsExactly("나", "가", "다").inOrder()
    }

    @Test
    fun `an empty list is allowed`() {
        assertThat(ExerciseCatalog.parse(file())).isEmpty()
    }

    // ----- 틀린 파일 -----

    @Test
    fun `unknown category lists the valid values`() {
        val message = rejects(file(item(category = "BARBEL")))

        assertThat(message).contains("test.json")
        assertThat(message).contains("category")
        assertThat(message).contains("BARBEL")
        assertThat(message).contains("DUMBBELL")
    }

    @Test
    fun `unknown body part and tracking type are rejected`() {
        assertThat(rejects(file(item(bodyPart = "CHST")))).contains("bodyPart")
        assertThat(rejects(file(item(extra = tracking("WEIGHT"))))).contains("trackingType")
    }

    @Test
    fun `a misspelled key is rejected instead of ignored`() {
        val message = rejects(file("""{"name":"벤치프레스","categroy":"BARBELL","category":"BARBELL","bodyPart":"CHEST"}"""))

        assertThat(message).contains("categroy")
    }

    @Test
    fun `a missing required field is rejected`() {
        val message = rejects(file("""{"name":"벤치프레스","category":"BARBELL"}"""))

        assertThat(message).contains("bodyPart")
    }

    /** 이름은 DB 의 유니크 키이고 템플릿 · 기록이 이름으로 가리킨다. */
    @Test
    fun `duplicate names are rejected and named in the message`() {
        val message = rejects(file(item("스쿼트"), item("데드리프트"), item("스쿼트")))

        assertThat(message).contains("스쿼트")
        assertThat(message).contains("exercises[2]")
    }

    @Test
    fun `blank name is rejected`() {
        assertThat(rejects(file(item(name = "  ")))).contains("name")
    }

    @Test
    fun `default rest must be between 5 and 600 seconds`() {
        assertThat(ExerciseCatalog.parse(file(item(extra = rest("5")))).single().defaultRestSeconds).isEqualTo(5)
        assertThat(ExerciseCatalog.parse(file(item(extra = rest("600")))).single().defaultRestSeconds).isEqualTo(600)

        assertThat(rejects(file(item(extra = rest("4"))))).contains("defaultRestSeconds")
        assertThat(rejects(file(item(extra = rest("601"))))).contains("defaultRestSeconds")
    }

    @Test
    fun `malformed json is reported with the file name`() {
        assertThat(rejects("{")).startsWith("test.json:")
        assertThat(rejects("")).startsWith("test.json:")
        assertThat(rejects("[]")).startsWith("test.json:")
    }

    @Test
    fun `a wrong value type is rejected`() {
        assertThat(rejects(file(item(extra = rest("\"길게\""))))).contains("test.json")
    }

    // ----- 앱에 들어간 실제 파일 -----

    @Test
    fun `the bundled file is read and equals what the app exposes`() {
        val bundled = ExerciseCatalog.loadBundled()

        assertThat(bundled).isNotEmpty()
        assertThat(DefaultExercises.all).isEqualTo(bundled)
        assertThat(bundled.map { it.name }).containsNoDuplicates()
        assertThat(bundled.all { it.isBuiltIn }).isTrue()
    }

    /** 첫 사용 때 한 번만 읽고 같은 목록을 돌려준다. */
    @Test
    fun `the bundled list is loaded once`() {
        assertThat(DefaultExercises.all).isSameInstanceAs(DefaultExercises.all)
    }

    @Test
    fun `a missing bundled file is reported with its path`() {
        val e = assertThrows(CatalogFormatException::class.java) { BundledData.readText("health/없는파일.json") }

        assertThat(e.message).contains("health/없는파일.json")
    }
}
