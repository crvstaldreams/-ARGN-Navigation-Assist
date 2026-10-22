plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "ua.argn.navigation"
 compileSdk = 35
 defaultConfig {
  applicationId = "ua.argn.navigation"
  minSdk = 29 // Android 10 (API 29)
  targetSdk = 35
  versionCode = 4
  versionName = "0.4"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
