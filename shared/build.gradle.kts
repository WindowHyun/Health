import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 폰 앱과 시계 앱이 주고받는 메시지의 모양. 안드로이드에 기대지 않는 순수 코틀린이라 JVM 에서 바로 테스트한다.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
