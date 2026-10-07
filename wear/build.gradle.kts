import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * 시계 앱. 폰 앱과 **같은 앱 ID, 같은 서명 키, 같은 버전**이어야 Wearable Data Layer 가 한 짝으로 본다.
 * 서명 정보를 읽는 방법은 폰 앱(app/build.gradle.kts)과 같다: keystore.properties → 환경 변수 → (없으면) 서명 안 함.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, envName: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envName)

val releaseStoreFile: File? = signingValue("storeFile", "HEALTH_STORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.windowhyun.health.wear"
    compileSdk = 35

    defaultConfig {
        // 폰 앱과 같아야 한다(VersionMatchTest 가 확인한다).
        applicationId = "com.windowhyun.health"
        minSdk = 30
        targetSdk = 34
        versionCode = 23
        versionName = "0.10.0"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "HEALTH_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "HEALTH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "HEALTH_KEY_PASSWORD")
            }
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            // 폰 앱의 디버그 빌드(.debug)와 짝을 이룬다.
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // 작은 앱이라 줄이기(R8)를 켜지 않는다. 직렬화 · Play 서비스 규칙을 따로 챙기는 위험이 이득보다 크다.
            isMinifyEnabled = false
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
