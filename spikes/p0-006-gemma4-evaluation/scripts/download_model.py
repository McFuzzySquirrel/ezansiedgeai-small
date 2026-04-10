#!/usr/bin/env python3
"""Download Gemma 4 1B INT4 model for P0-006 spike evaluation.

Downloads the LiteRT-format model to the local models/ directory.
Supports resumable downloads and integrity verification.
"""

import argparse
import hashlib
import os
import sys
from pathlib import Path

import yaml


def load_config() -> dict:
    config_path = Path(__file__).parent.parent / "config.yaml"
    with open(config_path) as f:
        return yaml.safe_load(f)


def download_from_huggingface(repo_id: str, filename: str, dest_dir: Path) -> Path:
    """Download model file from HuggingFace Hub."""
    from huggingface_hub import hf_hub_download

    print(f"Downloading {filename} from {repo_id}...")
    path = hf_hub_download(
        repo_id=repo_id,
        filename=filename,
        local_dir=str(dest_dir),
        local_dir_use_symlinks=False,
    )
    return Path(path)


def verify_file(path: Path, expected_size_mb: int) -> bool:
    """Basic verification: file exists and size is reasonable."""
    if not path.exists():
        print(f"ERROR: File not found: {path}")
        return False

    size_mb = path.stat().st_size / (1024 * 1024)
    print(f"Downloaded: {path.name} ({size_mb:.1f} MB)")

    if size_mb < expected_size_mb * 0.5:
        print(f"WARNING: File seems too small (expected ~{expected_size_mb} MB)")
        return False

    return True


def main():
    parser = argparse.ArgumentParser(description="Download Gemma 4 model for spike evaluation")
    parser.add_argument("--repo", help="HuggingFace repo ID (overrides config)")
    parser.add_argument("--filename", help="Model filename (overrides config)")
    parser.add_argument("--dest", default="models", help="Destination directory")
    args = parser.parse_args()

    config = load_config()
    model_cfg = config["model"]

    repo_id = args.repo or "google/gemma-4-1b-it-litert"
    filename = args.filename or model_cfg["filename"]
    dest_dir = Path(__file__).parent.parent / args.dest
    dest_dir.mkdir(parents=True, exist_ok=True)

    dest_file = dest_dir / filename
    if dest_file.exists():
        print(f"Model already exists at {dest_file}")
        if verify_file(dest_file, model_cfg["size_estimate_mb"]):
            print("Skipping download.")
            return
        print("File appears corrupted, re-downloading...")

    try:
        downloaded = download_from_huggingface(repo_id, filename, dest_dir)
        if verify_file(downloaded, model_cfg["size_estimate_mb"]):
            print(f"\nModel ready at: {downloaded}")
            print("Run benchmark_generation.py next.")
        else:
            print("WARNING: Download may be incomplete.", file=sys.stderr)
            sys.exit(1)
    except Exception as e:
        print(f"Download failed: {e}", file=sys.stderr)
        print("\nManual download instructions:")
        print(f"  1. Visit https://huggingface.co/{repo_id}")
        print(f"  2. Download {filename}")
        print(f"  3. Place it in {dest_dir}/")
        sys.exit(1)


if __name__ == "__main__":
    main()
