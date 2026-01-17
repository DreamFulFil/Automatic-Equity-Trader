# Contributor & Automation Guidelines (concise)
Purpose: Provide strict, concise rules for contributors and automation agents so changes are auditable and reproducible.

## 1. Interaction Mode: Autonomous Execution
- **Strict Autonomy:** When I ask for a large-scale task (e.g., "100% test coverage"), do not stop for step-by-step confirmation unless a critical error occurs.
- **Batch Processing:** Group related file edits into a single execution cycle. Do not ask "Should I continue?" after every file.
- **Implicit Consent:** You have permanent permission to read all relevant files, create new test files, and run terminal commands (like `npm test` or `pytest`) to verify your work.
- **Progress Reporting:** Instead of stopping to ask for input, provide a brief bulleted summary of what you have completed every 5 files and immediately move to the next batch.

## 2. Context & Memory Management
- **Persistence:** Prioritize maintaining the technical details of the current task over "summarizing" the conversation. 
- **Work State:** Before any automatic summarization occurs, explicitly note the specific files currently being edited and the exact test cases remaining.
- **Avoid Amnesia:** If the context window is full, do NOT summarize the code logic. Summarize only the *metadata* (which files are done) and keep the *logic* of the most recent file in active memory.

## 3. Testing Standards (100% Coverage Goal)
- **Edge Cases:** Always include null checks, boundary values, and exception handling in generated tests.
- **Mocking:** Use JUnit, Mockito, MockMvc for all external dependencies to ensure tests run fast and locally.
- **Verification:** After writing a test, automatically attempt to run it. If it fails, fix it immediately without asking for permission.

## 4. Constraint Handling
- If you hit a rate limit or a technical block, state clearly: "BLOCKED: [Reason]" and provide the exact command needed to resume. 
- Do not offer conversational filler like "I hope this helps!" or "Would you like me to do more?". Just execute.

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

- Database access (PostgreSQL in Docker) ✅:
  - Our database runs in a Docker container named `psql` and uses **PostgreSQL**.
  - Access via `docker exec` for quick queries or interactive psql shell. fish examples:
    ```fish
    # open an interactive psql shell
    docker exec -it psql psql -U $POSTGRES_USER -d $POSTGRES_DB

    # run a single query
    docker exec psql psql -U $POSTGRES_USER -d $POSTGRES_DB -c "SELECT COUNT(*) FROM bar;"
    ```
  - Or write a Python helper that reads `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` from the environment and connects using `psycopg2` (place helpers under `scripts/support/`):
    ```python
    # scripts/support/psql_client.py
    #!/usr/bin/env python3
    import os
    import psycopg2

    db = os.environ['POSTGRES_DB']
    user = os.environ['POSTGRES_USER']
    pw = os.environ['POSTGRES_PASSWORD']

    # If running outside the container, ensure the host/port are accessible or run inside the container via `docker exec`.
    conn = psycopg2.connect(dbname=db, user=user, password=pw, host='localhost')
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM bar;")
        print(cur.fetchone())
    ```
  - Notes:
    - If you run the Python script outside the `psql` container, either run it inside the container (`docker exec -i psql python /path/to/script.py`) or configure access to the database host/port. For most one-off tasks we prefer `docker exec` to avoid host networking issues.
    - Ensure `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are set in your environment (e.g. `set -x POSTGRES_DB ...`).

- If a task is difficult to express in shell or using the preferred tools, write a small Python helper script under `scripts/support/` (e.g., `scripts/support/my_task.py`). After the script has been used, **evaluate** whether to move it to `scripts/automation/`, `scripts/operational/`, or `scripts/setup/` for long-term use; if it was a one-off, delete it.

## Python venv
- Use `python/venv`. Activate with: `source python/venv/bin/activate`.
- Run Python commands via the venv binary: `python/venv/bin/python`, `python/venv/bin/pytest`.

## Commit & Release Workflow
- For every commit: run the checklist in `.github/prompts/commit.prompt.md` (unit tests + validator). ✅
- **Before committing and running the full test suite**, rebuild the Java artifact to ensure the build is up-to-date: `jenv exec mvn clean install -DskipTests`.
- The AI assistant **must NOT** automatically commit, tag, or push changes without explicit user approval. The assistant may prepare commit messages, run validations, and propose the exact git commands, but it must always request and receive confirmation from a human operator before performing `git commit`, `git tag`, or `git push` operations.
- The assistant will not invoke `.github/prompts/commit.prompt.md` or execute any part of the commit workflow unless explicitly asked by the user and the user confirms execution; commits and pushes must be manually triggered by the user after reviewing test results and the proposed commit message.
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