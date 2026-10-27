import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 서버 주소: local.properties의 server.url(디버그) / server.url.release(릴리스)로 바꿀 수 있다.
// 기본값: 디버그는 에뮬레이터에서 PC로 가는 주소(10.0.2.2). 실기기는 server.url에 PC의 IP를 적는다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun serverUrl(key: String, default: String): String =
    "\"" + localProps.getProperty(key, default).trimEnd('/') + "/\""

android {
    namespace = "com.example.livescore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.livescore"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", serverUrl("server.url", "http://10.0.2.2:8080"))
        }
        release {
            buildConfigField("String", "BASE_URL", serverUrl("server.url.release", "https://api.sscore.example"))
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}