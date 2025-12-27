**General rules (must follow):**
* Always read and follow `.github/copilot-instructions.md`
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

**Tool usage rules (strict):**

* Use real tools when available; never simulate outputs.
* If you say you will run a command or inspect a file, actually do it.
* Prefer specialized tools over generic shell commands (`fd`, `rg`, `ast-grep`, `jq`, `yq`).

**File reading discipline:**

* Read only the sections needed to make changes.
* Avoid reading entire files unless full context is required.

**Completion workflow (must be followed):**

1. **Mandatory Verification:**
   - During development, always run only unit tests: `./run-tests.sh --unit <jasypt-password>`
   - Before any commit or push, you must run the full test suite: `./run-tests.sh <jasypt-password>` and all tests must pass. **This is the primary and only test entry point; it executes the full suite (unit, integration, and e2e). A successful exit from this script is the absolute requirement for completion.**
2. Wait for all tests within the script to finish. If any part fails, you must fix the code and restart the verification.
3. **Semantic Versioning (Automatic):**
   - After tests pass, update the `VERSION` file according to the commit type:
     - **Minor bump (+0.1.0):** `feat:`, `perf:`, `refactor:` commits
     - **Patch bump (+0.0.1):** `fix:`, `chore:`, `docs:`, `ci:`, `test:` commits
   - Version must remain in `0.x.y` range (never reach 1.0.0)
   - Example: If current VERSION is `0.79.0` and commit is `feat: add new feature`, update to `0.80.0`
4. **Git Tag Creation (Minor versions only):**
   - If the bump is a minor version (feat, perf, refactor), create an annotated git tag:
     ```bash
     git tag -a "v0.X.0" -m "Release v0.X.0"
     ```
   - Patch versions do NOT get tags (only tracked in VERSION file)
5. **GitHub Release Creation (Minor versions only):**
   - For minor version bumps, create a GitHub release:
     ```bash
     # Generate release body from commit message (no hashes, clean markdown)
     # Use GitHub API or gh CLI
     gh release create "v0.X.0" --title "v0.X.0" --notes "## <Type>\n\n- <commit message>"
     ```
   - Release body format:
     ```markdown
     ## Features (or Performance/Refactoring)
     
     - <commit description>
     ```
6. **Final Documentation:** Update `README.MD` concisely if needed. Do not add new files to `docs/`.
7. **Commit & Push:**
   - `git add .` (includes VERSION file)
   - `git commit` with a clear, descriptive Conventional Commits message
   - `git push origin <branch>`
   - `git push origin --tags` (if tag was created)

**Test Protection Policy**

* **Coverage:** Unit tests are mandatory for every Java or Python code change.
* **Integration Testing:** Required for interactions with external components. Mocks are permitted if the external interaction is resource-heavy.
* **Framework Independence:** Integration tests should be Spring-independent where possible. Avoid `@SpringBootTest`; prioritize **Mockito** for faster, decoupled execution.
* **Web Layer Testing:** For Java Controllers, prefer **Slice Testing** using `MockMvc`. The agent may exercise discretion to choose the most appropriate testing strategy based on complexity.
* **Deprecation Warning:** Explicitly forbid the use of `@MockBean`. Note that it is deprecated and slated for removal; use standard Mockito annotations instead.

**Claude Model Restriction:**
* If any Claude model is used, do NOT generate markdown summary reports or arbitrary markdown files unless explicitly instructed.
* Only update the main README.MD, and keep it concise and focused on essential changes.