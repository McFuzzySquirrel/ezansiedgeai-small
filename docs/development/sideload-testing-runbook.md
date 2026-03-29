# Sideload Testing Runbook (Learner Mobile)

This guide is for collaborators who need to test the learner app by sideloading APKs onto a real Android device.

## Scope

- Project: eZansiEdgeAI learner mobile app
- App path: apps/learner-mobile
- Host OS covered: Linux
- Goal: install a debug APK via ADB or manual transfer and run a fast sanity pass

## 1) Prerequisites

Install and verify:

1. Android SDK Platform-Tools (adb)
2. JDK 17
3. Android device running Android 10+
4. USB cable for first-time setup

On the phone:

1. Enable Developer options
2. Enable USB debugging
3. Allow installation from unknown sources for the installer you use

Host check:

1. adb version
2. adb devices

If the device is not shown on Linux, accept the USB debugging prompt on the phone and re-run adb devices.

## 2) Build Debug APK

From repository root:

1. cd apps/learner-mobile
2. ./gradlew assembleDebug

Primary output path:

- app/build/outputs/apk/debug/

## 3) Install with ADB (Recommended)

### USB install

1. Connect phone via USB
2. Confirm device appears in adb devices
3. Install:
   - ./gradlew installDebug

Alternative direct install:

- adb install -r app/build/outputs/apk/debug/ezansi-v0.1.0-debug.apk

Use -r to replace an existing install with the same signing key.

### Wireless ADB (optional)

After at least one trusted USB pairing:

1. adb tcpip 5555
2. Find phone IP on the same Wi-Fi network
3. adb connect <phone-ip>:5555
4. adb devices
5. ./gradlew installDebug

## 4) Manual Sideload (No ADB)

Use this when ADB is unavailable:

1. Copy debug APK to phone storage (USB file transfer, nearby share, cloud drive)
2. Open Files app on device and tap APK
3. Allow install from unknown sources when prompted
4. Complete installation and launch app

## 5) 5-Minute On-Device Sanity Checklist

After launch, verify:

1. App starts without crash.
2. Onboarding appears and can be dismissed.
3. Chat accepts input and renders response placeholder.
4. Topic browser navigation works.
5. Profiles and Preferences screens are reachable.
6. Library screen handles empty state without failure.
7. App survives background/foreground cycle.

Current expected behavior:

- App attempts real runtime paths by default (`OnnxEmbeddingModel` and `LlamaCppEngine`).
- If ONNX setup fails, embedding uses deterministic fallback.
- If llama native bindings are unavailable, generation returns an explicit runtime-unavailable response.

## 6) Load a Content Pack onto the Device

Recommended test pack:

- content-packs/maths-grade6-caps-all-terms-v1.0.pack

### ADB-based install into app storage

1. Push to shared device storage:
   - adb push content-packs/maths-grade6-caps-all-terms-v1.0.pack /sdcard/Download/
2. Copy into the app pack directory:
   - adb shell run-as com.ezansi.learner mkdir -p files/content-packs
   - adb shell run-as com.ezansi.learner cp /sdcard/Download/maths-grade6-caps-all-terms-v1.0.pack files/content-packs/
3. Verify the copy:
   - adb shell run-as com.ezansi.learner ls -lh files/content-packs
4. Relaunch the app.

Expected result:

1. Library shows the installed pack.
2. Topics shows real Grade 6 content.
3. Chat runtime status indicates ONNX real/fallback state and llama native availability.

### Manual file-transfer fallback

If `run-as` copy is unavailable, keep the `.pack` on the device and use ADB later to move it into app-private storage. The current app UI does not yet expose an in-app pack picker/import action.

## 7) Stage GGUF and ONNX Model Files on the Device

Use this to prepare a device for future real-model testing.

Expected filenames from the app code:

1. `qwen2.5-1.5b-instruct-q4_k_m.gguf`
2. `all-MiniLM-L6-v2.onnx`

Current limitation:

1. `LlamaCppEngine` is wired by default, but native llama bindings are not yet integrated.
2. `OnnxEmbeddingModel` is wired by default; if ONNX cannot initialise, embedding falls back deterministically.
3. Staging these files prepares storage and runtime paths, and is required for future native-inference readiness.

Model source guidance:

- models/phone-models/README.md

ADB staging flow:

1. Create the app model directory:
   - adb shell run-as com.ezansi.learner mkdir -p files/models
2. Push GGUF to shared storage:
   - adb push models/phone-models/qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Download/
3. Copy GGUF into app storage:
   - adb shell run-as com.ezansi.learner cp /sdcard/Download/qwen2.5-1.5b-instruct-q4_k_m.gguf files/models/
4. Push ONNX file to shared storage once available as a single `.onnx` file:
   - adb push <path-to>/all-MiniLM-L6-v2.onnx /sdcard/Download/
5. Copy ONNX file into app storage:
   - adb shell run-as com.ezansi.learner cp /sdcard/Download/all-MiniLM-L6-v2.onnx files/models/
6. Verify files:
   - adb shell run-as com.ezansi.learner ls -lh files/models

## 8) Useful Debug Commands

From apps/learner-mobile:

1. Show connected devices:
   - adb devices
2. View logs for app process:
   - adb logcat | grep -i ezansi
3. Reinstall cleanly:
   - adb uninstall ai.ezansi.edge
   - ./gradlew installDebug
4. Run unit tests before sideload:
   - ./gradlew test
5. List installed app-private content packs:
   - adb shell run-as com.ezansi.learner ls -lh files/content-packs
6. List staged model files:
   - adb shell run-as com.ezansi.learner ls -lh files/models

## 9) Common Sideload Issues

### INSTALL_FAILED_VERSION_DOWNGRADE

A newer build is already installed.

Fix:

1. adb uninstall ai.ezansi.edge
2. Reinstall debug APK

### INSTALL_FAILED_UPDATE_INCOMPATIBLE

Existing app was signed with a different key.

Fix:

1. Uninstall existing app
2. Install current debug APK

### Device unauthorized in adb devices

Fix:

1. Reconnect USB
2. Re-enable USB debugging
3. Accept RSA prompt on phone
4. Run adb devices again

### Parse error when tapping APK manually

Fix:

1. Re-copy APK and verify transfer completed
2. Confirm APK targets supported Android version
3. Rebuild with ./gradlew assembleDebug

### Content pack does not appear after copy

Fix:

1. Verify it was copied into `files/content-packs` under `run-as`
2. Relaunch the app
3. Confirm the filename still ends with `.pack`

### GGUF or ONNX files are staged but runtime still reports native-unavailable

Fix:

1. No file-path fix is needed if files are correctly staged.
2. Llama native integration is still pending; check `ExplanationEngine` runtime status logs to confirm current mode.

## 10) Collaboration Reporting Template

When filing a test report, include:

1. Branch and commit SHA
2. Device model and Android version
3. Install method used (USB ADB, Wi-Fi ADB, manual)
4. Which checklist items passed or failed
5. Crash log excerpt or screenshot for failures
6. Whether `.pack`, GGUF, and ONNX files were staged

## 11) Ownership and Updates

Update this runbook in the same PR whenever sideload package name, install flow, or build output paths change.

Related docs:

- docs/development/emulator-testing-runbook.md
- models/phone-models/README.md
- README.md
- apps/learner-mobile/README.md
