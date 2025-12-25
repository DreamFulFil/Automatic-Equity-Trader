---
agent: agent
---
Commit instruction — Conventional Commits (v1.0.0)
Follow the Conventional Commits spec: https://www.conventionalcommits.org/en/v1.0.0/#specification

FORMAT
<type>(<scope>): <short summary>

<detailed description — optional; wrapped at ~72 chars>

<footer — optional; e.g., BREAKING CHANGE: description or Refs: #123>

ALLOWED TYPES
feat, fix, perf, refactor, docs, test, ci, chore

SCOPE
Optional single word describing area (e.g., telegram, selection, data)

SHORT SUMMARY
- Imperative, present tense
- ≤50 characters
- No trailing period

BODY
- Explain what and why (not how)
- Use bullet points for multiple items

FOOTER
- Reference issues (Refs: #123)
- Use `BREAKING CHANGE: <description>` for breaking changes

REPO-SPECIFIC VERSIONING RULES
- Minor bump (create tag & release): types `feat`, `perf`, `refactor`
- Patch bump: types `fix`, `chore`, `docs`, `ci`, `test`

REPOSITORY PROCEDURE (follow exactly)
1. Run unit tests before committing: `./run-tests.sh --unit <jasypt-password>`
2. For a minor bump (feat/perf/refactor):
   - Update the `VERSION` file with the new semantic version (keep <1.0.0). Example: 0.79.0 → 0.80.0.
     You can update it manually:
     ```bash
     echo "0.80.0" > VERSION
     ```
     or use the helper script to pick the correct new version:
     ```bash
     ./scripts/bump-version.sh minor
     ```
   - Commit the `VERSION` change and any code changes, then create an annotated tag: `git tag -a "v${NEW_VERSION}" -m "Release v${NEW_VERSION}"`
   - Push commits and tags to origin
   - Create a release if required by running the prompt [release.prompt.md](./release.prompt.md)
3. For a patch bump: update the `VERSION` file to the new patch version (e.g., 0.79.0 → 0.79.1), commit, push (no tag). Example:
   ```bash
   echo "0.79.1" > VERSION
   ```
   Or use the helper script:
   ```bash
   ./scripts/bump-version.sh patch
   ```

PRE-COMMIT CHECKLIST
- Add/modify unit tests covering the change
- Run `./run-tests.sh --unit <jasypt-password>` and fix failures
- If all tests pass, push commits and (for minor bumps) annotated tags to origin
- Ensure no compile warnings or unused imports
- Update the `VERSION` file contents when required (e.g., `echo "0.80.0" > VERSION`), or run `./scripts/bump-version.sh <minor|patch>`

EXAMPLES
- feat(selection): add dynamic stock selection algorithm
- fix: handle null ticker names in telegram parser
- docs: add usage note for nightly cron job
- refactor: simplify portfolio valuation logic
- test: add unit tests for earnings blackout dates

HELPFUL SCRIPTS & COMMANDS
- Run full test suite: `./run-tests.sh <jasypt-password>`
- Bump version helper: `./scripts/bump-version.sh <commit-type>`

NOTES
- Keep commit messages concise and focused — the summary is the release note title.
- Always follow the Conventional Commits spec for structure and semantic meaning.

END