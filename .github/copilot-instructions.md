# Contributor & Automation Guidelines (concise)

Purpose: Provide strict, concise rules for contributors and automation agents so changes are auditable and reproducible.

## Quick Rules
- Follow Conventional Commits for commit messages (see `.github/prompts/commit.prompt.md`).
- All code must compile and tests must pass. Do not remove tests to make builds pass.
- Use `jenv exec` for Java/Maven command execution and ensure you set the local JDK first with `jenv local 21.0` (example: `jenv local 21.0 && jenv exec mvn compile`). Examples:
  - Test: `jenv exec mvn compile`
  - Clean: `jenv exec mvn clean`

## Shell & Tooling
- Development shell: **fish** on macOS — prefer fish syntax. **Do not** invoke commands via `bash -lc`; prefer fish-native commands and the repository's preferred tools instead.
  - Reference: consult the official fish shell documentation for examples and usage: https://fishshell.com/docs/2.4/index.html
- Preferred tools:
  - Find files: `fd`
  - Search text: `rg`
  - JSON: `jq`
  - YAML/XML: `yq`
  - Interactive: `fzf`
  - Code analysis: `ast-grep`

- If a task is difficult to express in shell or using the preferred tools, write a small Python helper script under `scripts/support/` (e.g., `scripts/support/my_task.py`). After the script has been used, **evaluate** whether to move it to `scripts/automation/`, `scripts/operational/`, or `scripts/setup/` for long-term use; if it was a one-off, delete it.

## Python venv
- Use `python/venv`. Activate with: `source python/venv/bin/activate`.
- Run Python commands via the venv binary: `python/venv/bin/python`, `python/venv/bin/pytest`.

## Commit & Release Workflow
- For every commit: run the checklist in `.github/prompts/commit.prompt.md` (unit tests + validator). ✅
- Only run `.github/prompts/release.prompt.md` when a release is required (minor bumps: `feat`, `perf`, `refactor`).
- Use `./scripts/operational/bump-version.sh` to manage `VERSION` bumps and tags.

## Enforced checks & hooks
- Use the local hook and validator to enforce commit format and VERSION updates: `./scripts/operational/validate-commit-and-version.sh <commit-msg-file>`.
- Enable hooks: `git config core.hooksPath .githooks && chmod +x .githooks/commit-msg`.

## Test Protection Policy
- Unit tests are mandatory for every change.
- Integration tests required for external interactions; mocks are permitted when heavy.
- Prefer framework-independent tests; avoid heavyweight context loading when possible.

## Notes
- Keep docs edits minimal (update `README.MD` only when necessary).
- Examples should prefer fish equivalents; where Bash is unavoidable, show a clear `bash -lc` invocation as a secondary option.