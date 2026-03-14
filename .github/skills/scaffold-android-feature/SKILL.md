---
name: scaffold-android-feature
description: >
  Scaffold a new Android feature module for eZansiEdgeAI with standard structure:
  Activity/Fragment or Composable, ViewModel, Repository interface, DI setup, and test stubs.
  Use this skill each time a new feature screen is added (chat, topics, profiles, preferences, library).
---

# Scaffold Android Feature Module

Creates a new feature module under `apps/learner-mobile/` following the eZansiEdgeAI 4-layer architecture and project conventions.

## When to Use

Use this skill whenever you need to create a new feature module for the learner mobile app. Each major screen area gets its own feature module:
- `:feature:chat` — Chat interface
- `:feature:topics` — Topic browser
- `:feature:profiles` — Learner profile selection
- `:feature:preferences` — Preference engine UI
- `:feature:library` — Content library management

## Inputs

Before running this skill, you need:

1. **Feature name** (lowercase, hyphenated) — e.g., `chat`, `topics`, `profiles`
2. **Package name suffix** — e.g., `chat`, `topics`, `profiles`
3. **Primary screen type** — Fragment (View system) or Composable (Compose)
4. **Dependencies** — Which core modules it depends on (`:core:ai`, `:core:data`, `:core:common`)

## Output Structure

For a feature named `{name}` with package `com.ezansi.app.feature.{name}`:

```
apps/learner-mobile/feature/{name}/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── kotlin/com/ezansi/app/feature/{name}/
    │       ├── {Name}Screen.kt          # UI (Composable or Fragment)
    │       ├── {Name}ViewModel.kt       # Presentation logic
    │       └── navigation/
    │           └── {Name}Navigation.kt  # Navigation graph contribution
    └── test/
        └── kotlin/com/ezansi/app/feature/{name}/
            └── {Name}ViewModelTest.kt   # ViewModel unit test stub
```

## build.gradle.kts Template

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ezansi.app.feature.{name}"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:common"))
    // Add feature-specific core dependencies:
    // implementation(project(":core:ai"))
    // implementation(project(":core:data"))

    // Testing
    testImplementation(libs.junit5)
    testImplementation(libs.kotlin.test)
}
```

## ViewModel Template

```kotlin
package com.ezansi.app.feature.{name}

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class {Name}UiState(
    val isLoading: Boolean = false,
    // Add feature-specific state fields
)

class {Name}ViewModel : ViewModel() {

    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()

    // Add feature-specific methods
}
```

## Conventions

- Feature modules depend on `:core:*` modules only — never on other `:feature:*` modules
- ViewModels expose `StateFlow<UiState>` — no LiveData
- UI communicates with core layer via repository interfaces injected into ViewModel
- All user-facing strings go in `res/values/strings.xml` for localisation
- All colours reference the app theme — no hardcoded values
- Touch targets ≥ 48×48 dp (accessibility requirement ACC-02)
- Grade 4 reading level for all UI text (ACC-07)

## Post-Scaffold Checklist

After scaffolding, verify:
- [ ] Module is added to `settings.gradle.kts`
- [ ] Module builds successfully (`./gradlew :feature:{name}:assembleDebug`)
- [ ] Navigation route is registered in the app's navigation graph
- [ ] ViewModel test stub compiles and runs
