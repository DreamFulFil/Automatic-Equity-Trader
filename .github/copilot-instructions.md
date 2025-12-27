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

1. **Mandatory Verification:** Run `./run-tests.sh` using the runtime-provided secret. **This is the primary and only test entry point; it executes the full suite (unit, integration, and e2e). A successful exit from this script is the absolute requirement for completion.**
2. Wait for all tests within the script to finish. If any part fails, you must fix the code and restart the verification.
3. **Final Documentation:** If successful, update `README.MD` concisely. Do not add new files to `docs/`.
4. `git add .`
5. `git commit` with a clear, descriptive message.
6. `git push` to the current branch.

**Claude Model Restriction:**
* If any Claude model is used, do NOT generate markdown summary reports or arbitrary markdown files unless explicitly instructed.
* Only update the main README.MD, and keep it concise and focused on essential changes.