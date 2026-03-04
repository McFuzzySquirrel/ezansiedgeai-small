"""Tests for the EJS ADR Database tool (scripts/adr-db.py)."""

from __future__ import annotations

import json
import sqlite3
import textwrap
import unittest
from pathlib import Path

# Allow importing from the scripts directory
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from importlib import import_module

adr_db = import_module("adr-db")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

SAMPLE_ADR = textwrap.dedent("""\
    ---
    ejs:
      type: journey-adr
      version: 1.1
      adr_id: 0042
      title: Use SQLite for ADR Tracking
      date: 2026-03-02
      status: accepted
      session_id: ejs-session-2026-03-02-01
      session_journey: ejs-docs/journey/2026/ejs-session-2026-03-02-01.md

    actors:
      humans:
        - id: alice
          role: lead-engineer
      agents:
        - id: copilot
          role: coding-agent

    context:
      repo: my-repo
      branch: main
    ---

    # Context

    Agents need a fast way to look up past decisions.

    ---

    # Session Intent

    Evaluate SQLite as an ADR index.

    # Decision

    Adopt SQLite for ADR tracking and agent reference.

    ---

    # Rationale

    SQLite is lightweight, zero-config, and available everywhere.

    ---

    # Consequences

    ### Positive
    - Fast lookup
    - No external dependencies

    ### Negative / Trade-offs
    - Extra sync step needed

    ---

    # Key Learnings

    Agents benefit from structured search over raw file reads.

    ---

    # Agent Guidance

    Prefer database queries over scanning all ADR files.
""")

TEMPLATE_ADR = textwrap.dedent("""\
    ---
    ejs:
      type: journey-adr
      version: 1.1
      adr_id: XXXX
      title: <Short, descriptive title>
      date: YYYY-MM-DD
      status: proposed | accepted | deprecated
    ---

    # Context

    Placeholder template.
""")


def _write_adr(tmp: Path, filename: str, content: str) -> Path:
    fp = tmp / filename
    fp.write_text(content, encoding="utf-8")
    return fp


class _TempDirMixin:
    """Mixin providing a temporary directory and in-memory database."""

    def setUp(self) -> None:
        import tempfile

        self._tmpdir_obj = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmpdir_obj.name)
        self.adr_dir = self.tmp / "adr"
        self.adr_dir.mkdir()
        self.db_path = self.tmp / "test.db"
        self.conn = adr_db.init_db(self.db_path)

    def tearDown(self) -> None:
        self.conn.close()
        self._tmpdir_obj.cleanup()


# ---------------------------------------------------------------------------
# Test cases
# ---------------------------------------------------------------------------


class TestParseFrontmatter(unittest.TestCase):
    def test_valid_frontmatter(self) -> None:
        fm = adr_db._parse_frontmatter(SAMPLE_ADR)
        # PyYAML (YAML 1.1) interprets 0042 as octal → 34.
        # parse_adr_file works around this by extracting adr_id from raw text.
        self.assertEqual(fm["ejs"]["adr_id"], 34)
        self.assertEqual(fm["ejs"]["status"], "accepted")
        self.assertEqual(fm["actors"]["humans"][0]["id"], "alice")

    def test_no_frontmatter(self) -> None:
        self.assertEqual(adr_db._parse_frontmatter("# Just a heading\n"), {})

    def test_template_skipped(self) -> None:
        fm = adr_db._parse_frontmatter(TEMPLATE_ADR)
        self.assertEqual(fm["ejs"]["adr_id"], "XXXX")


class TestExtractSection(unittest.TestCase):
    def test_decision_section(self) -> None:
        section = adr_db._extract_section(SAMPLE_ADR, "Decision")
        self.assertIn("Adopt SQLite", section)

    def test_context_section(self) -> None:
        section = adr_db._extract_section(SAMPLE_ADR, "Context")
        self.assertIn("fast way to look up", section)

    def test_missing_section(self) -> None:
        self.assertEqual(adr_db._extract_section(SAMPLE_ADR, "Nonexistent"), "")


class TestParseAdrFile(_TempDirMixin, unittest.TestCase):
    def test_valid_file(self) -> None:
        fp = _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        # Temporarily patch _repo_root for relative path calculation
        original = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]
        try:
            record = adr_db.parse_adr_file(fp)
        finally:
            adr_db._repo_root = original
        self.assertIsNotNone(record)
        self.assertEqual(record["adr_id"], "0042")
        self.assertEqual(record["title"], "Use SQLite for ADR Tracking")
        self.assertEqual(record["status"], "accepted")
        self.assertEqual(record["context_repo"], "my-repo")
        self.assertIn("Adopt SQLite", record["decision"])

    def test_template_returns_none(self) -> None:
        fp = _write_adr(self.adr_dir, "0000-template.md", TEMPLATE_ADR)
        original = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]
        try:
            self.assertIsNone(adr_db.parse_adr_file(fp))
        finally:
            adr_db._repo_root = original

    def test_no_frontmatter_returns_none(self) -> None:
        fp = _write_adr(self.adr_dir, "bad.md", "# No frontmatter\n")
        original = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]
        try:
            self.assertIsNone(adr_db.parse_adr_file(fp))
        finally:
            adr_db._repo_root = original


class TestDatabaseSchema(_TempDirMixin, unittest.TestCase):
    def test_tables_created(self) -> None:
        tables = {
            row[0]
            for row in self.conn.execute(
                "SELECT name FROM sqlite_master WHERE type IN ('table', 'trigger')"
            )
        }
        self.assertIn("adrs", tables)
        self.assertIn("adrs_fts", tables)
        self.assertIn("adrs_ai", tables)
        self.assertIn("adrs_ad", tables)
        self.assertIn("adrs_au", tables)
        self.assertIn("journeys", tables)
        self.assertIn("journeys_fts", tables)
        self.assertIn("journeys_ai", tables)
        self.assertIn("journeys_ad", tables)
        self.assertIn("journeys_au", tables)


class TestSync(_TempDirMixin, unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        self._orig_root = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]

    def tearDown(self) -> None:
        adr_db._repo_root = self._orig_root
        super().tearDown()

    def test_sync_inserts_records(self) -> None:
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        rc = adr_db.cmd_sync(self.conn, self.adr_dir)
        self.assertEqual(rc, 0)
        rows = self.conn.execute("SELECT * FROM adrs").fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["adr_id"], "0042")

    def test_sync_skips_template(self) -> None:
        _write_adr(self.adr_dir, "0000-template.md", TEMPLATE_ADR)
        adr_db.cmd_sync(self.conn, self.adr_dir)
        rows = self.conn.execute("SELECT * FROM adrs").fetchall()
        self.assertEqual(len(rows), 0)

    def test_sync_upserts_on_change(self) -> None:
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        adr_db.cmd_sync(self.conn, self.adr_dir)

        updated = SAMPLE_ADR.replace("accepted", "deprecated")
        _write_adr(self.adr_dir, "0042-test.md", updated)
        adr_db.cmd_sync(self.conn, self.adr_dir)

        row = self.conn.execute("SELECT status FROM adrs WHERE adr_id = '0042'").fetchone()
        self.assertEqual(row["status"], "deprecated")

    def test_sync_removes_stale(self) -> None:
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        adr_db.cmd_sync(self.conn, self.adr_dir)
        self.assertEqual(self.conn.execute("SELECT count(*) FROM adrs").fetchone()[0], 1)

        # Remove the file and re-sync
        (self.adr_dir / "0042-test.md").unlink()
        adr_db.cmd_sync(self.conn, self.adr_dir)
        self.assertEqual(self.conn.execute("SELECT count(*) FROM adrs").fetchone()[0], 0)

    def test_sync_missing_dir(self) -> None:
        rc = adr_db.cmd_sync(self.conn, self.tmp / "nonexistent")
        self.assertEqual(rc, 1)


class TestFullTextSearch(_TempDirMixin, unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        self._orig_root = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        adr_db.cmd_sync(self.conn, self.adr_dir)

    def tearDown(self) -> None:
        adr_db._repo_root = self._orig_root
        super().tearDown()

    def test_search_finds_match(self) -> None:
        rows = self.conn.execute(
            "SELECT adr_id FROM adrs_fts WHERE adrs_fts MATCH 'SQLite'"
        ).fetchall()
        self.assertEqual(len(rows), 1)

    def test_search_no_match(self) -> None:
        rows = self.conn.execute(
            "SELECT adr_id FROM adrs_fts WHERE adrs_fts MATCH 'nonexistent_xyzzy'"
        ).fetchall()
        self.assertEqual(len(rows), 0)


class TestCLICommands(_TempDirMixin, unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        self._orig_root = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        adr_db.cmd_sync(self.conn, self.adr_dir)

    def tearDown(self) -> None:
        adr_db._repo_root = self._orig_root
        super().tearDown()

    def test_cmd_list(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_list(self.conn)
        self.assertEqual(rc, 0)
        self.assertIn("0042", buf.getvalue())
        self.assertIn("Use SQLite", buf.getvalue())

    def test_cmd_get_found(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_get(self.conn, "0042")
        self.assertEqual(rc, 0)
        self.assertIn("Use SQLite for ADR Tracking", buf.getvalue())

    def test_cmd_get_not_found(self) -> None:
        rc = adr_db.cmd_get(self.conn, "9999")
        self.assertEqual(rc, 1)

    def test_cmd_summary(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_summary(self.conn)
        self.assertEqual(rc, 0)
        output = buf.getvalue()
        self.assertIn("ADR 0042", output)
        self.assertIn("accepted", output)

    def test_cmd_search(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_search(self.conn, "SQLite")
        self.assertEqual(rc, 0)
        self.assertIn("0042", buf.getvalue())


class TestMainEntryPoint(_TempDirMixin, unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        self._orig_root = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)

    def tearDown(self) -> None:
        adr_db._repo_root = self._orig_root
        super().tearDown()

    def test_main_sync_and_list(self) -> None:
        import io
        from contextlib import redirect_stdout

        db = str(self.db_path)
        adr = str(self.adr_dir)

        # sync
        rc = adr_db.main(["--db", db, "--adr-dir", adr, "sync"])
        self.assertEqual(rc, 0)

        # list
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.main(["--db", db, "--adr-dir", adr, "list"])
        self.assertEqual(rc, 0)
        self.assertIn("0042", buf.getvalue())

    def test_main_no_command(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.main([])
        self.assertEqual(rc, 1)


# ---------------------------------------------------------------------------
# Journey test fixtures and helpers
# ---------------------------------------------------------------------------

SAMPLE_JOURNEY_FRONTMATTER = textwrap.dedent("""\
    ---
    session_id: ejs-session-2026-02-10-01
    author: github-copilot
    date: 2026-02-10
    repo: Engineering-Journey-System
    branch: copilot/capture-sub-agent-decisions
    agents_involved:
      - GitHub Copilot (main agent)
      - explore agent (codebase analysis)
    decision_detected: true
    adr_links:
      - ../../adr/0012-sub-agent-decision-capture-protocol.md
    tags:
      - ejs
      - multi-agent
    refs:
      - .github/agents/ejs-journey.agent.md
    ---

    # Problem / Intent

    Ensure sub-agents capture their decisions and collaboration.

    # Interaction Summary (Required)

    - Human: Asked how to ensure sub-agent decisions are captured
      - Agent: Explored the repository, identified the gap
      - Outcome: Implemented sub-agent contribution protocol

    # Decisions Made

    - Decision: Add Sub-Agent Contributions section
      - Reason: Sub-agent decisions were being lost
      - Impact: Better traceability

    # Key Learnings

    Sub-agent decisions are a distinct data category.

    # Future Agent Guidance

    Prefer recording sub-agent decisions in structured sections.
""")

SAMPLE_JOURNEY_PLAIN = textwrap.dedent("""\
    session_id: ejs-session-2026-03-02-01
    author: github-copilot
    date: 2026-03-02
    repo: Engineering-Journey-System
    branch: copilot/add-sqlite
    agents_involved: [github-copilot, explore-agent]
    decision_detected: false
    adr_links: []
    tags: [sqlite, adr-tracking]
    refs: []

    # Problem / Intent

    Create a SQLite-backed tool for ADR tracking.

    # Interaction Summary (Required)

    - Human: Requested SQLite implementation
      - Agent: Implemented CLI tool with FTS5
      - Outcome: Tool working with 5 commands

    # Key Learnings

    YAML octal parsing can silently corrupt IDs.

    # Future Agent Guidance

    Prefer database queries over file scanning.
""")


class _JourneyTempDirMixin:
    """Mixin providing a temp dir with both adr and journey subdirs."""

    def setUp(self) -> None:
        import tempfile

        self._tmpdir_obj = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmpdir_obj.name)
        self.adr_dir = self.tmp / "adr"
        self.adr_dir.mkdir()
        self.journey_dir = self.tmp / "journey" / "2026"
        self.journey_dir.mkdir(parents=True)
        # Also add a _templates dir to ensure it's skipped
        (self.tmp / "journey" / "_templates").mkdir()
        self.db_path = self.tmp / "test.db"
        self.conn = adr_db.init_db(self.db_path)
        self._orig_root = adr_db._repo_root
        adr_db._repo_root = lambda: self.tmp  # type: ignore[assignment]

    def tearDown(self) -> None:
        adr_db._repo_root = self._orig_root
        self.conn.close()
        self._tmpdir_obj.cleanup()


def _write_journey(d: Path, filename: str, content: str) -> Path:
    fp = d / filename
    fp.write_text(content, encoding="utf-8")
    return fp


# ---------------------------------------------------------------------------
# Journey tests
# ---------------------------------------------------------------------------


class TestParseJourneyFile(_JourneyTempDirMixin, unittest.TestCase):
    def test_frontmatter_journey(self) -> None:
        fp = _write_journey(self.journey_dir, "ejs-session-2026-02-10-01.md",
                            SAMPLE_JOURNEY_FRONTMATTER)
        record = adr_db.parse_journey_file(fp)
        self.assertIsNotNone(record)
        self.assertEqual(record["session_id"], "ejs-session-2026-02-10-01")
        self.assertEqual(record["date"], "2026-02-10")
        self.assertEqual(record["decision_detected"], "true")
        self.assertIn("sub-agents capture", record["problem_intent"])

    def test_plain_metadata_journey(self) -> None:
        fp = _write_journey(self.journey_dir, "ejs-session-2026-03-02-01.md",
                            SAMPLE_JOURNEY_PLAIN)
        record = adr_db.parse_journey_file(fp)
        self.assertIsNotNone(record)
        self.assertEqual(record["session_id"], "ejs-session-2026-03-02-01")
        self.assertEqual(record["date"], "2026-03-02")
        self.assertIn("SQLite-backed tool", record["problem_intent"])

    def test_no_session_id_returns_none(self) -> None:
        fp = _write_journey(self.journey_dir, "bad.md", "# Just a heading\n")
        self.assertIsNone(adr_db.parse_journey_file(fp))

    def test_template_skipped(self) -> None:
        template = "session_id:\nauthor:\ndate:\n\n# Problem / Intent\nDescribe.\n"
        fp = _write_journey(self.journey_dir, "template.md", template)
        self.assertIsNone(adr_db.parse_journey_file(fp))


class TestJourneySync(_JourneyTempDirMixin, unittest.TestCase):
    def test_sync_inserts_journeys(self) -> None:
        _write_journey(self.journey_dir, "ejs-session-2026-02-10-01.md",
                       SAMPLE_JOURNEY_FRONTMATTER)
        _write_journey(self.journey_dir, "ejs-session-2026-03-02-01.md",
                       SAMPLE_JOURNEY_PLAIN)
        rc = adr_db._sync_journeys(self.conn, self.tmp / "journey")
        self.assertEqual(rc, 0)
        rows = self.conn.execute("SELECT * FROM journeys").fetchall()
        self.assertEqual(len(rows), 2)

    def test_sync_removes_stale_journeys(self) -> None:
        _write_journey(self.journey_dir, "ejs-session-2026-02-10-01.md",
                       SAMPLE_JOURNEY_FRONTMATTER)
        adr_db._sync_journeys(self.conn, self.tmp / "journey")
        self.assertEqual(
            self.conn.execute("SELECT count(*) FROM journeys").fetchone()[0], 1)

        (self.journey_dir / "ejs-session-2026-02-10-01.md").unlink()
        adr_db._sync_journeys(self.conn, self.tmp / "journey")
        self.assertEqual(
            self.conn.execute("SELECT count(*) FROM journeys").fetchone()[0], 0)

    def test_sync_skips_templates_dir(self) -> None:
        template_dir = self.tmp / "journey" / "_templates"
        _write_journey(template_dir, "journey-template.md",
                       "session_id: test\n# Problem / Intent\nTemplate.\n")
        adr_db._sync_journeys(self.conn, self.tmp / "journey")
        rows = self.conn.execute("SELECT * FROM journeys").fetchall()
        self.assertEqual(len(rows), 0)

    def test_cmd_sync_includes_journeys(self) -> None:
        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        _write_journey(self.journey_dir, "ejs-session-2026-03-02-01.md",
                       SAMPLE_JOURNEY_PLAIN)
        rc = adr_db.cmd_sync(self.conn, self.adr_dir, self.tmp / "journey")
        self.assertEqual(rc, 0)
        self.assertEqual(
            self.conn.execute("SELECT count(*) FROM adrs").fetchone()[0], 1)
        self.assertEqual(
            self.conn.execute("SELECT count(*) FROM journeys").fetchone()[0], 1)


class TestJourneyFTS(_JourneyTempDirMixin, unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        _write_journey(self.journey_dir, "ejs-session-2026-03-02-01.md",
                       SAMPLE_JOURNEY_PLAIN)
        adr_db._sync_journeys(self.conn, self.tmp / "journey")

    def test_fts_finds_journey(self) -> None:
        rows = self.conn.execute(
            "SELECT session_id FROM journeys_fts WHERE journeys_fts MATCH 'SQLite'"
        ).fetchall()
        self.assertEqual(len(rows), 1)

    def test_fts_no_match(self) -> None:
        rows = self.conn.execute(
            "SELECT session_id FROM journeys_fts WHERE journeys_fts MATCH 'nonexistent_xyzzy'"
        ).fetchall()
        self.assertEqual(len(rows), 0)


class TestJourneyCLICommands(_JourneyTempDirMixin, unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        _write_journey(self.journey_dir, "ejs-session-2026-03-02-01.md",
                       SAMPLE_JOURNEY_PLAIN)
        adr_db._sync_journeys(self.conn, self.tmp / "journey")

    def test_cmd_list_journeys(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_list_journeys(self.conn)
        self.assertEqual(rc, 0)
        self.assertIn("ejs-session-2026-03-02-01", buf.getvalue())

    def test_cmd_get_journey_found(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_get_journey(self.conn, "ejs-session-2026-03-02-01")
        self.assertEqual(rc, 0)
        self.assertIn("SQLite-backed tool", buf.getvalue())

    def test_cmd_get_journey_not_found(self) -> None:
        rc = adr_db.cmd_get_journey(self.conn, "nonexistent")
        self.assertEqual(rc, 1)

    def test_cmd_summary_journeys(self) -> None:
        import io
        from contextlib import redirect_stdout

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_summary_journeys(self.conn)
        self.assertEqual(rc, 0)
        output = buf.getvalue()
        self.assertIn("ejs-session-2026-03-02-01", output)
        self.assertIn("SQLite-backed tool", output)

    def test_search_across_both(self) -> None:
        import io
        from contextlib import redirect_stdout

        _write_adr(self.adr_dir, "0042-test.md", SAMPLE_ADR)
        adr_db._sync_adrs(self.conn, self.adr_dir)

        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = adr_db.cmd_search(self.conn, "SQLite")
        self.assertEqual(rc, 0)
        output = buf.getvalue()
        self.assertIn("[ADR 0042]", output)
        self.assertIn("[Journey ejs-session-2026-03-02-01]", output)


if __name__ == "__main__":
    unittest.main()
