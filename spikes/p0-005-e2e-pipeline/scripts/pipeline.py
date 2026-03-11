#!/usr/bin/env python3
"""
P0-005: End-to-End Pipeline Smoke Test

Wires together:
  - all-MiniLM-L6-v2 embedding model (P0-002)
  - FAISS Flat retrieval from a SQLite .pack file (P0-004)
  - Qwen2.5-1.5B-Instruct GGUF via llama-cpp-python (P0-001)

Pipeline (strictly sequential — models do NOT run simultaneously per ADR 0007):

  Phase A — Retrieval
    1. Load embedding model
    2. Embed query
    3. Load FAISS index from .pack file
    4. Retrieve top-k chunks
    5. DEL embedding model + gc.collect()   ← RAM released before LLM loads

  Phase B — Prompt construction
    6. Build grounded prompt from Jinja2 template + retrieved chunks

  Phase C — Generation
    7. Load Qwen2.5-1.5B-Instruct
    8. Generate explanation
    9. DEL LLM + gc.collect()

Usage:
  python scripts/pipeline.py
  python scripts/pipeline.py --config ../config.yaml
  python scripts/pipeline.py --question "How do I add fractions?"
"""

import argparse
import gc
import json
import os
import sqlite3
import sys
import tempfile
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from textwrap import indent

import faiss
import numpy as np
import psutil
import yaml
from jinja2 import Environment, FileSystemLoader
from llama_cpp import Llama
from sentence_transformers import SentenceTransformer


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class RetrievedChunk:
    chunk_id: str
    title: str
    content: str
    topic_path: str
    score: float


@dataclass
class PhaseMetrics:
    name: str
    duration_ms: float
    ram_before_mb: float
    ram_after_mb: float

    @property
    def ram_delta_mb(self) -> float:
        return self.ram_after_mb - self.ram_before_mb


@dataclass
class PipelineResult:
    question_id: str
    question: str
    retrieved_chunks: list[RetrievedChunk] = field(default_factory=list)
    prompt: str = ""
    answer: str = ""
    prompt_tokens: int = 0
    completion_tokens: int = 0
    phases: list[PhaseMetrics] = field(default_factory=list)
    peak_ram_mb: float = 0.0
    total_ms: float = 0.0
    passed: bool = False
    failure_reason: str = ""


# ---------------------------------------------------------------------------
# Utilities
# ---------------------------------------------------------------------------

def get_ram_mb() -> float:
    return psutil.Process(os.getpid()).memory_info().rss / (1024 * 1024)


def load_config(config_path: Path) -> dict:
    with open(config_path) as f:
        return yaml.safe_load(f)


def resolve_path(base: Path, rel: str) -> Path:
    """Resolve a path relative to the config file's directory."""
    p = Path(rel)
    if not p.is_absolute():
        p = (base / rel).resolve()
    return p


# ---------------------------------------------------------------------------
# Phase A: Retrieval
# ---------------------------------------------------------------------------

def load_faiss_from_pack(pack_path: Path) -> tuple[faiss.Index, list[str]]:
    """Load the FAISS index and chunk_order from the SQLite .pack file."""
    con = sqlite3.connect(str(pack_path))
    con.row_factory = sqlite3.Row
    row = con.execute(
        "SELECT index_data, chunk_order FROM faiss_indexes ORDER BY id LIMIT 1"
    ).fetchone()
    if row is None:
        raise RuntimeError(f"No FAISS index found in {pack_path}")
    chunk_order = json.loads(row["chunk_order"])
    with tempfile.NamedTemporaryFile(suffix=".faiss", delete=False) as tmp:
        tmp.write(row["index_data"])
        tmp_path = tmp.name
    try:
        index = faiss.read_index(tmp_path)
    finally:
        os.unlink(tmp_path)
    con.close()
    return index, chunk_order


def fetch_chunks_by_ids(pack_path: Path, chunk_ids: list[str]) -> dict[str, dict]:
    """Fetch chunk records from the SQLite .pack file by ID."""
    placeholders = ",".join("?" * len(chunk_ids))
    con = sqlite3.connect(str(pack_path))
    con.row_factory = sqlite3.Row
    rows = con.execute(
        f"SELECT chunk_id, title, content, topic_path FROM chunks WHERE chunk_id IN ({placeholders})",
        chunk_ids,
    ).fetchall()
    con.close()
    return {r["chunk_id"]: dict(r) for r in rows}


def run_retrieval(
    question: str,
    pack_path: Path,
    embedding_model_path: str,
    top_k: int,
) -> tuple[list[RetrievedChunk], list[PhaseMetrics]]:
    """Phase A: embed question, search FAISS, return top-k chunks.
    Embedding model is loaded and deleted within this function."""

    phases: list[PhaseMetrics] = []

    # --- Load embedding model ---
    ram_before = get_ram_mb()
    t0 = time.perf_counter()
    embed_model = SentenceTransformer(embedding_model_path, device="cpu")
    phases.append(PhaseMetrics(
        name="embed_model_load",
        duration_ms=(time.perf_counter() - t0) * 1000,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    ))

    # --- Embed query ---
    ram_before = get_ram_mb()
    t0 = time.perf_counter()
    q_vec = embed_model.encode(
        [question],
        convert_to_numpy=True,
        normalize_embeddings=True,
        show_progress_bar=False,
    ).astype(np.float32)
    phases.append(PhaseMetrics(
        name="embed_query",
        duration_ms=(time.perf_counter() - t0) * 1000,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    ))

    # --- Load FAISS index ---
    ram_before = get_ram_mb()
    t0 = time.perf_counter()
    index, chunk_order = load_faiss_from_pack(pack_path)
    phases.append(PhaseMetrics(
        name="faiss_load",
        duration_ms=(time.perf_counter() - t0) * 1000,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    ))

    # --- Search ---
    ram_before = get_ram_mb()
    t0 = time.perf_counter()
    faiss.normalize_L2(q_vec)
    distances, indices = index.search(q_vec, top_k)
    retrieved_ids = [chunk_order[i] for i in indices[0] if i >= 0]
    phases.append(PhaseMetrics(
        name="faiss_search",
        duration_ms=(time.perf_counter() - t0) * 1000,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    ))

    # --- Fetch chunk content ---
    chunk_records = fetch_chunks_by_ids(pack_path, retrieved_ids)
    retrieved = [
        RetrievedChunk(
            chunk_id=cid,
            title=chunk_records[cid]["title"],
            content=chunk_records[cid]["content"],
            topic_path=chunk_records[cid]["topic_path"],
            score=float(distances[0][i]),
        )
        for i, cid in enumerate(retrieved_ids)
        if cid in chunk_records
    ]

    # --- Unload embedding model (ADR 0007: must not run simultaneously with LLM) ---
    ram_before = get_ram_mb()
    del embed_model
    del index
    gc.collect()
    phases.append(PhaseMetrics(
        name="embed_model_unload",
        duration_ms=0,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    ))

    return retrieved, phases


# ---------------------------------------------------------------------------
# Phase B: Prompt construction
# ---------------------------------------------------------------------------

def build_prompt(
    question: str,
    chunks: list[RetrievedChunk],
    template_path: Path,
) -> str:
    env = Environment(
        loader=FileSystemLoader(str(template_path.parent)),
        autoescape=False,
    )
    tmpl = env.get_template(template_path.name)
    return tmpl.render(question=question, chunks=chunks)


# ---------------------------------------------------------------------------
# Phase C: Generation
# ---------------------------------------------------------------------------

def run_generation(
    prompt: str,
    llm_model_path: str,
    cfg: dict,
) -> tuple[str, int, int, PhaseMetrics, PhaseMetrics]:
    """Phase C: load LLM, generate, unload. Returns (answer, prompt_tokens, completion_tokens, load_metrics, gen_metrics)."""

    llm_cfg = cfg["llm"]

    # --- Load LLM ---
    ram_before = get_ram_mb()
    t0 = time.perf_counter()
    llm = Llama(
        model_path=llm_model_path,
        n_ctx=llm_cfg["n_ctx"],
        n_threads=llm_cfg["n_threads"],
        verbose=False,
    )
    load_metrics = PhaseMetrics(
        name="llm_load",
        duration_ms=(time.perf_counter() - t0) * 1000,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    )

    # --- Generate ---
    ram_before = get_ram_mb()
    t0 = time.perf_counter()
    response = llm.create_completion(
        prompt=prompt,
        max_tokens=llm_cfg["max_tokens"],
        temperature=llm_cfg["temperature"],
        top_p=llm_cfg["top_p"],
        repeat_penalty=llm_cfg["repeat_penalty"],
        seed=llm_cfg["seed"],
    )
    gen_metrics = PhaseMetrics(
        name="llm_generate",
        duration_ms=(time.perf_counter() - t0) * 1000,
        ram_before_mb=ram_before,
        ram_after_mb=get_ram_mb(),
    )

    answer = response["choices"][0]["text"].strip()
    prompt_tokens = response.get("usage", {}).get("prompt_tokens", 0)
    completion_tokens = response.get("usage", {}).get("completion_tokens", 0)

    # --- Unload LLM ---
    del llm
    gc.collect()

    return answer, prompt_tokens, completion_tokens, load_metrics, gen_metrics


# ---------------------------------------------------------------------------
# Full pipeline (one question)
# ---------------------------------------------------------------------------

def run_pipeline(
    question_id: str,
    question: str,
    cfg: dict,
    config_dir: Path,
) -> PipelineResult:
    result = PipelineResult(question_id=question_id, question=question)
    total_start = time.perf_counter()

    pack_path = resolve_path(config_dir, cfg["paths"]["content_pack"])
    embedding_model_path = str(resolve_path(config_dir, cfg["paths"]["embedding_model"]))
    llm_model_path = str(resolve_path(config_dir, cfg["paths"]["llm_model"]))
    template_path = resolve_path(config_dir, cfg["paths"]["prompt_template"])
    top_k = cfg["retrieval"]["top_k"]

    # Phase A
    retrieved, retrieval_phases = run_retrieval(question, pack_path, embedding_model_path, top_k)
    result.retrieved_chunks = retrieved
    result.phases.extend(retrieval_phases)

    # Phase B
    result.prompt = build_prompt(question, retrieved, template_path)

    # Phase C
    answer, pt, ct, load_m, gen_m = run_generation(result.prompt, llm_model_path, cfg)
    result.answer = answer
    result.prompt_tokens = pt
    result.completion_tokens = ct
    result.phases.extend([load_m, gen_m])

    result.total_ms = (time.perf_counter() - total_start) * 1000
    result.peak_ram_mb = max(p.ram_after_mb for p in result.phases)

    # Validate acceptance criteria
    ac = cfg["acceptance_criteria"]
    total_s = result.total_ms / 1000
    embed_ms = next((p.duration_ms for p in result.phases if p.name == "embed_query"), 0)
    search_ms = next((p.duration_ms for p in result.phases if p.name == "faiss_search"), 0)

    if total_s > ac["total_latency_s"]:
        result.failure_reason = f"Total latency {total_s:.1f}s > {ac['total_latency_s']}s limit"
    elif embed_ms > ac["embed_time_ms"]:
        result.failure_reason = f"Embed time {embed_ms:.0f}ms > {ac['embed_time_ms']:.0f}ms limit"
    elif search_ms > ac["search_time_ms"]:
        result.failure_reason = f"Search time {search_ms:.1f}ms > {ac['search_time_ms']:.0f}ms limit"
    else:
        result.passed = True

    return result


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

def print_result(r: PipelineResult, latency_target_s: float = 15.0) -> None:
    status = "✓ PASS" if r.passed else f"✗ FAIL — {r.failure_reason}"
    print(f"\n{'=' * 72}")
    print(f"[{r.question_id}] {r.question}")
    print(f"Status:          {status}")
    print(f"Total latency:   {r.total_ms / 1000:.2f}s  (target: <{latency_target_s:.0f}s)")
    print(f"Peak RAM:        {r.peak_ram_mb:.0f} MB")
    print(f"\nPhase breakdown:")
    for p in r.phases:
        if p.name == "embed_model_unload":
            print(f"  {'embed_model_unload':<22}  RAM freed: {-p.ram_delta_mb:.0f} MB  → {p.ram_after_mb:.0f} MB")
        else:
            print(f"  {p.name:<22}  {p.duration_ms:>7.1f}ms  RAM delta: {p.ram_delta_mb:+.0f} MB  → {p.ram_after_mb:.0f} MB")
    print(f"\nRetrieved chunks (top-{len(r.retrieved_chunks)}):")
    for c in r.retrieved_chunks:
        print(f"  [{c.score:.4f}] {c.chunk_id} — {c.title}")
    print(f"\nGenerated answer ({r.completion_tokens} tokens):")
    print(indent(r.answer, "  "))


def write_report(results: list[PipelineResult], cfg: dict, config_dir: Path) -> Path:
    report_path = resolve_path(config_dir, cfg["paths"]["report_output"])
    report_path.parent.mkdir(parents=True, exist_ok=True)

    latency_target_s = cfg["acceptance_criteria"]["total_latency_s"]
    passed = sum(1 for r in results if r.passed)
    total = len(results)
    avg_total_s = sum(r.total_ms for r in results) / total / 1000

    lines = [
        "# P0-005 Smoke Test Report",
        "",
        f"**Date:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}",
        f"**Pack:** `{cfg['paths']['content_pack']}`",
        f"**LLM:** `{Path(cfg['paths']['llm_model']).name}`",
        f"**Embedding model:** `{Path(cfg['paths']['embedding_model']).name}`",
        "",
        "## Summary",
        "",
        f"| Metric | Result | Target | Status |",
        f"|--------|--------|--------|--------|",
        f"| Questions run | {total} | 5 | {'✓' if total >= 5 else '✗'} |",
        f"| Passed | {passed}/{total} | {total}/{total} | {'✓' if passed == total else '✗'} |",
        f"| Avg total latency | {avg_total_s:.2f}s | <{latency_target_s:.0f}s | {'✓' if avg_total_s < latency_target_s else '✗'} |",
        "",
        "## Acceptance Criteria",
        "",
        "| Criterion | Target | Result |",
        "|-----------|--------|--------|",
    ]

    for r in results:
        embed_ms = next((p.duration_ms for p in r.phases if p.name == "embed_query"), 0)
        search_ms = next((p.duration_ms for p in r.phases if p.name == "faiss_search"), 0)
        gen_ms = next((p.duration_ms for p in r.phases if p.name == "llm_generate"), 0)
        total_s = r.total_ms / 1000
        lines += [
            f"| [{r.question_id}] Pipeline (no crash) | ✓ | {'✓' if r.answer else '✗'} |",
            f"| [{r.question_id}] Total latency | <{latency_target_s:.0f}s | {total_s:.2f}s {'✓' if total_s < latency_target_s else '✗'} |",
            f"| [{r.question_id}] Embed time | <500ms | {embed_ms:.1f}ms {'✓' if embed_ms < 500 else '✗'} |",
            f"| [{r.question_id}] Search time | <500ms | {search_ms:.3f}ms {'✓' if search_ms < 500 else '✗'} |",
            f"| [{r.question_id}] Retrieved content | grounded | {r.retrieved_chunks[0].title if r.retrieved_chunks else '—'} |",
        ]

    lines += ["", "## Per-Question Results", ""]

    for r in results:
        status = "✓ PASS" if r.passed else f"✗ FAIL — {r.failure_reason}"
        lines += [
            f"### {r.question_id}: {r.question}",
            "",
            f"**Status:** {status}  ",
            f"**Total latency:** {r.total_ms / 1000:.2f}s  ",
            f"**Peak RAM:** {r.peak_ram_mb:.0f} MB  ",
            "",
            "**Retrieved chunks:**",
        ]
        for c in r.retrieved_chunks:
            lines.append(f"- [{c.score:.4f}] `{c.chunk_id}` — {c.title}")
        lines += [
            "",
            "**Phase breakdown:**",
            "",
            "| Phase | Duration | RAM after |",
            "|-------|----------|-----------|",
        ]
        for p in r.phases:
            if p.name == "embed_model_unload":
                lines.append(f"| {p.name} | RAM freed: {-p.ram_delta_mb:.0f} MB | {p.ram_after_mb:.0f} MB |")
            else:
                lines.append(f"| {p.name} | {p.duration_ms:.1f}ms | {p.ram_after_mb:.0f} MB |")
        lines += [
            "",
            "**Generated explanation:**",
            "",
            "```",
            r.answer,
            "```",
            "",
        ]

    report_path.write_text("\n".join(lines), encoding="utf-8")
    return report_path


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="P0-005 end-to-end pipeline smoke test.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--config",
        default=None,
        help="Path to config.yaml (default: ../config.yaml relative to this script)",
    )
    parser.add_argument(
        "--question",
        default=None,
        help="Run a single ad-hoc question instead of the configured test set",
    )
    args = parser.parse_args()

    # Resolve config
    script_dir = Path(__file__).parent
    config_path = Path(args.config) if args.config else script_dir.parent / "config.yaml"
    if not config_path.exists():
        print(f"ERROR: config not found: {config_path}", file=sys.stderr)
        sys.exit(1)
    cfg = load_config(config_path)
    config_dir = config_path.parent

    # Validate required paths
    for key in ("llm_model", "embedding_model", "content_pack"):
        p = resolve_path(config_dir, cfg["paths"][key])
        if not p.exists():
            print(f"ERROR: {key} not found: {p}", file=sys.stderr)
            sys.exit(1)

    # Build question list
    if args.question:
        questions = [{"id": "adhoc", "question": args.question}]
    else:
        questions = [
            {"id": q["id"], "question": q["question"]}
            for q in cfg.get("test_questions", [])
        ]

    if not questions:
        print("ERROR: no test questions configured.", file=sys.stderr)
        sys.exit(1)

    print(f"\n{'=' * 72}")
    print("P0-005 End-to-End Pipeline Smoke Test")
    print(f"{'=' * 72}")
    print(f"LLM:             {Path(cfg['paths']['llm_model']).name}")
    print(f"Embedding model: {Path(cfg['paths']['embedding_model']).name}")
    print(f"Content pack:    {Path(cfg['paths']['content_pack']).name}")
    print(f"Questions:       {len(questions)}")
    print(f"{'=' * 72}")

    results: list[PipelineResult] = []
    for q in questions:
        print(f"\n>>> Running [{q['id']}]: {q['question'][:60]}...")
        r = run_pipeline(q["id"], q["question"], cfg, config_dir)
        print_result(r, cfg["acceptance_criteria"]["total_latency_s"])
        results.append(r)

    # Summary
    passed = sum(1 for r in results if r.passed)
    print(f"\n{'=' * 72}")
    print(f"RESULTS: {passed}/{len(results)} passed")

    # Write report
    report_path = write_report(results, cfg, config_dir)
    print(f"Report written: {report_path}")

    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
