#!/usr/bin/env python3
"""Platform audit for MediaPipe GenAI SDK impact.

Checks APK size impact, permissions, network calls, and GMS dependencies
from the MediaPipe SDK integration. Reports against P0-006 acceptance criteria.

Usage:
    python scripts/platform_audit.py
    python scripts/platform_audit.py --apk-path path/to/app.apk
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

import yaml


def load_config() -> dict:
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def check_apk_size(apk_path: Path | None) -> dict:
    """Check APK size with and without MediaPipe SDK."""
    result = {
        "apk_path": str(apk_path) if apk_path else None,
        "apk_size_mb": None,
        "mediapipe_size_estimate_mb": None,
        "notes": [],
    }

    if apk_path and apk_path.exists():
        size_mb = apk_path.stat().st_size / (1024 * 1024)
        result["apk_size_mb"] = round(size_mb, 1)
        result["notes"].append(f"APK size: {size_mb:.1f} MB")
    else:
        result["notes"].append("APK not found — build with ./gradlew assembleDebug first")
        result["notes"].append("Compare APK size before/after MediaPipe SDK addition")

    return result


def check_permissions(project_root: Path) -> dict:
    """Check AndroidManifest for internet/network permissions."""
    result = {
        "internet_permission": False,
        "network_state_permission": False,
        "gms_required": False,
        "manifests_checked": [],
    }

    for manifest in project_root.rglob("AndroidManifest.xml"):
        if "build" in str(manifest) or ".gradle" in str(manifest):
            continue
        result["manifests_checked"].append(str(manifest.relative_to(project_root)))

        content = manifest.read_text()
        if "android.permission.INTERNET" in content:
            result["internet_permission"] = True
        if "android.permission.ACCESS_NETWORK_STATE" in content:
            result["network_state_permission"] = True
        if "com.google.android.gms" in content:
            result["gms_required"] = True

    return result


def check_gradle_dependencies(project_root: Path) -> dict:
    """Check Gradle dependencies for GMS/Play Services references."""
    result = {
        "gms_dependencies": [],
        "play_services_dependencies": [],
        "mediapipe_version": None,
        "files_checked": [],
    }

    for gradle_file in project_root.rglob("build.gradle.kts"):
        if "build" in str(gradle_file.relative_to(project_root)).split("/")[0]:
            continue
        content = gradle_file.read_text()
        rel_path = str(gradle_file.relative_to(project_root))
        result["files_checked"].append(rel_path)

        for line in content.split("\n"):
            line = line.strip()
            if "gms" in line.lower() and "implementation" in line.lower():
                result["gms_dependencies"].append({"file": rel_path, "line": line})
            if "play-services" in line.lower() and "implementation" in line.lower():
                result["play_services_dependencies"].append({"file": rel_path, "line": line})

    # Check version catalog
    toml_path = project_root / "gradle" / "libs.versions.toml"
    if toml_path.exists():
        content = toml_path.read_text()
        for line in content.split("\n"):
            if "mediapipe" in line.lower():
                result["mediapipe_version"] = line.strip()

    return result


def run_audit(config: dict, apk_path: Path | None = None) -> dict:
    """Run full platform audit."""
    project_root = Path(__file__).parent.parent.parent.parent / "apps" / "learner-mobile"

    print("=" * 60)
    print("Platform Audit — P0-006")
    print("=" * 60)

    results = {
        "apk_size": check_apk_size(apk_path),
        "permissions": check_permissions(project_root),
        "dependencies": check_gradle_dependencies(project_root),
        "acceptance": {},
    }

    # Evaluate acceptance
    platform_accept = config["platform_acceptance"]
    results["acceptance"] = {
        "gms_free": not results["permissions"]["gms_required"]
                    and len(results["dependencies"]["gms_dependencies"]) == 0
                    and len(results["dependencies"]["play_services_dependencies"]) == 0,
        "no_internet_permission": not results["permissions"]["internet_permission"],
    }

    # Print summary
    print(f"\nAPK Size:")
    for note in results["apk_size"]["notes"]:
        print(f"  {note}")

    print(f"\nPermissions:")
    print(f"  INTERNET: {'⚠ YES' if results['permissions']['internet_permission'] else '✓ NO'}")
    print(f"  NETWORK_STATE: {'⚠ YES' if results['permissions']['network_state_permission'] else '✓ NO'}")
    print(f"  GMS required: {'⚠ YES' if results['permissions']['gms_required'] else '✓ NO'}")

    print(f"\nDependencies:")
    print(f"  GMS deps: {len(results['dependencies']['gms_dependencies'])}")
    print(f"  Play Services deps: {len(results['dependencies']['play_services_dependencies'])}")
    print(f"  MediaPipe version: {results['dependencies']['mediapipe_version']}")

    print(f"\nAcceptance:")
    print(f"  GMS-free: {'PASS' if results['acceptance']['gms_free'] else 'FAIL'}")

    return results


def main():
    parser = argparse.ArgumentParser(description="Platform audit for MediaPipe SDK")
    parser.add_argument("--apk-path", type=Path, help="Path to debug APK for size check")
    parser.add_argument("--output", default="results/platform-audit.json")
    args = parser.parse_args()

    config = load_config()
    results = run_audit(config, apk_path=args.apk_path)

    output_path = Path(__file__).parent.parent / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(results, f, indent=2)
    print(f"\nResults saved to: {output_path}")


if __name__ == "__main__":
    main()
