#!/usr/bin/env bash
set -euo pipefail

# Usage:
#  scripts/operational/validate-commit-and-version.sh <commit-msg-file>
# This script validates that the commit message follows Conventional Commits and
# that for minor-bump commit types (feat|perf|refactor) a VERSION file is staged.

COMMIT_MSG_FILE=${1:-}
if [ -z "$COMMIT_MSG_FILE" ]; then
  echo "Usage: $0 <commit-msg-file>" >&2
  exit 2
fi

if [ ! -f "$COMMIT_MSG_FILE" ]; then
  echo "Commit message file not found: $COMMIT_MSG_FILE" >&2
  exit 2
fi

COMMIT_MSG_HEAD=$(head -n1 "$COMMIT_MSG_FILE" | tr -d '\n')
# Choose matcher: prefer ripgrep (rg) if available, fallback to grep -E
if command -v rg >/dev/null 2>&1; then
  MATCHER="rg -q"
else
  MATCHER="grep -E -q"
fi

# Conventional Commit header regex (simplified)
if ! echo "$COMMIT_MSG_HEAD" | $MATCHER "^(feat|fix|perf|refactor|docs|test|ci|chore)(\(|:).*"; then
  echo "ERROR: Commit message header does not follow Conventional Commits format." >&2
  echo "Expected: <type>(<scope>): <short summary>" >&2
  echo "See .github/prompts/commit.prompt.md for guidance." >&2
  echo "Commit header: '$COMMIT_MSG_HEAD'" >&2
  exit 1
fi

# Determine commit type
COMMIT_TYPE=$(echo "$COMMIT_MSG_HEAD" | sed -E 's/^([^(:]+).*$/\1/')

# For minor bump types, ensure VERSION is staged
if echo "$COMMIT_TYPE" | $MATCHER "^(feat|perf|refactor)$"; then
  STAGED_FILES=$(git diff --cached --name-only)
  if ! echo "$STAGED_FILES" | $MATCHER "(^|/)VERSION$"; then
    echo "ERROR: This commit is a minor bump type ('$COMMIT_TYPE') and requires updating the VERSION file." >&2
    echo "Please run: ./scripts/operational/bump-version.sh minor" >&2
    echo "Then stage the updated VERSION and include it in this commit." >&2
    exit 1
  fi
fi

# For patch/other types, no VERSION check required
exit 0
