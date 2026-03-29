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

- App runs with mock AI implementations by default.
- Real GGUF/ONNX model files are not required for this UI/flow pass.

## 6) Useful Debug Commands

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

## 7) Common Sideload Issues

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

## 8) Collaboration Reporting Template

When filing a test report, include:

1. Branch and commit SHA
2. Device model and Android version
3. Install method used (USB ADB, Wi-Fi ADB, manual)
4. Which checklist items passed or failed
5. Crash log excerpt or screenshot for failures

## 9) Ownership and Updates

Update this runbook in the same PR whenever sideload package name, install flow, or build output paths change.

Related docs:

- docs/development/emulator-testing-runbook.md
- README.md
- apps/learner-mobile/README.md
