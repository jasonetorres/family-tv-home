plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
extra["familyApp"] = "weather"
android {
    namespace = "app.familytv.weather"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.familytv.weather"
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("previewDir", rootProject.file("docs/screenshots").absolutePath) }
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
apply(from = rootProject.file("shared/family-config.gradle.kts"))
