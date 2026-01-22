---
agent: agent
---

# Commit prompt — Conventional Commits (concise)

Purpose: enforce Conventional Commits and required pre-commit checks.

Format: <type>(<scope>): <short summary> (imperative, ≤50 chars)
Types: feat, fix, perf, refactor, docs, test, ci, chore

- Required checklist:
- Run unit tests: `./run-tests.sh --unit $JASYPT_PASSWORD` (or pass `JASYPT_PASSWORD` when invoking the assistant with `/commit <JASYPT_PASSWORD>`)
- Add/modify unit tests for your change
- No compile warnings or unused imports
- For `feat|perf|refactor`: update and stage `VERSION` (`./scripts/operational/bump-version.sh minor`)

Validator:
- Run `./scripts/operational/validate-commit-and-version.sh <commit-msg-file>`

A I automation (invocation examples below):
- The assistant will run unit tests, generate a Conventional Commit message that summarizes the staged changes and write it to `logs/commit-msg-<TS>.txt`, automatically run the commit validator, and commit staged changes if validation passes. Validation runs non-interactively; if validation fails the assistant aborts the commit and saves validation logs to `logs/run-commit-<TS>.log`.
- If a `JASYPT_PASSWORD` is provided via `/commit`, the assistant will use it for the test command instead of the environment variable (the password is never echoed or stored in chat).
- If the commit is a minor bump (`feat|perf|refactor`): bump `VERSION`, create an annotated tag, push branch+tags, and attempt a release (requires `GITHUB_TOKEN`).
- Otherwise: push the branch.
- All outputs are saved to `logs/run-commit-<TS>.log` and `logs/releases-summary.csv` (release entries may be `SKIPPED`).

Automatic `/commit` behavior:
- Invocation: `GitHub Copilot: /commit [JASYPT_PASSWORD] ["optional commit header or message"]`
- If no commit header/message is supplied, the assistant will generate a Conventional Commit header and a short summary that describes the staged changes (derived from `git diff --staged`). If a commit message is supplied, the assistant will verify it adequately summarizes the staged changes; if it does not, the assistant will replace it with a generated summary.
- The assistant will then run the following steps non-interactively (it will not ask for permission to run the validator or proceed):
  - Run unit tests (use provided `JASYPT_PASSWORD` if given, else use env var)
  - Run the commit message validator automatically on the generated or supplied message and abort if validation fails; validation errors and logs will be saved to `logs/run-commit-<TS>.log`
  - Stage all unstaged changes (`git add -A`)
  - Commit using the generated or validated message file (saved as `logs/commit-msg-<TS>.txt`)
  - Push the branch to `origin`
- For minor bump commits (`feat|perf|refactor`) the assistant will also bump `VERSION`, create an annotated tag, push tags, and attempt a release (requires `GITHUB_TOKEN`).
- The assistant will never echo secrets in chat; all outputs are saved in `logs/run-commit-<TS>.log` and the commit message is saved to `logs/commit-msg-<TS>.txt`.

Decision rules:
- Non-interactive: the assistant prefers a `JASYPT_PASSWORD` provided via `/commit` (if present), otherwise it looks for the `JASYPT_PASSWORD` env var. If neither is available, the process aborts.
- Secrets are never printed to chat; full logs are stored under `logs/`.

Examples:
- feat(selection): add dynamic stock selection algorithm
- fix(bridge): handle null ticker names in telegram parser

Invocation examples:
- GitHub Copilot: Run commit.prompt.md with message 'feat(selection): add dynamic stock selection algorithm'
- GitHub Copilot: /commit <JASYPT_PASSWORD> Run commit.prompt.md with message 'feat(selection): add dynamic stock selection algorithm'
- Run commit (uses env `JASYPT_PASSWORD` if set)

END