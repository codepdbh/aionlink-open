plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "org.aionlink.open"; compileSdk = 35
    defaultConfig {
        // Optional isolated lab package lets encoder tests run without replacing the owner's installed app.
        applicationId = if (project.hasProperty("aionlinkLab")) "org.aionlink.open.lab" else "org.aionlink.open"
        manifestPlaceholders["applicationLabel"] = if (project.hasProperty("aionlinkLab")) "AionLink Sonda" else "AionLink Open"
        minSdk = 29; targetSdk = 35
        versionCode = 32; versionName = "0.32.0-research"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core")); implementation(project(":protocol-api")); implementation(project(":protocol-iccoa"))
    implementation(project(":transport-bluetooth")); implementation(project(":vehicle-profiles"))
    implementation(project(":transport-usb"))
    implementation(project(":transport-wifi"))
    implementation(project(":projection"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
