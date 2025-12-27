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

3. **Final Documentation:** Update `README.MD` concisely if needed. Do not add new files to `docs/`.

**Test Protection Policy**
* **Coverage:** Unit tests are mandatory for every Java or Python code change.
* **Integration Testing:** Required for interactions with external components. Mocks are permitted if the external interaction is resource-heavy.
* **Framework Independence:** Integration tests should be Spring-independent where possible. Avoid `@SpringBootTest`; prioritize **Mockito** for faster, decoupled execution.
* **Web Layer Testing:** For Java Controllers, prefer **Slice Testing** using `MockMvc`. The agent may exercise discretion to choose the most appropriate testing strategy based on complexity.
* **Deprecation Warning:** Explicitly forbid the use of `@MockBean`. Note that it is deprecated and slated for removal; use `@MockitoBean` instead.

**Claude Model Restriction:**
* If any Claude model is used, do NOT generate markdown summary reports or arbitrary markdown files unless explicitly instructed.
* Only update the main README.MD, and keep it concise and focused on essential changes.