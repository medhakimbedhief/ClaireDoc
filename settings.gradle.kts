pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required for localagents-fc:0.1.0 and litertlm-android:0.1.0
        maven { url = uri("https://storage.googleapis.com/ai-edge-maven-release") }
    }
}
rootProject.name = "ClaireDoc"
include(":app")
