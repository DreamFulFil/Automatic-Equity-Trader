---
agent: agent
---
Release instructions — Create GitHub release via REST API (curl)

**Note:** This file is an operational prompt template for maintainers. Ensure you have run unit tests and used `./scripts/operational/bump-version.sh` to update `VERSION` (and create the tag) before creating the release.

This document describes the exact steps to create a GitHub Release using the GitHub Releases REST API (`2022-11-28`) with curl. Follow these steps only when a release is required by the rules in `.github/instructions/commit.instructions.md` (i.e., **minor bumps**: commit types `feat`, `perf`, `refactor`).

Prerequisites
- You must have pushed the commit that bumps the `VERSION` file and created the annotated tag `v${NEW_VERSION}` on the target branch (usually `main`).
- A valid `GITHUB_TOKEN` with `repo` permissions must be available in your shell environment.
- The release body must use the **project release format** described below and include at least 3 bullet points in `## Changes`.

Checklist (quick)
1. Verify `VERSION` file was updated to the new version (e.g., `0.80.0` → `0.81.0`).
   ```bash
   cat VERSION
   ```
2. Create annotated tag if not created yet:
   ```bash
   NEW_VERSION=$(cat VERSION)
   git tag -a "v${NEW_VERSION}" -m "Release v${NEW_VERSION}"
   git push origin "v${NEW_VERSION}"
   ```
3. Confirm tag exists locally and remotely:
   ```bash
   git tag --list | rg "v${NEW_VERSION}"
   git ls-remote --tags origin | rg "v${NEW_VERSION}"
   ```

Building the Release Body
- The release body MUST start with an H1 line and include a `## Summary` and `## Changes` sections.
- The required format (exactly):

  # Release v${NEW_VERSION}

  ## Summary
  - <clean commit message (text after the commit type prefix)>

  ## Changes
  - [At least 3 concise bullet points summarizing the change(s)]

Example: (build it into an env variable to avoid JSON quoting problems)
```bash
NEW_VERSION=$(cat VERSION)
# Clean commit message (strip the leading 'type: ' if present)
COMMIT_MSG=$(git log -1 --pretty=%B | sed 's/^[^:]*: //')
# Files changed in the commit
FILES_CHANGED=$(git diff-tree --no-commit-id --name-only -r HEAD)
# Optional automated tallies to help with summary lines
JAVA_FILES=$(git diff --name-only HEAD~1 HEAD | rg -c "\\.java$" || echo 0)
PY_FILES=$(git diff --name-only HEAD~1 HEAD | rg -c "\\.py$" || echo 0)
# Build the body
BODY="# Release v${NEW_VERSION}\n\n## Summary\n- ${COMMIT_MSG}\n\n## Changes\n- Updated ${JAVA_FILES} Java files\n- Modified configuration\n- Enhanced tests\n"
# Preview
echo -e "$BODY"
# Escape for JSON using jq
BODY_ESCAPED=$(echo "$BODY" | jq -Rs .)
```

Create the Release via curl
- Replace `<owner>` and `<repo>` in the URL with the repository owner/name (e.g., `DreamFulFil/Automatic-Equity-Trader`).
- Use the `GITHUB_TOKEN` environment variable with at least `repo` scope.

> 💡 Tip: When running in fish, use `$GITHUB_TOKEN` (no braces) and fish-native constructs; provide the fish example or a `bash -lc` fallback only when strictly necessary.

```bash
# Capture release API output into logs with a timestamp
mkdir -p logs
LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
LOGFILE="logs/release-${LOG_TS}.log"

# Create a new release
curl -L -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/<owner>/<repo>/releases \
  -d "{\"tag_name\": \"v${NEW_VERSION}\", \"target_commitish\": \"main\", \"name\": \"v${NEW_VERSION}\", \"body\": ${BODY_ESCAPED}, \"draft\": false, \"prerelease\": false, \"generate_release_notes\": false }" 2>&1 | tee "$LOGFILE"
```

Updating an existing release (edit release body)
- To update the release body for an existing release, first fetch the release metadata (to obtain the `id`) and then PATCH the release using the `id`.

```bash
# Example: fetch release metadata by tag and extract release id
RELEASE_TAG="v${NEW_VERSION}"
RELEASE_JSON=$(curl -sS -H "Accept: application/vnd.github+json" -H "Authorization: Bearer $GITHUB_TOKEN" -H "X-GitHub-Api-Version: 2022-11-28" "https://api.github.com/repos/<owner>/<repo>/releases/tags/${RELEASE_TAG}")
RELEASE_ID=$(echo "$RELEASE_JSON" | jq -r .id)

# Build the updated body (must follow project format)
UPDATED_BODY="# Release ${RELEASE_TAG}\n\n## Summary\n- ${COMMIT_MSG}\n\n## Changes\n- Auto-populated strategy_stock_mapping from backtest results\n- Added fallback name resolution in HistoryDataService\n- Added unit tests and operational checks\n"

# Escape the body for JSON safely
UPDATED_BODY_ESCAPED=$(echo "$UPDATED_BODY" | jq -Rs .)

# PATCH the release body and save output to logs
LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
LOGFILE="logs/release-update-${LOG_TS}-${RELEASE_TAG}.log"
curl -sS -X PATCH \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/<owner>/<repo>/releases/${RELEASE_ID} \
  -d "{\"body\": ${UPDATED_BODY_ESCAPED}}" 2>&1 | tee "$LOGFILE"

# Validate: successful PATCH returns HTTP 200 and the response contains the updated body
```

Notes & Rules (important)
Notes & Rules (important)
- Only create a release for **minor bumps** (types: `feat`, `perf`, `refactor`) as defined in `.github/instructions/commit.instructions.md`.
- The release **body must** be in the required format and include at least 3 bullet points in the `## Changes` section.
- Use the commit message content (text after the `type:` prefix) as the `## Summary` item.
- Ensure you pushed both the commit and tag to `origin` before calling the API.
- The request must be made using `curl` (per project policy) and must include the `Authorization: Bearer ${GITHUB_TOKEN}` header.

Validation & Common Checks
- If the API call returns HTTP 201, the release was created successfully.
- If you get an error, check:
  - The `GITHUB_TOKEN` has sufficient scopes
  - The `tag_name` exists on origin
  - The JSON body is properly escaped (use `jq` to escape)

Automating the steps (optional)
- We recommend adding a small release helper script (e.g., `scripts/create-release.sh`) to enforce format and avoid mistakes.
- The script should:
  - Validate the commit type is allowed for release (check latest commit header)
  - Validate `VERSION` and tag exist
  - Generate the release `BODY` per format
  - Escape the body and call the `curl` endpoint

Example release helper (sketch)
```bash
# scripts/create-release.sh (recommended)
set -e
NEW_VERSION=$(cat VERSION)
COMMIT_HEADER=$(git log -1 --pretty=%B | head -n1)
if ! echo "$COMMIT_HEADER" | rg -q "^(feat|perf|refactor)(\(|:).*"; then
  echo "No release necessary for commit: $COMMIT_HEADER"
  exit 0
fi
# Build the BODY following the required format
COMMIT_MSG=$(git log -1 --pretty=%B | sed 's/^[^:]*: //')
FILES_CHANGED=$(git diff-tree --no-commit-id --name-only -r HEAD)
JAVA_FILES=$(git diff --name-only HEAD~1 HEAD | rg -c "\\.java$" || echo 0)
PY_FILES=$(git diff --name-only HEAD~1 HEAD | rg -c "\\.py$" || echo 0)
BODY="# Release v${NEW_VERSION}\n\n## Summary\n- ${COMMIT_MSG}\n\n## Changes\n- Updated ${JAVA_FILES} Java files\n- Modified configuration\n- Enhanced tests\n"
BODY_ESCAPED=$(echo "$BODY" | jq -Rs .)

# Create release via curl (see section above for caveats about shells)
mkdir -p logs
LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
LOGFILE="logs/release-${LOG_TS}.log"

curl -L -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/<owner>/<repo>/releases \
  -d "{\"tag_name\": \"v${NEW_VERSION}\", \"target_commitish\": \"main\", \"name\": \"v${NEW_VERSION}\", \"body\": ${BODY_ESCAPED}, \"draft\": false, \"prerelease\": false, \"generate_release_notes\": false }" 2>&1 | tee "$LOGFILE"

# scripts/operational/update-release-body.sh (convenience helper)
# Usage: REPO_OWNER=<owner> REPO_NAME=<repo> RELEASE_TAG=vX.Y.Z GITHUB_TOKEN=<token> ./scripts/operational/update-release-body.sh
set -e
REPO_OWNER=${REPO_OWNER:-DreamFulFil}
REPO_NAME=${REPO_NAME:-Automatic-Equity-Trader}
RELEASE_TAG=${RELEASE_TAG:-v${NEW_VERSION}}

# Fetch release id for the tag
RELEASE_JSON=$(curl -sS -H "Accept: application/vnd.github+json" -H "Authorization: Bearer $GITHUB_TOKEN" -H "X-GitHub-Api-Version: 2022-11-28" "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/tags/${RELEASE_TAG}")
RELEASE_ID=$(echo "$RELEASE_JSON" | jq -r .id)
if [ -z "$RELEASE_ID" ] || [ "$RELEASE_ID" = "null" ]; then
  echo "Could not find release id for tag ${RELEASE_TAG}. Aborting."
  exit 1
fi

# Build UPDATED_BODY following required format
UPDATED_BODY="# Release ${RELEASE_TAG}\n\n## Summary\n- ${COMMIT_MSG}\n\n## Changes\n- Short bullet one\n- Short bullet two\n- Short bullet three\n"
UPDATED_BODY_ESCAPED=$(echo "$UPDATED_BODY" | jq -Rs .)

LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
LOGFILE="logs/release-update-${LOG_TS}-${RELEASE_TAG}.log"

# PATCH the release body
curl -sS -X PATCH \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/${RELEASE_ID} \
  -d "{\"body\": ${UPDATED_BODY_ESCAPED}}" 2>&1 | tee "$LOGFILE"

# Validate the operation: check HTTP code and saved log for success or failure
```

Convenience: update an existing release body
- Use `scripts/operational/update-release-body.sh <tag> <body-file>` to PATCH an existing release body by tag (this repository includes example bodies under `scripts/operational/release-bodies/`).

Troubleshooting & Tips
- If you see an HTTP 4xx/5xx from the API:
  - Confirm `GITHUB_TOKEN` is exported and has `repo` scope.
  - Confirm the annotated tag `v${NEW_VERSION}` exists on `origin` (use `git ls-remote --tags origin`).
  - Check the log file in `logs/` for the full request/response and JSON error details.
- Shells: if using fish, prefer `$GITHUB_TOKEN` (no braces) in curl header values, or run the commands via `bash -lc "..."`.
- Validation: the API returns HTTP 201 for successful creation (POST) and HTTP 200 for successful updates (PATCH). The response includes `html_url` and `published_at` when published.

Assistant automation — AI-run semantics
- Invocation: When instructed to **"Run release.prompt.md"** or when automatically invoked by `commit.prompt.md` after a minor bump, the assistant will perform a non-interactive release creation run following these rules:
  1. Preconditions: verify `GITHUB_TOKEN` is present; if missing, abort release creation and record `SKIPPED` in `logs/releases-summary.csv` with a note.
  2. Build the required release `BODY` per the project format. If the commit versus changed-files produce fewer than 3 bullets for `## Changes`, the assistant will synthesize concise bullets using the commit message, files changed, and test outcomes to meet the minimum requirement.
  3. Escape the body using `jq -Rs .` and POST the release via the Releases REST API (use `curl` per project policy), capturing full API request/response to `logs/release-<TS>-${TAG}.log`.
  4. On success (HTTP 201): parse the returned `html_url`, append `tag,commit,html_url` to `logs/releases-summary.csv`, and return the `html_url` in the assistant response.
  5. On failure: save the full API response to the log file and return a concise error report (HTTP code + message). The assistant will not prompt interactively for corrective action.

Non-interactive & safety notes
- The assistant will not ask for secrets; if `GITHUB_TOKEN` is not available it will skip release creation and report `SKIPPED`. The assistant will redact tokens and sensitive headers from any chat output.
- Logs with raw API responses are stored locally in `logs/` and referenced in the assistant summary for auditing.

End of instructions
