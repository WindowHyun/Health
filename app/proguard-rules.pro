# Room generated implementations are looked up reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * { @androidx.room.* <methods>; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin coroutines debug metadata
-dontwarn kotlinx.coroutines.**

# osmdroid 는 consumer ProGuard 규칙을 제공하지 않는다.
# 타일 제공자 · 설정 제공자 · 아카이브 리더를 리플렉션으로 만드는 경로가 있어
# 통째로 남긴다. APK 가 조금 커지지만, 지도를 못 그리는 것보다 낫다.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# osmdroid 가 참조하는 선택적 의존성(사용하지 않음)
-dontwarn org.slf4j.**
