"""
Gemma 4 deterministic embedding — hash-based fallback.

Produces 768-dim L2-normalized vectors from text content using SHA-256 seeded
pseudo-random generation. This is a placeholder until the MediaPipe GenAI SDK
exposes a real embedding extraction API for Gemma 4.

Cross-platform parity: The Android GemmaEmbeddingModel uses the same algorithm
(SHA-256 seed → java.util.Random → normalize). See EMBEDDING_CONTRACT.md.

NOTE: The current Android implementation (GemmaEmbeddingModel.kt) uses
text.hashCode() as seed and java.util.Random (LCG) as the PRNG, which differs
from this module's SHA-256 + Mersenne Twister approach. The EMBEDDING_CONTRACT.md
defines THIS implementation as canonical. The Android side will be updated in a
future task to match. During the hash-based fallback period, retrieval is
non-semantic regardless of PRNG choice, so the mismatch does not affect
functionality — only integration testing consistency.

Algorithm (see EMBEDDING_CONTRACT.md §5.1):
    1. SHA-256(UTF-8(text)) → 64-char hex digest
    2. First 16 hex chars → parse as unsigned 64-bit integer
    3. Seed Mersenne Twister (random.Random) with this integer
    4. Generate `dim` floats uniformly in [-1.0, +1.0)
    5. L2-normalize so ‖v‖₂ = 1

Schema version: 2
Model identifier: gemma4-1b
Implementation version: gemma4-1b-hash-v1
Default dimension: 768
"""

import hashlib
import random as _random

import numpy as np

# ── Constants ────────────────────────────────────────────────────────

EMBEDDING_MODEL_ID = "gemma4-1b"
EMBEDDING_MODEL_VERSION = "gemma4-1b-hash-v1"
EMBEDDING_DIM = 768
SCHEMA_VERSION = 2


# ── Public API ───────────────────────────────────────────────────────


def embed_text(text: str, dim: int = EMBEDDING_DIM) -> np.ndarray:
    """Embed text into a deterministic L2-normalized vector.

    Uses SHA-256 of the text as a seed for the Mersenne Twister PRNG,
    generates ``dim`` floats in [-1, 1], and L2-normalizes the result.

    This is a hash-based placeholder — vectors have no semantic meaning.
    See EMBEDDING_CONTRACT.md §5 for the full specification.

    Args:
        text: Input text to embed (UTF-8 encoded internally).
        dim: Output vector dimension. Default 768 (Gemma 4 native).
             Supported values: 256, 384, 512, 768.

    Returns:
        A 1-D numpy array of shape (dim,), dtype float32, L2-normalized.

    Raises:
        ValueError: If ``dim`` is not a positive integer.

    Examples:
        >>> v = embed_text("What is a fraction?")
        >>> v.shape
        (768,)
        >>> abs(float(np.linalg.norm(v)) - 1.0) < 1e-6
        True
    """
    if dim <= 0:
        raise ValueError(f"dim must be positive, got {dim}")

    seed = _sha256_seed(text)
    rng = _random.Random(seed)
    raw = np.array(
        [rng.uniform(-1.0, 1.0) for _ in range(dim)],
        dtype=np.float32,
    )
    return _l2_normalize(raw)


def embed_batch(texts: list[str], dim: int = EMBEDDING_DIM) -> np.ndarray:
    """Embed multiple texts, returning shape (N, dim).

    Convenience wrapper around :func:`embed_text` for batch processing.
    Each text is embedded independently — order does not affect results.

    Args:
        texts: List of input texts to embed.
        dim: Output vector dimension per text. Default 768.

    Returns:
        A 2-D numpy array of shape (len(texts), dim), dtype float32.
        Each row is L2-normalized.

    Raises:
        ValueError: If ``texts`` is empty or ``dim`` is not positive.

    Examples:
        >>> vecs = embed_batch(["hello", "world"])
        >>> vecs.shape
        (2, 768)
    """
    if not texts:
        raise ValueError("texts must be a non-empty list")

    return np.stack([embed_text(t, dim) for t in texts])


# ── Internal helpers ─────────────────────────────────────────────────


def _sha256_seed(text: str) -> int:
    """Compute the canonical PRNG seed from text.

    Algorithm (EMBEDDING_CONTRACT.md §5.2, steps 1-4):
        1. SHA-256 hash of UTF-8 encoded text → 32-byte digest
        2. Hex-encode → 64-character string
        3. Take first 16 hex characters (= 8 bytes = 64 bits)
        4. Parse as unsigned 64-bit big-endian integer

    Args:
        text: The input text.

    Returns:
        An unsigned 64-bit integer suitable for seeding a PRNG.
    """
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return int(digest[:16], 16)


def _l2_normalize(vector: np.ndarray) -> np.ndarray:
    """L2-normalize a vector so ‖v‖₂ = 1.

    After normalization, dot product equals cosine similarity —
    compatible with FAISS IndexFlatIP.

    Args:
        vector: A 1-D numpy array.

    Returns:
        The L2-normalized vector. If the input is a zero vector,
        returns the input unchanged.
    """
    norm = np.linalg.norm(vector)
    if norm > 0:
        vector = vector / norm
    return vector


# ── Self-test ────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("Gemma 4 Deterministic Embedding — Self-Test")
    print(f"  Model:   {EMBEDDING_MODEL_ID}")
    print(f"  Version: {EMBEDDING_MODEL_VERSION}")
    print(f"  Dim:     {EMBEDDING_DIM}")
    print()

    # Test 1: Basic embedding
    v = embed_text("What is a fraction?")
    norm = float(np.linalg.norm(v))
    print(f"  embed_text('What is a fraction?'):")
    print(f"    shape={v.shape}, dtype={v.dtype}, norm={norm:.6f}")
    assert v.shape == (768,), f"Expected (768,), got {v.shape}"
    assert abs(norm - 1.0) < 1e-5, f"Expected norm ≈ 1.0, got {norm}"
    print("    ✓ shape and norm correct")

    # Test 2: Determinism
    v2 = embed_text("What is a fraction?")
    assert np.array_equal(v, v2), "Embeddings should be identical for same text"
    print("    ✓ deterministic (same input → same output)")

    # Test 3: Different texts → different vectors
    v3 = embed_text("Explain multiplication")
    assert not np.array_equal(v, v3), "Different texts should produce different vectors"
    print("    ✓ different texts → different vectors")

    # Test 4: Custom dimension
    v384 = embed_text("hello", dim=384)
    assert v384.shape == (384,), f"Expected (384,), got {v384.shape}"
    norm384 = float(np.linalg.norm(v384))
    assert abs(norm384 - 1.0) < 1e-5, f"Expected norm ≈ 1.0, got {norm384}"
    print(f"    ✓ dim=384 works (norm={norm384:.6f})")

    # Test 5: Batch embedding
    batch = embed_batch(["hello", "world", "fraction"])
    assert batch.shape == (3, 768), f"Expected (3, 768), got {batch.shape}"
    print(f"    ✓ embed_batch: shape={batch.shape}")

    # Test 6: Seed reproducibility
    seed1 = _sha256_seed("test")
    seed2 = _sha256_seed("test")
    assert seed1 == seed2, "SHA-256 seed should be deterministic"
    seed3 = _sha256_seed("other")
    assert seed1 != seed3, "Different texts should produce different seeds"
    print(f"    ✓ SHA-256 seeding is deterministic and distinct")

    # Test 7: No NaN or Inf
    for val in v:
        assert not np.isnan(val), "Vector contains NaN"
        assert not np.isinf(val), "Vector contains Inf"
    print("    ✓ no NaN or Inf values")

    print()
    print("All self-tests passed ✓")
