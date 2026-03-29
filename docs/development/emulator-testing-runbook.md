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

## 7) Optional Test Commands

From apps/learner-mobile:

1. Unit tests: ./gradlew test
2. Instrumented tests (if configured for modules): ./gradlew connectedDebugAndroidTest

## 8) Common Issues and Fixes

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

## 9) Suggested Collaboration Workflow

1. Pull latest branch.
2. Run quick build/install.
3. Run 5-minute sanity checklist.
4. Capture failures with:
   - Screenshot
   - Logcat snippet
   - Repro steps
   - Device/API level
5. Open issue or PR comment with findings.

## 10) Ownership and Updates

When app startup, package name, or module structure changes, update this runbook in the same PR.

Current source references:

- Root project README
- apps/learner-mobile README

Related docs:

- docs/development/sideload-testing-runbook.md
