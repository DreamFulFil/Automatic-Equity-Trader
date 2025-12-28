---
agent: agent
---

# Commit prompt — Conventional Commits (concise)

Purpose: enforce commit message style and pre-commit checks for maintainability and release accuracy.

**Non-interactive:** This assistant-run workflow reads secrets from environment variables (e.g. `JASYPT_PASSWORD`) and will not prompt the user for them; it will abort with an error if required env vars are missing.

Quick rules
- Format: `<type>(<scope>): <short summary>` (imperative, ≤50 chars)
- Types: feat, fix, perf, refactor, docs, test, ci, chore
- Short summary MUST summarize files changed (comma-separated scopes) — e.g. `fix(earnings,tests): return cached earnings data`

Pre-commit checklist (required)
1. Run unit tests: `./run-tests.sh --unit $JASYPT_PASSWORD` (requires `JASYPT_PASSWORD` exported in environment)
2. Add/modify unit tests covering your change
3. Ensure no compile warnings or unused imports
4. For minor bumps (`feat|perf|refactor`): update `VERSION` (use `./scripts/operational/bump-version.sh minor`) and stage it

Validator
- Run: `./scripts/operational/validate-commit-and-version.sh <commit-msg-file>`
- The validator enforces Conventional Commits and ensures `VERSION` is staged for minor bumps
- Configure hook: `git config core.hooksPath .githooks && chmod +x .githooks/commit-msg`

Release rule
- Only create a release if commit is a minor bump (per rules above). See `.github/prompts/release.prompt.md` for release steps.

Examples
- `feat(selection): add dynamic stock selection algorithm`
- `fix(bridge): handle null ticker names in telegram parser`

Notes
- Keep bodies short and explain why, not how
- Run the validator before committing to avoid blocked pushes

Assistant automation — AI-run semantics
- Invocation: When you say **"Run commit.prompt.md"**, **"Run commit"**, or explicitly: **"GitHub Copilot: run commit process"** (optionally with a message), the assistant will execute the full commit workflow non-interactively and without additional confirmation. The assistant will:
  1. Run unit tests: `./run-tests.sh --unit $JASYPT_PASSWORD`. Abort and report on any failing tests.
  2. Build a commit message file from your supplied header/body (if provided) or use the staged commit message; the assistant will write this file to `logs/commit-msg-<TS>.txt` (not `/tmp`) and then run the validator: `./scripts/operational/validate-commit-and-version.sh <commit-msg-file>`. Abort and report on validation errors.
  3. Commit staged changes with the validated message (if not already committed).
  4. Determine the commit type from the header. If it is a **minor bump** (`feat|perf|refactor`):
     - Run `./scripts/operational/bump-version.sh minor` to update and stage `VERSION` and create an annotated tag `v${NEW_VERSION}` (if not already present).
     - After the commit and tag(s) are created, **push the branch and all tags to `origin`**.
     - Automatically **invoke `release.prompt.md`** (see its Assistant automation) to attempt release creation (only if `GITHUB_TOKEN` is set; otherwise record `SKIPPED`).
  5. If not a minor bump: after committing, **push the branch to `origin`** immediately.
  6. Logging & audit: save test/validator/bump/tag/push output to `logs/run-commit-<TS>.log`. If a release was created or skipped, append `tag,commit,html_url_or_SKIPPED` to `logs/releases-summary.csv`. The assistant will redact secrets when summarizing results in chat.

Decision logic & non-interactive behavior
- The assistant will not ask follow-up questions during execution. It reads required secrets from the environment (e.g. `JASYPT_PASSWORD`) and will abort and return an actionable error message if a required env var is missing or a precondition fails (e.g., failing tests, validator failure, or network/Git errors). For missing `GITHUB_TOKEN`, it will skip release creation but continue other steps.
- The assistant will never print secrets to chat. Full logs containing unredacted API output are saved only under `logs/` and are referred to by path in the assistant report.

Audit output (on success)
- The assistant will return a concise summary in chat containing: commit SHA, new version (if bumped), tag name (if created), release `html_url` (if created), and paths to logs created.

Invocation examples
- "GitHub Copilot: Run commit.prompt.md with message 'feat(selection): add dynamic stock selection algorithm'"
- "Run commit" (use staged changes and any existing commit message)

END