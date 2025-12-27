**General rules (must follow):**
* Always read and follow `.github/copilot-instructions.md`
* Clean code principles should be followed as much as possible.
* Always use `jenv` for Java and Maven commands; never invoke `java`, `javac`, or `mvn` directly.
* **Documentation Integrity:** Do not generate or modify files in the `docs/` directory unless explicitly instructed by the user. All docs must be in proper subdirectories (usage/, reference/, development/), NOT directly under docs/.
* **Markdown Restriction:** Do not generate arbitrary markdown files. The only permitted markdown edits are to `README.MD` during the completion workflow.
* **Telegram Message Formatting:** NEVER use `\n` escape sequences in Telegram messages. Use actual newlines or format strings properly. Users see literal `\n` which is unacceptable.
* **Commit Message Format:** Follow Conventional Commits specification:
  - Format: `<type>: <description>`
  - Types: `feat` (new feature), `fix` (bug fix), `chore` (maintenance), `refactor` (code restructure), `docs` (documentation), `test` (testing), `ci` (CI/CD), `perf` (performance)
  - Example: `feat: add dynamic stock selection`, `fix: resolve NULL stock names in Telegram`
  - Keep descriptions concise and imperative mood (e.g., "add" not "added")
* PostgreSQL is running in Docker. If database inspection is needed, write a temporary Python script under `/tmp`, or use `docker exec`.
* All code must compile, all existing tests must pass, and all new code must be covered by tests.
* Never remove tests to make builds pass.

* Use `exec` exclusively for command execution.
* If standard shell tools or built-in capabilities are insufficient to complete a task, write and execute custom Python scripts in the `/tmp` directory to bridge the gap.

**Java rules:**
* Always use Lombok `@Data`, `@AllArgsConstructor`, and `@NoArgsConstructor` instead of explicit getters, setters, or self-written constructors.
* Always check that there are no compile warnings (e.g., unused imports, unused methods, etc.) before committing.

## Shell Tools Usage Guidelines
⚠️ **IMPORTANT**: Use the following specialized tools instead of traditional Unix commands: (Install if missing)
| Task Type | Must Use | Do Not Use |
|-----------|----------|------------|
| Find Files | `fd` | `find`, `ls -R` |
| Search Text | `rg` (ripgrep) | `grep`, `ag` |
| Analyze Code Structure | `ast-grep` | `grep`, `sed` |
| Interactive Selection | `fzf` | Manual filtering |
| Process JSON | `jq` | `python -m json.tool` |
| Process YAML/XML | `yq` | Manual parsing |

## Python Virtual Environment Usage
* Always run all Python commands (including scripts, tests, and package installs) using the Python virtual environment located at `python/venv`.
* Activate the venv with `source python/venv/bin/activate` before running any Python-related command.
* If running Python scripts or modules, use the full venv path (e.g., `python/venv/bin/python script.py`).

**File reading discipline:**

* Read only the sections needed to make changes.
* Avoid reading entire files unless full context is required.

**Completion workflow (must be followed):**

1. **Mandatory Verification:**
   - During development, always run only unit tests: `./run-tests.sh --unit <jasypt-password>`
   - Before any commit or push, you must run the full test suite: `./run-tests.sh <jasypt-password>` and all tests must pass. **This is the primary and only test entry point; it executes the full suite (unit, integration, and e2e). A successful exit from this script is the absolute requirement for completion.**

2. Wait for all tests within the script to finish. If any part fails, you must fix the code and restart the verification.

3. **VERSION File Management (MANDATORY - https://semver.org/):**
   
   **Step 3a - Read current version:**
   ```bash
   CURRENT_VERSION=$(cat VERSION)  # e.g., "0.79.0"
   ```
   
   **Step 3b - Calculate new version based on commit type:**
   - **Minor bump (+0.1.0):** For `feat:`, `perf:`, `refactor:` commits
     - Example: `0.79.0` → `0.80.0` (reset patch to 0)
   - **Patch bump (+0.0.1):** For `fix:`, `chore:`, `docs:`, `ci:`, `test:` commits
     - Example: `0.79.0` → `0.79.1`
   - **Version cap:** NEVER reach 1.0.0 (stay in 0.x.y range)
   
   **Step 3c - Update VERSION file:**
   ```bash
   echo "0.80.0" > VERSION
   ```

4. **Git Tag Creation (Minor versions ONLY):**
   
   **IF** commit type is `feat:`, `perf:`, or `refactor:` (minor bump):
   ```bash
   NEW_VERSION=$(cat VERSION)  # e.g., "0.80.0"
   git tag -a "v${NEW_VERSION}" -m "Release v${NEW_VERSION}"
   ```
   
   **ELSE** (patch bump): Skip tag creation

5. **GitHub Release Creation (Minor versions ONLY):**
   
   **IF** commit type is `feat:`, `perf:`, or `refactor:` AND tag was created:
   
   **Use curl (REQUIRED - NOT gh CLI):**
   ```bash
   # Extract commit message without type prefix
   COMMIT_MSG=$(git log -1 --pretty=%B | sed 's/^[^:]*: //')
   
   # Get files changed in this commit
   FILES_CHANGED=$(git diff-tree --no-commit-id --name-only -r HEAD)
   
   # Create a concise summary of changes (at least 3 items)
   # Example: "Updated 5 Java files", "Modified 2 Python scripts", "Enhanced test coverage"
   
   # Build release body with proper format
   BODY="# Release v${NEW_VERSION}

## Summary
- ${COMMIT_MSG}

## Changes
- [Change summary 1]
- [Change summary 2]
- [Change summary 3]"
   
   # Escape body for JSON
   BODY_ESCAPED=$(echo "$BODY" | jq -Rs .)
   
   # Create release using curl
   curl -L -X POST \
     -H "Accept: application/vnd.github+json" \
     -H "Authorization: Bearer ${GITHUB_TOKEN}" \
     -H "X-GitHub-Api-Version: 2022-11-28" \
     https://api.github.com/repos/DreamFulFil/Automatic-Equity-Trader/releases \
     -d "{
       \"tag_name\": \"v${NEW_VERSION}\",
       \"target_commitish\": \"main\",
       \"name\": \"v${NEW_VERSION}\",
       \"body\": ${BODY_ESCAPED},
       \"draft\": false,
       \"prerelease\": false,
       \"generate_release_notes\": false
     }"
   ```
   
   **Release Body Format Requirements:**
   - Must start with `# Release vX.Y.Z`
   - Must have `## Summary` section with clean commit message (text after colon)
   - Must have `## Changes` section with at least 3 bullet points
   - Changes should summarize file modifications concisely (e.g., "Updated 3 Java files", "Modified configuration", "Enhanced tests")
   
   **ELSE** (patch bump): Skip GitHub release creation

6. **Final Documentation:** Update `README.MD` concisely if needed. Do not add new files to `docs/`.

7. **Commit & Push (with VERSION file):**
   ```bash
   git add VERSION <other-changed-files>
   git commit -m "feat: your commit message"  # Use correct Conventional Commits type
   git push origin main
   
   # If tag was created (minor version only):
   git push origin --tags
   ```

**Quick Reference - Version Bump Decision Tree:**
```
Commit type?
├─ feat/perf/refactor → Minor bump (0.79.0 → 0.80.0) → CREATE TAG → CREATE RELEASE
└─ fix/chore/docs/ci/test → Patch bump (0.79.0 → 0.79.1) → NO TAG → NO RELEASE
```

**Helper Script (Optional):**
```bash
./scripts/bump-version.sh <commit-type>  # Automates steps 3-5
```

**Test Protection Policy**

* **Coverage:** Unit tests are mandatory for every Java or Python code change.
* **Integration Testing:** Required for interactions with external components. Mocks are permitted if the external interaction is resource-heavy.
* **Framework Independence:** Integration tests should be Spring-independent where possible. Avoid `@SpringBootTest`; prioritize **Mockito** for faster, decoupled execution.
* **Web Layer Testing:** For Java Controllers, prefer **Slice Testing** using `MockMvc`. The agent may exercise discretion to choose the most appropriate testing strategy based on complexity.
* **Deprecation Warning:** Explicitly forbid the use of `@MockitoBean`. Note that it is deprecated and slated for removal; use `@MockitoBean` instead.

**Claude Model Restriction:**
* If any Claude model is used, do NOT generate markdown summary reports or arbitrary markdown files unless explicitly instructed.
* Only update the main README.MD, and keep it concise and focused on essential changes.