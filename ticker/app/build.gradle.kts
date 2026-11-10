plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
extra["familyApp"] = "ticker"
android {
    namespace = "app.familytv.ticker"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.familytv.ticker"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "4.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").java.srcDir(rootProject.file("shared/src"))
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
apply(from = rootProject.file("shared/family-config.gradle.kts"))
