pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "family-tv-home"
include(":notes", ":weather", ":where", ":ticker")
project(":notes").projectDir = file("notes/app")
project(":weather").projectDir = file("weather/app")
project(":where").projectDir = file("where/app")
project(":ticker").projectDir = file("ticker/app")
