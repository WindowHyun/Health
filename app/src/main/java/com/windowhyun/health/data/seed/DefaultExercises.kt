package com.windowhyun.health.data.seed

import com.windowhyun.health.core.model.BodyPart
import com.windowhyun.health.core.model.ExerciseCategory
import com.windowhyun.health.core.model.ExerciseTrackingType
import com.windowhyun.health.data.local.entity.ExerciseEntity

/**
 * 기본 운동 종목. 처음 실행할 때 넣고, 앱을 업데이트해 목록이 늘면 빠진 것만 채운다
 * ([ExerciseSeedCallback]). 사용자는 여기에 자유롭게 종목을 추가할 수 있다.
 *
 * 이름은 루틴 템플릿([com.windowhyun.health.ui.gym.RoutineTemplates])과 이미 설치된 기기의
 * 기록이 가리키므로, 있는 종목의 이름을 바꾸거나 지우지 않는다. 종목을 늘리는 것만 한다.
 * 기록 방식은 중량×횟수가 기본이고, 맨몸 횟수 운동은 횟수만, 버티는 운동과 유산소는 시간만 쓴다.
 */
object DefaultExercises {

    private fun exercise(
        name: String,
        category: ExerciseCategory,
        bodyPart: BodyPart,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_REPS,
    ) = ExerciseEntity(
        name = name,
        category = category,
        bodyPart = bodyPart,
        isBuiltIn = true,
        trackingType = trackingType,
    )

    val all: List<ExerciseEntity> = listOf(
        // 가슴
        exercise("벤치프레스", ExerciseCategory.BARBELL, BodyPart.CHEST),
        exercise("인클라인 벤치프레스", ExerciseCategory.BARBELL, BodyPart.CHEST),
        exercise("덤벨 벤치프레스", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("덤벨 플라이", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("체스트 프레스 머신", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("케이블 크로스오버", ExerciseCategory.CABLE, BodyPart.CHEST),
        exercise("딥스", ExerciseCategory.BODYWEIGHT, BodyPart.CHEST, ExerciseTrackingType.REPS_ONLY),
        exercise("푸시업", ExerciseCategory.BODYWEIGHT, BodyPart.CHEST, ExerciseTrackingType.REPS_ONLY),
        // 등
        exercise("데드리프트", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("바벨로우", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("덤벨로우", ExerciseCategory.DUMBBELL, BodyPart.BACK),
        exercise("랫풀다운", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("시티드 케이블로우", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("풀업", ExerciseCategory.BODYWEIGHT, BodyPart.BACK, ExerciseTrackingType.REPS_ONLY),
        exercise("티바로우", ExerciseCategory.MACHINE, BodyPart.BACK),
        // 어깨
        exercise("오버헤드 프레스", ExerciseCategory.BARBELL, BodyPart.SHOULDER),
        exercise("덤벨 숄더프레스", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("사이드 레터럴 레이즈", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("벤트오버 레터럴 레이즈", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("페이스풀", ExerciseCategory.CABLE, BodyPart.SHOULDER),
        exercise("슈러그", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        // 팔
        exercise("바벨 컬", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("덤벨 컬", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("해머 컬", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("케이블 푸시다운", ExerciseCategory.CABLE, BodyPart.ARM),
        exercise("라잉 트라이셉스 익스텐션", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("클로즈그립 벤치프레스", ExerciseCategory.BARBELL, BodyPart.ARM),
        // 하체
        exercise("스쿼트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("프론트 스쿼트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("레그프레스", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("레그 익스텐션", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("레그 컬", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("루마니안 데드리프트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("런지", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("카프레이즈", ExerciseCategory.MACHINE, BodyPart.LEG),
        // 코어
        exercise("플랭크", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.TIME),
        exercise("크런치", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("행잉 레그레이즈", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("케이블 크런치", ExerciseCategory.CABLE, BodyPart.CORE),
        // 전신
        exercise("케틀벨 스윙", ExerciseCategory.KETTLEBELL, BodyPart.FULL_BODY),
        exercise("버피", ExerciseCategory.BODYWEIGHT, BodyPart.FULL_BODY, ExerciseTrackingType.REPS_ONLY),

        // ----- v0.8.1 에서 늘린 종목 -----
        // 가슴
        exercise("디클라인 벤치프레스", ExerciseCategory.BARBELL, BodyPart.CHEST),
        exercise("인클라인 덤벨 프레스", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("인클라인 덤벨 플라이", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("덤벨 풀오버", ExerciseCategory.DUMBBELL, BodyPart.CHEST),
        exercise("스미스머신 벤치프레스", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("스미스머신 인클라인 프레스", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("인클라인 체스트 프레스 머신", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("펙덱 플라이", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("로우 케이블 크로스오버", ExerciseCategory.CABLE, BodyPart.CHEST),
        exercise("어시스티드 딥스", ExerciseCategory.MACHINE, BodyPart.CHEST),
        exercise("와이드 푸시업", ExerciseCategory.BODYWEIGHT, BodyPart.CHEST, ExerciseTrackingType.REPS_ONLY),
        exercise("디클라인 푸시업", ExerciseCategory.BODYWEIGHT, BodyPart.CHEST, ExerciseTrackingType.REPS_ONLY),
        // 등
        exercise("펜들레이 로우", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("랙풀", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("스모 데드리프트", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("굿모닝", ExerciseCategory.BARBELL, BodyPart.BACK),
        exercise("원암 케이블 로우", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("시티드 로우 머신", ExerciseCategory.MACHINE, BodyPart.BACK),
        exercise("체스트 서포티드 로우", ExerciseCategory.MACHINE, BodyPart.BACK),
        exercise("와이드그립 랫풀다운", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("클로즈그립 랫풀다운", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("리버스그립 랫풀다운", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("스트레이트암 풀다운", ExerciseCategory.CABLE, BodyPart.BACK),
        exercise("어시스티드 풀업", ExerciseCategory.MACHINE, BodyPart.BACK),
        exercise("친업", ExerciseCategory.BODYWEIGHT, BodyPart.BACK, ExerciseTrackingType.REPS_ONLY),
        exercise("인버티드 로우", ExerciseCategory.BODYWEIGHT, BodyPart.BACK, ExerciseTrackingType.REPS_ONLY),
        exercise("백 익스텐션", ExerciseCategory.BODYWEIGHT, BodyPart.BACK, ExerciseTrackingType.REPS_ONLY),
        // 어깨
        exercise("아놀드 프레스", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("머신 숄더프레스", ExerciseCategory.MACHINE, BodyPart.SHOULDER),
        exercise("스미스머신 숄더프레스", ExerciseCategory.MACHINE, BodyPart.SHOULDER),
        exercise("프론트 레이즈", ExerciseCategory.DUMBBELL, BodyPart.SHOULDER),
        exercise("케이블 레터럴 레이즈", ExerciseCategory.CABLE, BodyPart.SHOULDER),
        exercise("머신 레터럴 레이즈", ExerciseCategory.MACHINE, BodyPart.SHOULDER),
        exercise("리어 델트 플라이 머신", ExerciseCategory.MACHINE, BodyPart.SHOULDER),
        exercise("업라이트 로우", ExerciseCategory.BARBELL, BodyPart.SHOULDER),
        exercise("바벨 슈러그", ExerciseCategory.BARBELL, BodyPart.SHOULDER),
        exercise("파이크 푸시업", ExerciseCategory.BODYWEIGHT, BodyPart.SHOULDER, ExerciseTrackingType.REPS_ONLY),
        // 팔
        exercise("EZ바 컬", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("프리처 컬", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("인클라인 덤벨 컬", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("컨센트레이션 컬", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("케이블 컬", ExerciseCategory.CABLE, BodyPart.ARM),
        exercise("리버스 컬", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("바이셉스 컬 머신", ExerciseCategory.MACHINE, BodyPart.ARM),
        exercise("손목 컬", ExerciseCategory.BARBELL, BodyPart.ARM),
        exercise("트라이셉스 로프 푸시다운", ExerciseCategory.CABLE, BodyPart.ARM),
        exercise("오버헤드 트라이셉스 익스텐션", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("케이블 오버헤드 트라이셉스 익스텐션", ExerciseCategory.CABLE, BodyPart.ARM),
        exercise("트라이셉스 킥백", ExerciseCategory.DUMBBELL, BodyPart.ARM),
        exercise("트라이셉스 익스텐션 머신", ExerciseCategory.MACHINE, BodyPart.ARM),
        exercise("벤치 딥스", ExerciseCategory.BODYWEIGHT, BodyPart.ARM, ExerciseTrackingType.REPS_ONLY),
        exercise("다이아몬드 푸시업", ExerciseCategory.BODYWEIGHT, BodyPart.ARM, ExerciseTrackingType.REPS_ONLY),
        // 하체
        exercise("스미스머신 스쿼트", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("핵 스쿼트", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("고블릿 스쿼트", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("불가리안 스플릿 스쿼트", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("워킹 런지", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("리버스 런지", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("스텝업", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("스티프 레그 데드리프트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("덤벨 루마니안 데드리프트", ExerciseCategory.DUMBBELL, BodyPart.LEG),
        exercise("힙 쓰러스트", ExerciseCategory.BARBELL, BodyPart.LEG),
        exercise("글루트 브릿지", ExerciseCategory.BODYWEIGHT, BodyPart.LEG, ExerciseTrackingType.REPS_ONLY),
        exercise("케이블 풀스루", ExerciseCategory.CABLE, BodyPart.LEG),
        exercise("시티드 레그 컬", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("힙 어브덕션 머신", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("힙 어덕션 머신", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("시티드 카프 레이즈", ExerciseCategory.MACHINE, BodyPart.LEG),
        exercise("맨몸 스쿼트", ExerciseCategory.BODYWEIGHT, BodyPart.LEG, ExerciseTrackingType.REPS_ONLY),
        exercise("점프 스쿼트", ExerciseCategory.BODYWEIGHT, BodyPart.LEG, ExerciseTrackingType.REPS_ONLY),
        exercise("박스 점프", ExerciseCategory.BODYWEIGHT, BodyPart.LEG, ExerciseTrackingType.REPS_ONLY),
        exercise("월싯", ExerciseCategory.BODYWEIGHT, BodyPart.LEG, ExerciseTrackingType.TIME),
        // 코어
        exercise("사이드 플랭크", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.TIME),
        exercise("할로우 홀드", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.TIME),
        exercise("싯업", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("레그레이즈", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("행잉 니레이즈", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("토즈투바", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("바이시클 크런치", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("데드버그", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("마운틴 클라이머", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("러시안 트위스트", ExerciseCategory.BODYWEIGHT, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("앱 롤아웃", ExerciseCategory.OTHER, BodyPart.CORE, ExerciseTrackingType.REPS_ONLY),
        exercise("머신 크런치", ExerciseCategory.MACHINE, BodyPart.CORE),
        exercise("케이블 우드찹", ExerciseCategory.CABLE, BodyPart.CORE),
        exercise("팔로프 프레스", ExerciseCategory.CABLE, BodyPart.CORE),
        // 전신
        exercise("파워 클린", ExerciseCategory.BARBELL, BodyPart.FULL_BODY),
        exercise("스러스터", ExerciseCategory.BARBELL, BodyPart.FULL_BODY),
        exercise("케틀벨 스내치", ExerciseCategory.KETTLEBELL, BodyPart.FULL_BODY),
        exercise("케틀벨 클린 앤 프레스", ExerciseCategory.KETTLEBELL, BodyPart.FULL_BODY),
        exercise("터키시 겟업", ExerciseCategory.KETTLEBELL, BodyPart.FULL_BODY),
        exercise("월볼", ExerciseCategory.OTHER, BodyPart.FULL_BODY, ExerciseTrackingType.REPS_ONLY),
        exercise("배틀로프", ExerciseCategory.OTHER, BodyPart.FULL_BODY, ExerciseTrackingType.TIME),
        // 유산소(시간만 기록)
        exercise("러닝머신", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
        exercise("실내 자전거", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
        exercise("일립티컬", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
        exercise("로잉머신", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
        exercise("스텝밀", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
        exercise("에어바이크", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
        exercise("줄넘기", ExerciseCategory.CARDIO, BodyPart.CARDIO, ExerciseTrackingType.TIME),
    )
}
