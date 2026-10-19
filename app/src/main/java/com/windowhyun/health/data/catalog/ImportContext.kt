package com.windowhyun.health.data.catalog

/** 파일 하나가 가질 수 있는 크기의 한도. 외부에서 받은 파일이 앱을 느리게 하거나 멈추지 못하게 한다. */
data class ImportLimits(
    val maxBytes: Int,
    /** JSON 중첩 깊이. 템플릿 파일의 정상 깊이는 7이다. */
    val maxDepth: Int,
    val maxExercises: Int,
    val maxTemplates: Int,
    val maxDaysPerTemplate: Int,
    val maxItemsPerDay: Int,
    /** 한 번에 보여 주는 문제의 수(오류와 알림 각각). 넘는 것은 개수만 알려 준다. */
    val maxIssues: Int = 50,
) {
    companion object {
        /** 사용자가 고른 파일이나 붙여넣은 글. */
        val EXTERNAL = ImportLimits(
            maxBytes = 256 * 1024,
            maxDepth = 8,
            maxExercises = 100,
            maxTemplates = 30,
            maxDaysPerTemplate = 14,
            maxItemsPerDay = 30,
        )

        /** 앱에 함께 들어가는 파일. 직접 관리하는 데이터라 한도가 넉넉하다. */
        val BUNDLED = ImportLimits(
            maxBytes = 2 * 1024 * 1024,
            maxDepth = 8,
            maxExercises = 1000,
            maxTemplates = 200,
            maxDaysPerTemplate = 14,
            maxItemsPerDay = 30,
        )
    }
}

/** 글자 수 한도와 값의 범위. 앱에 포함된 파일과 가져온 파일에 똑같이 적용한다. */
object CatalogRules {
    const val MAX_ID_LENGTH = 40
    const val MAX_NAME_LENGTH = 40
    const val MAX_TITLE_LENGTH = 60
    const val MAX_DESCRIPTION_LENGTH = 2000
    const val MAX_SUPERSET_LABEL_LENGTH = 8

    /** 편집 화면의 세트 수 한도. */
    val SETS = 1..20

    /** 설정의 기본 휴식과 같은 한도. */
    val REST_SECONDS = 5..600

    /** 템플릿 id 에 쓸 수 있는 글자. */
    val ID_PATTERN = Regex("[A-Za-z0-9_-]+")

    /** 내 루틴을 내보낼 때 템플릿 하나에 담는 루틴 수(파일 하나의 `days` 한도와 같다). */
    const val MAX_DAYS_PER_TEMPLATE_FOR_EXPORT = 14

    /** 지원하는 파일 형식 버전. 파일에 적혀 있으면 이 값이어야 한다. */
    const val FORMAT_VERSION = 1
}

/**
 * 파일을 읽을 때 기기의 상태와 맞춰 보는 정보. 앱에 포함된 파일은 기본값 그대로 읽는다.
 */
data class ImportContext(
    val limits: ImportLimits = ImportLimits.BUNDLED,
    /** 파일의 종목을 기본 종목으로 표시할지. 앱에 포함된 파일은 true, 가져온 파일은 false(직접 만든 종목이 된다). */
    val markExercisesBuiltIn: Boolean = true,
    /** 기기에 이미 있는 종목 이름. 파일의 종목이 이 이름이면 건너뛴다(경고). */
    val knownExerciseNames: Set<String> = emptySet(),
    /**
     * true 면 템플릿이 쓰는 종목이 [knownExerciseNames] 나 파일의 `exercises` 에 있어야 한다(오류).
     * 이름이 하나라도 틀리면 루틴에서 그 종목이 조용히 빠지므로, 외부 파일은 반드시 켠다.
     */
    val checkExerciseReferences: Boolean = false,
    /** 앱에 포함된 템플릿의 id. 같은 id 는 쓸 수 없다(오류). */
    val reservedTemplateIds: Set<String> = emptySet(),
    /** 이미 가져온 템플릿의 id. 같은 id 를 가져오면 덮어쓴다(경고). */
    val replaceableTemplateIds: Set<String> = emptySet(),
    /**
     * 다른 템플릿의 루틴 이름(템플릿 id → 이름들). 겹치면 어느 프로그램인지 헷갈리므로 알려 준다(경고).
     * 이 파일이 같은 id 로 덮어쓸 템플릿의 이름은 곧 사라지므로 빼고 본다.
     */
    val otherRoutineNames: Map<String, Set<String>> = emptyMap(),
    /**
     * false 면 보이지 않는 문자 검사를 하지 않는다. 이미 저장된 글을 다시 읽을 때만 쓴다:
     * 옛 버전이 받아들여 저장한 글을 새 규칙으로 거절하면 저장해 둔 템플릿이 통째로 읽히지 않게 된다.
     */
    val rejectInvisibleText: Boolean = true,
    /**
     * false 면 서로 다른 템플릿이 같은 루틴 이름을 가져도 오류로 보지 않는다(한 템플릿 안에서 겹치는 것은 여전히 오류).
     * 가져온 템플릿은 여러 번에 걸쳐 쌓이고, 가져오기 검사는 이름이 겹치는 것을 경고만 하고 받아들이므로,
     * 저장된 파일을 다시 읽을 때는 겹쳐도 읽혀야 한다.
     */
    val routineNamesUniqueAcrossTemplates: Boolean = true,
) {
    companion object {
        /** 저장해 둔 가져온 템플릿(파일 · 백업)을 다시 읽을 때. 이미 받아들인 내용이라 새로 따지지 않는다. */
        val STORED = ImportContext(
            limits = ImportLimits.BUNDLED,
            markExercisesBuiltIn = false,
            rejectInvisibleText = false,
            routineNamesUniqueAcrossTemplates = false,
        )
    }
}
