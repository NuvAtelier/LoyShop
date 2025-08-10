#!/usr/bin/env bash
set -euo pipefail

# Run diff-cover using JaCoCo coverage against a target branch (default: origin/master)
# - Requires jacoco.xml at core/target/site/jacoco/
# - Auto-creates a local Python venv and installs diff-cover if not already available
# - Generates HTML and Markdown reports to scripts/diff-cover.html/.md by default

# Determine script and repo locations
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if git -C "$SCRIPT_DIR" rev-parse --show-toplevel >/dev/null 2>&1; then
  REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
else
  REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fi
cd "$REPO_ROOT"

COMPARE_BRANCH="${1:-origin/master}"
REPORT_MD="${2:-$SCRIPT_DIR/diff-cover.md}"
REPORT_HTML="${3:-$SCRIPT_DIR/diff-cover.html}"

JACOCO_DIR="$REPO_ROOT/core/target/site/jacoco"
JACOCO_XML="$JACOCO_DIR/jacoco.xml"

# Candidate source roots for mapping JaCoCo file paths (repo-relative)
SRC_ROOTS=()
[[ -d "core/src/main/java" ]] && SRC_ROOTS+=("core/src/main/java")
[[ -d "core/src/main/kotlin" ]] && SRC_ROOTS+=("core/src/main/kotlin")
[[ -d "src/main/java" ]] && SRC_ROOTS+=("src/main/java")
[[ -d "src/main/kotlin" ]] && SRC_ROOTS+=("src/main/kotlin")

usage() {
  cat <<EOF
Usage: $(basename "$0") [compare-branch] [output-md] [output-html]

Defaults:
  compare-branch: origin/master
  output-md:      scripts/diff-cover.md
  output-html:    scripts/diff-cover.html

Examples:
  $(basename "$0")
  $(basename "$0") origin/master
  $(basename "$0") master /tmp/diff-cover.md
  $(basename "$0") master /tmp/diff-cover.md /tmp/diff-cover.html
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

# Ensure JaCoCo XML exists
if [[ ! -f "$JACOCO_XML" ]]; then
  # Fallback: any XML in the directory
  if compgen -G "$JACOCO_DIR/*.xml" > /dev/null; then
    # Pick the newest xml file
    JACOCO_XML=$(ls -1t "$JACOCO_DIR"/*.xml | head -n 1)
    echo "Using JaCoCo report: $JACOCO_XML"
  else
    echo "ERROR: JaCoCo XML not found. Expected at: $JACOCO_XML" >&2
    echo "Hint: Build coverage first, e.g.: mvn -pl core clean test jacoco:report" >&2
    exit 1
  fi
fi

# Ensure compare branch is available; fetch if needed
if ! git rev-parse --verify --quiet "$COMPARE_BRANCH" >/dev/null 2>&1; then
  # If format is remote/branch (e.g., origin/master), fetch that ref
  if [[ "$COMPARE_BRANCH" == */* ]]; then
    remote="${COMPARE_BRANCH%%/*}"
    ref="${COMPARE_BRANCH#*/}"
    echo "Fetching $remote $ref ..."
    git fetch "$remote" "$ref" --quiet || true
  else
    echo "Fetching origin $COMPARE_BRANCH ..."
    git fetch origin "$COMPARE_BRANCH" --quiet || true
  fi
fi

wrap_markdown_collapsed() {
  # Wraps the markdown report in a collapsible section to keep PR comment compact
  local tmp_file="${REPORT_MD}.tmp"
  {
    echo "### Diff Cover report"
    echo
    echo "<details>"
    echo "<summary>Click to expand</summary>"
    echo
    cat "$REPORT_MD"
    echo
    echo "</details>"
  } > "$tmp_file"
  mv "$tmp_file" "$REPORT_MD"
  echo "Wrapped markdown report in a collapsible section: $REPORT_MD"
}

run_diff_cover() {
  local diff_cover_bin="$1"
  local src_args=()
  if (( ${#SRC_ROOTS[@]} > 0 )); then
    src_args=(--src-roots "${SRC_ROOTS[@]}")
  fi
  echo "Running: $diff_cover_bin $JACOCO_XML --compare-branch $COMPARE_BRANCH ${src_args[*]} --html-report $REPORT_HTML --markdown-report $REPORT_MD"
  "$diff_cover_bin" "$JACOCO_XML" \
    --compare-branch "$COMPARE_BRANCH" \
    ${src_args[@]:-} \
    --html-report "$REPORT_HTML" \
    --markdown-report "$REPORT_MD"
  echo "Report generated: $REPORT_HTML"
  echo "Report generated: $REPORT_MD"
  wrap_markdown_collapsed
}

# If diff-cover is available globally, use it
if command -v diff-cover >/dev/null 2>&1; then
  run_diff_cover "diff-cover"
  exit 0
fi

# Otherwise, create and use a local venv under scripts/
VENV_DIR="$SCRIPT_DIR/.venv-diff-cover"
PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="python"
else
  echo "ERROR: Python not found. Please install Python 3." >&2
  exit 2
fi

if [[ ! -d "$VENV_DIR" ]]; then
  echo "Creating virtual environment at $VENV_DIR..."
  "$PYTHON_BIN" -m venv "$VENV_DIR"
fi

# shellcheck source=/dev/null
source "$VENV_DIR/bin/activate"
python -m pip install --upgrade pip >/dev/null
python -m pip install "diff-cover>=7.7,<9" >/dev/null

run_diff_cover "diff-cover"

deactivate
