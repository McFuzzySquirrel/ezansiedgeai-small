#!/usr/bin/env python3
"""Download candidate embedding models from HuggingFace for P0-002 benchmarking.

Downloads sentence-transformers models for local embedding + retrieval testing.

Usage:
    python scripts/download_models.py --all
    python scripts/download_models.py --model all-minilm-l6-v2
    python scripts/download_models.py --model all-minilm-l6-v2 --model gte-small
    python scripts/download_models.py --list
"""

import argparse
import shutil
import sys
from pathlib import Path

import yaml
from sentence_transformers import SentenceTransformer


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
    models_dir = get_models_dir()
    print("\nAvailable embedding model candidates:")
    print("-" * 70)
    for model in config["models"]:
        model_path = models_dir / model["id"]
        status = "✓ downloaded" if model_path.exists() else "  not downloaded"
        print(f"  [{status}] {model['id']}")
        print(f"    Name:       {model['name']}")
        print(f"    Repo:       {model['repo']}")
        print(f"    Size:       ~{model['size_estimate_mb']} MB")
        print(f"    Dimensions: {model['dimensions']}")
        print(f"    Max seq:    {model['max_seq_length']}")
        print(f"    Notes:      {model['notes']}")
        print()


def get_model_size_mb(model_dir: Path) -> float:
    """Calculate total size of a model directory in MB."""
    total = sum(f.stat().st_size for f in model_dir.rglob("*") if f.is_file())
    return total / (1024 * 1024)


def download_model(model: dict, models_dir: Path) -> Path:
    """Download a single embedding model from HuggingFace.

    Returns the path to the downloaded model directory.
    """
    target_path = models_dir / model["id"]

    if target_path.exists():
        size_mb = get_model_size_mb(target_path)
        print(f"  ✓ Already downloaded: {model['id']} ({size_mb:.1f} MB)")
        return target_path

    print(f"  ↓ Downloading {model['name']}...")
    print(f"    From: {model['repo']}")
    print(f"    Estimated size: ~{model['size_estimate_mb']} MB")
    print()

    # Download using sentence-transformers (handles model files automatically)
    st_model = SentenceTransformer(model["repo"])
    st_model.save(str(target_path))

    actual_size_mb = get_model_size_mb(target_path)
    print(f"  ✓ Downloaded: {model['id']} ({actual_size_mb:.1f} MB)")
    return target_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Download candidate embedding models for P0-002 spike."
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
    parser.add_argument(
        "--clean",
        action="store_true",
        help="Remove all downloaded models.",
    )
    args = parser.parse_args()

    config = load_config()
    models_dir = get_models_dir()
    model_map = {m["id"]: m for m in config["models"]}

    if args.list:
        list_models(config)
        return

    if args.clean:
        if models_dir.exists():
            shutil.rmtree(models_dir)
            print(f"Cleaned: {models_dir}/")
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
    print(f"\nModels dir: {models_dir}/")
    print("Next step:  python scripts/benchmark.py --all")


if __name__ == "__main__":
    main()
