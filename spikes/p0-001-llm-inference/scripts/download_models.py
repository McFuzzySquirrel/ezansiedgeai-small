#!/usr/bin/env python3
"""Download candidate GGUF models from HuggingFace for P0-001 benchmarking.

Usage:
    python scripts/download_models.py --all
    python scripts/download_models.py --model qwen2.5-1.5b
    python scripts/download_models.py --model qwen2.5-1.5b --model smollm2-1.7b
    python scripts/download_models.py --list
"""

import argparse
import sys
from pathlib import Path

import yaml
from huggingface_hub import hf_hub_download


def load_config() -> dict:
    """Load model configuration from config.yaml."""
    config_path = Path(__file__).parent.parent / "config.yaml"
    if not config_path.exists():
        print(f"ERROR: Config file not found: {config_path}", file=sys.stderr)
        sys.exit(1)
    with open(config_path) as f:
        return yaml.safe_load(f)


def get_models_dir() -> Path:
    """Get the models directory, creating it if needed."""
    models_dir = Path(__file__).parent.parent / "models"
    models_dir.mkdir(exist_ok=True)
    return models_dir


def list_models(config: dict) -> None:
    """Print available model candidates."""
    print("\nAvailable model candidates:")
    print("-" * 70)
    for model in config["models"]:
        status = "✓ downloaded" if (get_models_dir() / model["filename"]).exists() else "  not downloaded"
        print(f"  [{status}] {model['id']}")
        print(f"    Name:     {model['name']}")
        print(f"    Repo:     {model['repo']}")
        print(f"    File:     {model['filename']}")
        print(f"    Size:     ~{model['size_estimate_mb']} MB")
        print(f"    Notes:    {model['notes']}")
        print()


def download_model(model: dict, models_dir: Path) -> Path:
    """Download a single model from HuggingFace.

    Returns the path to the downloaded file.
    """
    target_path = models_dir / model["filename"]

    if target_path.exists():
        print(f"  ✓ Already downloaded: {model['filename']}")
        return target_path

    print(f"  ↓ Downloading {model['name']}...")
    print(f"    From: {model['repo']}")
    print(f"    File: {model['filename']}")
    print(f"    Estimated size: ~{model['size_estimate_mb']} MB")
    print()

    downloaded_path = hf_hub_download(
        repo_id=model["repo"],
        filename=model["filename"],
        local_dir=models_dir,
        local_dir_use_symlinks=False,
    )

    actual_size_mb = Path(downloaded_path).stat().st_size / (1024 * 1024)
    print(f"  ✓ Downloaded: {model['filename']} ({actual_size_mb:.1f} MB)")
    return Path(downloaded_path)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Download candidate GGUF models for P0-001 inference spike."
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Download all candidate models.",
    )
    parser.add_argument(
        "--model",
        action="append",
        dest="models",
        metavar="MODEL_ID",
        help="Download a specific model by ID (can be repeated).",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List available models and their download status.",
    )
    args = parser.parse_args()

    config = load_config()
    models_dir = get_models_dir()
    model_map = {m["id"]: m for m in config["models"]}

    if args.list:
        list_models(config)
        return

    if not args.all and not args.models:
        parser.print_help()
        print("\nHint: Use --list to see available models, --all to download all.")
        return

    # Determine which models to download
    if args.all:
        targets = config["models"]
    else:
        targets = []
        for model_id in args.models:
            if model_id not in model_map:
                print(
                    f"ERROR: Unknown model ID '{model_id}'. "
                    f"Available: {', '.join(model_map.keys())}",
                    file=sys.stderr,
                )
                sys.exit(1)
            targets.append(model_map[model_id])

    print(f"\nDownloading {len(targets)} model(s) to: {models_dir}/")
    print("=" * 60)

    downloaded = []
    failed = []
    for model in targets:
        try:
            path = download_model(model, models_dir)
            downloaded.append((model["id"], path))
        except Exception as e:
            print(f"  ✗ FAILED: {model['id']} — {e}", file=sys.stderr)
            failed.append((model["id"], str(e)))
        print()

    # Summary
    print("=" * 60)
    print(f"Downloaded: {len(downloaded)}/{len(targets)}")
    if failed:
        print(f"Failed:     {len(failed)}")
        for model_id, error in failed:
            print(f"  ✗ {model_id}: {error}")
        sys.exit(1)
    else:
        print("All models ready for benchmarking.")
        print(f"\nNext step: python scripts/benchmark.py --all")


if __name__ == "__main__":
    main()
