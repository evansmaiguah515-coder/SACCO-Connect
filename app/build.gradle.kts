plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sacco.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sacco.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"

        // ---- M-Pesa backend settings (edit these) ----
        // BACKEND_URL: your ngrok https URL WITH a trailing slash, e.g. "https://abcd-12-34.ngrok-free.app/"
        buildConfigField("String", "BACKEND_URL", "\"https://lifter-juggle-issuing.ngrok-free.dev/\"")        // true  = payments are simulated inside the app (no server, no real M-Pesa call)
        // false = payments go to your backend, which calls the Safaricom Daraja sandbox
        buildConfigField("boolean", "MPESA_SIMULATED", "false")


        5    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
