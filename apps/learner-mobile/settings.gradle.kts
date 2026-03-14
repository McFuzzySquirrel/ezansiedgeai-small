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
    }
}

rootProject.name = "eZansiEdgeAI"

include(":app")

// Core modules
include(":core:common")
include(":core:ai")
include(":core:data")

// Feature modules
include(":feature:chat")
include(":feature:topics")
include(":feature:profiles")
include(":feature:preferences")
include(":feature:library")
