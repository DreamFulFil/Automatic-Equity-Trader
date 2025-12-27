Developer setup: hooks, bumping, and releases

1. Enable local git hooks (recommended):

   # Set hooks path for this repo
   git config core.hooksPath .githooks
   chmod +x .githooks/commit-msg

   You can verify by running a test validation:
   ./scripts/operational/validate-commit-and-version.sh <(printf "docs: test hook\n")

2. Bumping versions:

   - Use the helper script to bump versions and create annotated tags for minor bumps:
     ./scripts/operational/bump-version.sh minor
   - For patch bumps, run:
     ./scripts/operational/bump-version.sh patch

3. Releases and release bodies

   - Prepare a release body using the template in `.github/prompts/release.prompt.md` and/or use `scripts/operational/release-bodies/` as examples.
   - Use `scripts/operational/update-release-body.sh <tag> <body-file>` to patch an existing release body.

4. CI enforcement

   - GitHub Actions runs `.github/workflows/validate-commit.yml` on push and pull_request to validate commit messages and ensure VERSION changes for minor-bump commits are present in the commit.
   - The audit script `scripts/operational/audit-release-history.sh` can be used locally to re-check the full history (commit headers, VERSION changes, and tags). To check releases against GitHub the script requires a `GITHUB_TOKEN` environment variable.

5. If the hook stops you from committing a minor bump, follow the printed guidance to run the bump helper and include the updated VERSION file in your commit.
