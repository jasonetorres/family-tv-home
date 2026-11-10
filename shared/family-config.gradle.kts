val kind = project.extra["familyApp"] as String
val syncFamilyConfig = tasks.register("syncFamilyConfig") {
    doLast {
        project.exec {
            commandLine("python3", rootProject.file("scripts/sync_config.py").absolutePath, kind, project.projectDir.absolutePath)
        }
    }
}
project.afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(syncFamilyConfig) }
}
