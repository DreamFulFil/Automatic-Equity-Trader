#!/usr/bin/env bash
set -euo pipefail

# Audit commit history for:
#  - Conventional Commit header format
#  - VERSION updates when required
#  - Tag existence matching VERSION changes
#  - (Optional) Release existence on GitHub (requires GITHUB_TOKEN)

REPO_OWNER=${REPO_OWNER:-DreamFulFil}
REPO_NAME=${REPO_NAME:-Automatic-Equity-Trader}

echo "Starting repository audit: validating commits and VERSION changes"

# Helper: semver parse
parse_semver() {
  echo "$1" | awk -F. '{print $1" "$2" "$3}'
}

last_version="0.0.0"
last_version_commit=""

violations=()

commits=$(git rev-list --reverse --first-parent main)
for c in $commits; do
  header=$(git log -1 --pretty=%s "$c")
  body=$(git log -1 --pretty=%B "$c")
  echo "\nChecking commit $c -- $header"

  # 1) Validate header format
  if ! echo "$header" | rg -q "^(feat|fix|perf|refactor|docs|test|ci|chore)(\(|:).*"; then
    violations+=("$c: INVALID_COMMIT_HEADER: '$header'")
  fi

  type=$(echo "$header" | sed -E 's/^([^(:]+).*$/\1/')

  # 2) Did this commit touch VERSION?
  touched_version=false
  if git show --name-only --pretty="" "$c" | rg -q "^VERSION$"; then
    touched_version=true
    new_version=$(git show "$c":VERSION 2>/dev/null || true)
    if [ -z "$new_version" ]; then
      violations+=("$c: VERSION_PRESENT_BUT_EMPTY or unreadable")
    fi
  fi

  # 3) Enforce VERSION change for minor bump types
  if echo "$type" | rg -q "^(feat|perf|refactor)$"; then
    if [ "$touched_version" = false ]; then
      violations+=("$c: MISSING_VERSION_CHANGE for minor bump type ($type)")
    else
      # Check semantic increment rules relative to last_version
      read la ma pa <<< "$(parse_semver $last_version)"
      read na nb nc <<< "$(parse_semver $new_version)"
      # Expect: major same, minor = last_minor + 1, patch = 0
      if [ "$la" != "$na" ] || [ "$nb" -ne $((ma + 1)) ] || [ "$nc" -ne 0 ]; then
        violations+=("$c: VERSION_INCORRECT_INCREMENT (was $last_version -> $new_version) for minor bump")
      fi
      last_version="$new_version"
      last_version_commit="$c"
    fi
  else
    # For patch-like commits (fix,chore,docs,test,ci), if they change VERSION, check patch increment
    if [ "$touched_version" = true ]; then
      read la ma pa <<< "$(parse_semver $last_version)"
      read na nb nc <<< "$(parse_semver $new_version)"
      if [ "$la" != "$na" ] || [ "$nb" != "$ma" ] || [ "$nc" -ne $((pa + 1)) ]; then
        violations+=("$c: VERSION_INCORRECT_PATCH_INCREMENT (was $last_version -> $new_version) for patch-type change")
      fi
      last_version="$new_version"
      last_version_commit="$c"
    fi
  fi

  # 4) If commit changed VERSION, ensure a tag named v<new_version> exists and points to this commit
  if [ "$touched_version" = true ]; then
    tag="v${new_version}"
    if ! git rev-parse --verify --quiet "refs/tags/${tag}" >/dev/null; then
      violations+=("$c: MISSING_TAG ${tag}")
    else
      tag_commit=$(git rev-parse "${tag}^{commit}")
      if [ "$tag_commit" != "$c" ]; then
        violations+=("$c: TAG_POINT_MISMATCH ${tag} points to $tag_commit not $c")
      fi
    fi
  fi

done

# Print summary
echo "\nAUDIT COMPLETE"
if [ ${#violations[@]} -eq 0 ]; then
  echo "No violations found for commit header formats and VERSION/tag rules (local checks)."
else
  echo "Found ${#violations[@]} violation(s):"
  for v in "${violations[@]}"; do
    echo " - $v"
  done
fi

# Optional: check releases via GitHub API if GITHUB_TOKEN is set
if [ -n "${GITHUB_TOKEN:-}" ]; then
  echo "\nChecking GitHub releases for VERSION-tagged commits..."
  for tag in $(git tag --list | rg '^v' || true); do
    # query API for tag
    resp=$(curl -sS -H "Accept: application/vnd.github+json" -H "Authorization: Bearer $GITHUB_TOKEN" "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/tags/${tag}" || true)
    id=$(echo "$resp" | jq -r .id // empty)
    if [ -z "$id" ]; then
      echo " - Missing release for tag ${tag}"
    else
      echo " - Found release for ${tag} (id: $id)"
    fi
  done
else
  echo "\nSkipping GitHub release checks (GITHUB_TOKEN not set)."
  echo "To check releases, export GITHUB_TOKEN and re-run this script."
fi

# Return non-zero if violations found
if [ ${#violations[@]} -gt 0 ]; then
  exit 2
fi

exit 0
