plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "org.aionlink.open"; compileSdk = 35
    defaultConfig { applicationId = "org.aionlink.open"; minSdk = 29; targetSdk = 35; versionCode = 4; versionName = "0.4.0-research" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core")); implementation(project(":protocol-api")); implementation(project(":protocol-iccoa"))
    implementation(project(":transport-bluetooth")); implementation(project(":vehicle-profiles"))
    implementation(project(":transport-usb"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
