pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiteRT-LM on-device inference library (Google AI Edge)
        maven { url = uri("https://storage.googleapis.com/download.tensorflow.org/maven2") }
    }
}

rootProject.name = "PersonalAI"
include(":app")
include(":localllm")
