---
agent: agent
---

# Commit prompt — Conventional Commits (concise)

Purpose: enforce commit message style and pre-commit checks for maintainability and release accuracy.

Quick rules
- Format: `<type>(<scope>): <short summary>` (imperative, ≤50 chars)
- Types: feat, fix, perf, refactor, docs, test, ci, chore
- Short summary MUST summarize files changed (comma-separated scopes) — e.g. `fix(earnings,tests): return cached earnings data`

Pre-commit checklist (required)
1. Run unit tests: `./run-tests.sh --unit <jasypt-password>`
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

END