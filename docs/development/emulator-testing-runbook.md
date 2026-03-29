# Emulator Testing Runbook (Learner Mobile)

This guide is for collaborators who need to build, run, and sanity-test the learner app on an Android emulator.

## Scope

- Project: eZansiEdgeAI learner mobile app
- App path: apps/learner-mobile
- OS covered: Linux host
- Goal: get a working debug install in emulator and validate core Phase 1 flows quickly

## 1) Prerequisites

Install and verify:

1. Android Studio (Ladybug or newer)
2. Android SDK with:
   - Android Emulator
   - Android SDK Platform-Tools
   - Android SDK Build-Tools
   - Android API level 29+ system image (x86_64)
3. JDK 17
4. Virtualization support:
   - BIOS virtualization enabled (Intel VT-x or AMD-V)
   - KVM usable on host (recommended for performance)

Optional checks:

- android-studio available in launcher or PATH
- adb version prints correctly

## 2) Open and Sync Project

1. Open Android Studio.
2. Select project folder: apps/learner-mobile
3. Wait for Gradle sync to finish.
4. Confirm no blocking SDK/JDK prompts remain.

## 3) Create Emulator (AVD)

1. Open Device Manager.
2. Create a new Virtual Device.
3. Recommended profile: Pixel 4 or similar mid-range profile.
4. Select API 29+ x86_64 system image.
5. Finish and start the emulator.
6. Wait for full boot before install.

Notes:

- Prefer x86_64 images for host performance.
- Keep emulator RAM modest to reflect low/mid-tier target devices.

## 4) Build and Install (Terminal-First)

From repository root:

1. cd apps/learner-mobile
2. ./gradlew assembleDebug
3. ./gradlew installDebug

If install succeeds, app should appear in emulator launcher.

## 5) Build and Run (Android Studio)

1. Select the app run configuration.
2. Select your running emulator.
3. Click Run.

Use this flow when you want IDE logs, debugger, and breakpoints.

## 6) 5-Minute Sanity Checklist

After launch, verify these behaviors:

1. App launches without crash.
2. Onboarding appears and can be dismissed.
3. Chat screen renders and accepts input.
4. Topic browser opens and navigation works.
5. Profiles screen opens and profile operations are responsive.
6. Preferences screen opens and saving options persists in-session.
7. Library screen opens and handles empty/placeholder states gracefully.
8. Returning to app from background does not immediately crash.

Expected behavior in current setup:

- Mock AI implementations are used by default.
- You can test UI and flow without GGUF/ONNX model files.

## 7) Load a Content Pack into the Emulator

Use this when you want the Library and Topics screens to show real curriculum content.

Recommended test pack:

- content-packs/maths-grade6-caps-all-terms-v1.0.pack

From repository root:

1. Push the pack to emulator shared storage:
   - adb push content-packs/maths-grade6-caps-all-terms-v1.0.pack /sdcard/Download/
2. Copy it into the app's private pack directory:
   - adb shell run-as com.ezansi.learner mkdir -p files/content-packs
   - adb shell run-as com.ezansi.learner cp /sdcard/Download/maths-grade6-caps-all-terms-v1.0.pack files/content-packs/
3. Verify it is present:
   - adb shell run-as com.ezansi.learner ls -lh files/content-packs
4. Force-stop and relaunch the app:
   - adb shell am force-stop com.ezansi.learner
   - adb shell monkey -p com.ezansi.learner -c android.intent.category.LAUNCHER 1

Expected result:

1. Library shows the installed pack.
2. Topics shows non-empty curriculum content.
3. Chat still uses mock generation on this branch unless native AI integrations are switched on.

Fallback if `cp` fails in `run-as` shell:

- adb shell "run-as com.ezansi.learner sh -c 'cat /sdcard/Download/maths-grade6-caps-all-terms-v1.0.pack > files/content-packs/maths-grade6-caps-all-terms-v1.0.pack'"

## 8) Stage GGUF and ONNX Model Files in the Emulator

Use this when you want the emulator storage layout ready for real on-device AI testing.

Expected filenames in the app code:

1. GGUF LLM: `qwen2.5-1.5b-instruct-q4_k_m.gguf`
2. ONNX embedding model: `all-MiniLM-L6-v2.onnx`

Current limitation on this branch:

1. The app still wires `MockLlmEngine` and `MockEmbeddingModel` by default.
2. Staging the files is useful for storage/path verification, but it does not yet enable real inference by itself.

Model download guidance:

- models/phone-models/README.md

ADB staging commands:

1. Create the model directory:
   - adb shell run-as com.ezansi.learner mkdir -p files/models
2. Push the GGUF file to shared storage:
   - adb push models/phone-models/qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Download/
3. Copy the GGUF file into app storage:
   - adb shell run-as com.ezansi.learner cp /sdcard/Download/qwen2.5-1.5b-instruct-q4_k_m.gguf files/models/
4. Push the ONNX file to shared storage once exported/downloaded as a single `.onnx` file:
   - adb push <path-to>/all-MiniLM-L6-v2.onnx /sdcard/Download/
5. Copy the ONNX file into app storage:
   - adb shell run-as com.ezansi.learner cp /sdcard/Download/all-MiniLM-L6-v2.onnx files/models/
6. Verify both files are present:
   - adb shell run-as com.ezansi.learner ls -lh files/models

## 9) Optional Test Commands

From apps/learner-mobile:

1. Unit tests: ./gradlew test
2. Instrumented tests (if configured for modules): ./gradlew connectedDebugAndroidTest

## 10) Common Issues and Fixes

### Emulator is very slow

- Ensure KVM acceleration is available.
- Close heavy host apps.
- Use x86_64 image, not ARM image.

### Device not found during install

- Start emulator and wait until lock screen/home appears.
- Run adb devices and confirm at least one emulator is listed.
- Retry ./gradlew installDebug.

### SDK/JDK mismatch errors

- Ensure project uses JDK 17 in Android Studio settings.
- Re-sync Gradle after changing JDK.

### Build succeeds but app not visible

- Check install output for package replacement errors.
- Uninstall old package from emulator and reinstall:
  - adb uninstall ai.ezansi.edge
  - ./gradlew installDebug

### Content pack copied but not visible in the app

- Confirm the file exists in `run-as` app storage, not only in `/sdcard/Download`.
- Relaunch the app after copying the file.
- Check exact filename ends with `.pack`.

### GGUF or ONNX files are present but chat still uses placeholder AI

- This is expected on the current branch.
- The app still uses `MockLlmEngine` and `MockEmbeddingModel` in `AppContainer` until native integrations are enabled.

## 11) Suggested Collaboration Workflow

1. Pull latest branch.
2. Run quick build/install.
3. Run 5-minute sanity checklist.
4. Capture failures with:
   - Screenshot
   - Logcat snippet
   - Repro steps
   - Device/API level
   - Whether `.pack`, GGUF, and ONNX files were staged
5. Open issue or PR comment with findings.

## 12) Ownership and Updates

When app startup, package name, or module structure changes, update this runbook in the same PR.

Current source references:

- Root project README
- apps/learner-mobile README
- models/phone-models README

Related docs:

- docs/development/sideload-testing-runbook.md
