#!/usr/bin/env bash
set -euo pipefail

# Validate the latest commit(s) in the current ref in CI.
# This script runs the same validation as local commit hooks but is suitable for CI.

# Find the commits included in this push / PR (use Git refs available in CI)
# We validate the most recent commit on the checked-out branch (HEAD).
COMMIT=$(git rev-parse --verify HEAD)
HEADER=$(git log -1 --pretty=%s "$COMMIT")

echo "Validating commit $COMMIT: $HEADER"

# Use the same validator as local hooks
scripts/operational/validate-commit-and-version.sh <(git log -1 --pretty=%B "$COMMIT")

# Additional CI checks: for minor bumps, ensure the VERSION file changed in the commit
TYPE=$(echo "$HEADER" | sed -E 's/^([^(:]+).*$/\1/')
if echo "$TYPE" | rg -q "^(feat|perf|refactor)$"; then
  echo "Commit is a minor bump type: $TYPE — verifying VERSION was changed in commit"
  if ! git show --name-only --pretty="" "$COMMIT" | rg -q "^VERSION$"; then
    echo "ERROR: Minor-bump commit did not change VERSION. Please run ./scripts/operational/bump-version.sh minor and include VERSION in the commit." >&2
    exit 1
  fi
fi

echo "CI validation succeeded for commit $COMMIT"