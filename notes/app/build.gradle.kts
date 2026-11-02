plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
extra["familyApp"] = "notes"
android {
    namespace = "app.familytv.notes"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.familytv.notes"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").java.srcDir(rootProject.file("shared/src"))
}
apply(from = rootProject.file("shared/family-config.gradle.kts"))
