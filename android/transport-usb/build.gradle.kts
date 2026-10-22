plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "org.aionlink.usb"; compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":protocol-iccoa"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
