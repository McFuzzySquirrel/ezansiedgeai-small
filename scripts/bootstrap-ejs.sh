#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────
# EJS Bootstrap Script
# Adds the Engineering Journey System to an existing repository.
#
# Usage:
#   From the EJS starter repo:
#     ./scripts/bootstrap-ejs.sh /path/to/target-repo
#
#   Or download and run directly:
#     curl -sL https://raw.githubusercontent.com/McFuzzySquirrel/Engineering-Journey-System/main/scripts/bootstrap-ejs.sh | bash -s -- /path/to/target-repo
#
# What it does:
#   1. Copies the EJS agent profile, templates, and tooling
#   2. Appends the EJS Silent Recording Contract to your existing
#      copilot-instructions.md (does NOT replace it)
#   3. Optionally installs git hooks for commit/push reminders
#
# EJS is additive and non-competing — it layers silent collaboration
# recording onto whatever agents you already have.
# ─────────────────────────────────────────────────────────────────────

# ── Resolve EJS source directory ────────────────────────────────────

# If piped via curl, we need to clone the starter repo to a temp dir.
# If run from a local clone, use the repo root.
EJS_SOURCE=""
TEMP_DIR=""

resolve_source() {
  # Check if we're inside the EJS starter repo
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"

  if [[ -f "$script_dir/../.github/agents/ejs-journey.agent.md" ]]; then
    EJS_SOURCE="$(cd "$script_dir/.." && pwd)"
  elif [[ -f "$PWD/.github/agents/ejs-journey.agent.md" ]]; then
    EJS_SOURCE="$PWD"
  else
    echo "EJS: Cloning starter repo to temporary directory..."
    TEMP_DIR="$(mktemp -d)"
    git clone --depth 1 https://github.com/McFuzzySquirrel/Engineering-Journey-System.git "$TEMP_DIR" 2>/dev/null
    EJS_SOURCE="$TEMP_DIR"
  fi
}

cleanup() {
  if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then
    rm -rf "$TEMP_DIR"
  fi
}
trap cleanup EXIT

# ── Parse arguments ─────────────────────────────────────────────────

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS] <target-repo>

Bootstrap the Engineering Journey System into an existing repository.

Arguments:
  <target-repo>    Path to the target repository (must be a git repo)

Options:
  --with-hooks     Also install git commit/push reminder hooks
  --with-pr        Also copy the PR template
  --full           Copy everything (hooks + PR template)
  --dry-run        Show what would be done without making changes
  -h, --help       Show this help message

Tiers:
  Tier 1 (always-on) activates automatically after bootstrap — every
  agent in the repo silently records to Session Journey files.

  Tier 2 (bookend) requires the agent profile — invoke @ejs-journey
  at session start/end.

  Tier 3 (coordinator) requires the agent profile — select ejs-journey
  from the agent dropdown for full-session coordination.
EOF
  exit 0
}

TARGET=""
WITH_HOOKS=false
WITH_PR=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-hooks) WITH_HOOKS=true; shift ;;
    --with-db)    shift ;;  # ignored — database tool is now always included
    --with-pr)    WITH_PR=true; shift ;;
    --full)       WITH_HOOKS=true; WITH_PR=true; shift ;;
    --dry-run)    DRY_RUN=true; shift ;;
    -h|--help)    usage ;;
    -*)           echo "EJS: unknown option: $1"; usage ;;
    *)            TARGET="$1"; shift ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "EJS: error — target repository path is required."
  echo ""
  usage
fi

# ── Validate target ─────────────────────────────────────────────────

TARGET="$(cd "$TARGET" 2>/dev/null && pwd || echo "$TARGET")"

if [[ ! -d "$TARGET" ]]; then
  echo "EJS: error — target directory does not exist: $TARGET"
  exit 1
fi

if ! git -C "$TARGET" rev-parse --git-dir >/dev/null 2>&1; then
  echo "EJS: error — target is not a git repository: $TARGET"
  exit 1
fi

# ── Resolve source ──────────────────────────────────────────────────

resolve_source

echo "EJS: bootstrapping into $TARGET"
echo "EJS: source: $EJS_SOURCE"
echo ""

# ── Helper: copy file ──────────────────────────────────────────────

copy_file() {
  local src="$1"
  local dest="$2"
  local label="${3:-$dest}"

  if [[ "$DRY_RUN" == true ]]; then
    if [[ -f "$TARGET/$dest" ]]; then
      echo "  [skip] $label (already exists)"
    else
      echo "  [copy] $label"
    fi
    return
  fi

  if [[ -f "$TARGET/$dest" ]]; then
    echo "  [skip] $label (already exists)"
    return
  fi

  mkdir -p "$TARGET/$(dirname "$dest")"
  cp "$EJS_SOURCE/$src" "$TARGET/$dest"
  echo "  [done] $label"
}

# ── Helper: append to copilot-instructions.md ───────────────────────

append_recording_contract() {
  local target_file="$TARGET/.github/copilot-instructions.md"
  local source_file="$EJS_SOURCE/.github/copilot-instructions.md"
  local marker="## EJS Silent Recording Contract (Always-On)"

  if [[ "$DRY_RUN" == true ]]; then
    if [[ -f "$target_file" ]] && grep -qF "$marker" "$target_file" 2>/dev/null; then
      echo "  [skip] .github/copilot-instructions.md (EJS block already present)"
    elif [[ -f "$target_file" ]]; then
      echo "  [append] EJS Silent Recording Contract → .github/copilot-instructions.md"
    else
      echo "  [create] .github/copilot-instructions.md (with EJS recording contract)"
    fi
    return
  fi

  mkdir -p "$TARGET/.github"

  # If the file already has the EJS block, skip
  if [[ -f "$target_file" ]] && grep -qF "$marker" "$target_file" 2>/dev/null; then
    echo "  [skip] .github/copilot-instructions.md (EJS block already present)"
    return
  fi

  # Extract just the recording contract block (from --- onwards)
  local contract
  contract="$(sed -n '/^---$/,$ p' "$source_file")"

  if [[ -f "$target_file" ]]; then
    # Append to existing file
    printf '\n\n%s\n' "$contract" >> "$target_file"
    echo "  [done] Appended EJS Silent Recording Contract to .github/copilot-instructions.md"
  else
    # Create new file with the full contents
    cp "$source_file" "$target_file"
    echo "  [done] Created .github/copilot-instructions.md (with EJS recording contract)"
  fi
}

# ── Core files (always copied) ──────────────────────────────────────

echo "Core files:"
copy_file ".github/agents/ejs-journey.agent.md" ".github/agents/ejs-journey.agent.md" "Agent profile (.github/agents/ejs-journey.agent.md)"
append_recording_contract
copy_file "ejs-docs/journey/_templates/journey-template.md" "ejs-docs/journey/_templates/journey-template.md" "Journey template (ejs-docs/journey/_templates/journey-template.md)"
copy_file "ejs-docs/adr/0000-adr-template.md" "ejs-docs/adr/0000-adr-template.md" "ADR template (ejs-docs/adr/0000-adr-template.md)"
copy_file "scripts/adr-db.py" "scripts/adr-db.py" "adr-db.py (scripts/adr-db.py)"
copy_file "scripts/tests/test_adr_db.py" "scripts/tests/test_adr_db.py" "Tests (scripts/tests/test_adr_db.py)"

# Add .ejs.db to .gitignore if not already there
if [[ "$DRY_RUN" != true ]]; then
  if [[ -f "$TARGET/.gitignore" ]]; then
    if ! grep -qF ".ejs.db" "$TARGET/.gitignore" 2>/dev/null; then
      echo ".ejs.db" >> "$TARGET/.gitignore"
      echo "  [done] Added .ejs.db to .gitignore"
    fi
  else
    echo ".ejs.db" > "$TARGET/.gitignore"
    echo "  [done] Created .gitignore with .ejs.db"
  fi
else
  echo "  [append] .ejs.db → .gitignore"
fi
echo ""

# ── Optional: PR template ──────────────────────────────────────────

if [[ "$WITH_PR" == true ]]; then
  echo "PR template:"
  copy_file ".github/copilot/pull_request_template.md" ".github/copilot/pull_request_template.md" "PR template (.github/copilot/pull_request_template.md)"
  echo ""
fi

# ── Optional: git hooks ────────────────────────────────────────────

if [[ "$WITH_HOOKS" == true ]]; then
  echo "Git hooks:"
  copy_file ".githooks/post-commit" ".githooks/post-commit" "post-commit hook (.githooks/post-commit)"
  copy_file ".githooks/pre-push" ".githooks/pre-push" "pre-push hook (.githooks/pre-push)"
  copy_file "scripts/install-githooks.sh" "scripts/install-githooks.sh" "Hook installer (scripts/install-githooks.sh)"
  copy_file "scripts/install-githooks.ps1" "scripts/install-githooks.ps1" "Hook installer PS1 (scripts/install-githooks.ps1)"

  if [[ "$DRY_RUN" != true ]]; then
    chmod +x "$TARGET/.githooks/post-commit" "$TARGET/.githooks/pre-push" 2>/dev/null || true
    chmod +x "$TARGET/scripts/install-githooks.sh" 2>/dev/null || true

    # Install hooks
    git -C "$TARGET" config core.hooksPath .githooks 2>/dev/null || true
    echo "  [done] Activated hooks (core.hooksPath=.githooks)"
  else
    echo "  [activate] git config core.hooksPath .githooks"
  fi
  echo ""
fi

# ── Summary ─────────────────────────────────────────────────────────

if [[ "$DRY_RUN" == true ]]; then
  echo "─── Dry run complete (no changes made) ───"
  echo ""
  echo "Run without --dry-run to apply changes."
else
  echo "─── EJS bootstrap complete ───"
  echo ""
  echo "What happens now:"
  echo "  • Tier 1 (always-on): Active immediately — every Copilot agent"
  echo "    in this repo will silently record to Session Journey files."
  echo "  • Tier 2 (bookend): Say '@ejs-journey initialize session' to start"
  echo "    and '@ejs-journey finalize session' to end."
  echo "  • Tier 3 (coordinator): Select ejs-journey from the agent dropdown."
  echo ""
  echo "Next steps:"
  echo "  1. git add -A && git commit -m 'chore: bootstrap EJS'"
  echo "  2. Start working — EJS records automatically"
fi
