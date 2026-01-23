---
agent: agent
---

# Commit prompt — Conventional Commits (automation-safe)

Purpose: enforce Conventional Commits and required pre-commit checks, while ensuring the generated commit message matches the *actual staged diff*, `VERSION` is updated when required, and tags/releases are not skipped when conditions are met.

Format: `<type>(<scope>): <short summary>` (imperative, ≤50 chars)
Types: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `ci`, `chore`

## Hard rules (avoid wrong commit messages)
- Always stage first: run `git add -A` before generating or validating the commit message.
- Generate the commit message *only* from `git diff --staged` (never from `git diff`).
- If a user supplies a commit header/message, verify it matches the staged diff; replace it if it doesn’t.
- Never mention files/features not present in the staged diff.

## VERSION + tagging + release rules
- `feat|perf|refactor` commits require a `VERSION` update included in the same commit.
- Use the canonical script with a commit type (NOT `minor`/`patch`):
  - `./scripts/operational/bump-version.sh <commit-type>`
- For `feat|perf|refactor`, also ensure tag `v$(cat VERSION)` points at the new commit and is pushed.
- For `feat|perf|refactor`, attempt to create a GitHub release when `gh` exists and `GITHUB_TOKEN` is set.

Important: `bump-version.sh` creates a tag immediately. If you run it before committing (so the `VERSION` change is included), it will tag the previous `HEAD`. Therefore you must force-move the tag to the new `HEAD` after committing and then force-push that tag.

## Required checklist (always)
- Run unit tests: `./run-tests.sh --unit $JASYPT_PASSWORD`
- Add/modify unit tests for your change
- No compile warnings or unused imports

## Validator (always)
- Run: `./scripts/operational/validate-commit-and-version.sh <commit-msg-file>`

## Non-interactive `/commit` workflow (order matters)
Use fish-compatible shell syntax (`set VAR (cmd)`), and never echo secrets.

1. Verify `JASYPT_PASSWORD` is available (prefer `/commit` arg; else env). If missing: abort.
2. Create an execution timestamp and log paths (UTC):
   - `set TS (date -u +%Y%m%dT%H%M%SZ)`
   - `set MSGFILE logs/commit-msg-$TS.txt`
   - `set RUNLOG logs/run-commit-$TS.log`
3. Stage everything first: `git add -A`. Abort if nothing is staged.
4. Decide commit type/scope (user-provided preferred; else infer from staged diff). Scope should be a short noun describing the primary area (e.g. `ollama`, `ci`, `tests`, `config`).
5. If type is `feat|perf|refactor`, bump version before tests/message generation:
   - Run (prevent premature releases): `env -u GITHUB_TOKEN ./scripts/operational/bump-version.sh <type>`
   - Stage again: `git add -A` (must include `VERSION`).
6. Run unit tests (UNIT tier). Save output to `$RUNLOG`.
7. Generate commit message from the staged diff:
   - Save to `$MSGFILE`
   - Body: 2–6 bullets describing the impact of the staged changes.
8. Run validator on the message file; if it fails, abort and save details to `$RUNLOG`.
9. Commit: `git commit -F $MSGFILE`.
10. If type is `feat|perf|refactor`, force-move the version tag to this commit:
   - `set NEW_VERSION (cat VERSION)`
   - `set TAG v$NEW_VERSION`
   - `git tag -f -a "$TAG" -m "Release $TAG"`
11. Push branch: `git push origin HEAD`.
12. If type is `feat|perf|refactor`, push the updated tag:
   - `git push --force-with-lease origin "$TAG"`
13. If type is `feat|perf|refactor` and `gh` exists and `GITHUB_TOKEN` is set:
   - Attempt release creation: `./scripts/operational/create-release.sh "$TAG"`.
   - If the release already exists, treat as `[SKIPPED]` rather than failure.

## Heuristics (only if no user header supplied)
- If changes are only `docs/**` → `docs(<scope>): ...`
- If changes touch only tests (`src/test/**`, `tests/**`, test resources) → `test(<scope>): ...`
- If changes touch `.github/workflows/**` and no runtime code changes → `ci(<scope>): ...`
- Otherwise default to `chore(<scope>): ...` unless you can clearly justify `fix|feat|perf|refactor`.

## Examples
- `feat(selection): add dynamic stock selection algorithm`
- `fix(bridge): handle null ticker names in telegram parser`
- `ci(ollama): update model pull in CI`