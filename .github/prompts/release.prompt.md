---
agent: agent
---
Release instructions — Create GitHub release via REST API (curl)

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

```bash
curl -L -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/<owner>/<repo>/releases \
  -d "{\"tag_name\": \"v${NEW_VERSION}\", \"target_commitish\": \"main\", \"name\": \"v${NEW_VERSION}\", \"body\": ${BODY_ESCAPED}, \"draft\": false, \"prerelease\": false, \"generate_release_notes\": false }"
```

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
# scripts/create-release.sh
set -e
NEW_VERSION=$(cat VERSION)
COMMIT_HEADER=$(git log -1 --pretty=%B | head -n1)
if ! echo "$COMMIT_HEADER" | rg -q "^(feat|perf|refactor)(\(|:).*"; then
  echo "No release necessary for commit: $COMMIT_HEADER"
  exit 0
fi
# ... build BODY as above ...
# call curl
```

End of instructions
