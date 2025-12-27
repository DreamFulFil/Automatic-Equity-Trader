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
   
   **Option A - Using gh CLI (preferred):**
   ```bash
   # Extract commit message without type prefix
   COMMIT_MSG=$(git log -1 --pretty=%B | sed 's/^[^:]*: //')
   
   # Determine section header
   case "$TYPE" in
     feat) SECTION="Features" ;;
     perf) SECTION="Performance" ;;
     refactor) SECTION="Refactoring" ;;
   esac
   
   # Create release
   gh release create "v${NEW_VERSION}" \
     --title "v${NEW_VERSION}" \
     --notes "## ${SECTION}\n\n- ${COMMIT_MSG}"
   ```
   
   **Option B - Using curl:**
   ```bash
   curl -X POST \
     -H "Authorization: Bearer ${GITHUB_TOKEN}" \
     -H "Accept: application/vnd.github+json" \
     -d "{\"tag_name\":\"v${NEW_VERSION}\",\"name\":\"v${NEW_VERSION}\",\"body\":\"## ${SECTION}\\n\\n- ${COMMIT_MSG}\"}" \
     https://api.github.com/repos/DreamFulFil/Automatic-Equity-Trader/releases
   ```
   
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
* **Deprecation Warning:** Explicitly forbid the use of `@MockBean`. Note that it is deprecated and slated for removal; use standard Mockito annotations instead.

**Claude Model Restriction:**
* If any Claude model is used, do NOT generate markdown summary reports or arbitrary markdown files unless explicitly instructed.
* Only update the main README.MD, and keep it concise and focused on essential changes.

**GraalVM Native Build Guidelines:**

* **Prerequisites (One-Time Setup):**
  ```bash
  # Install GraalVM 21 via SDKMAN (recommended)
  curl -s "https://get.sdkman.io" | bash
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk install java 21-graal
  sdk use java 21-graal
  
  # Verify installation
  java -version  # Should show "GraalVM"
  native-image --version  # Should work without error
  
  # Alternative: Homebrew (macOS)
  brew install --cask graalvm-jdk21
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-jdk-21/Contents/Home
  export PATH=$JAVA_HOME/bin:$PATH
  ```

* **When to Use Native Compilation:**
  - Production deployments requiring sub-second startup times
  - Memory-constrained environments (native images use ~3-5x less memory)
  - CI/CD pipelines validating AOT compatibility
  - **NEVER** during active development (5-10 minute build time)

* **Build Commands:**
  ```bash
  # Standard JAR build (fast, for development)
  ./mvnw clean package -DskipTests
  
  # Native executable build (slow, for production)
  # Requires: All tests must pass first
  ./run-tests.sh --unit dreamfulfil && \
  ./mvnw clean -Pnative native:compile -DskipTests
  
  # Native test validation (validates closed-world assumption)
  ./mvnw -PnativeTest test
  ```

* **Artifacts Produced:**
  - `target/auto-equity-trader.jar` - Standard Spring Boot JAR (always built)
  - `target/auto-equity-trader` - Native executable (only with `-Pnative`)

* **Startup Script Behavior:**
  - `start-auto-trader.fish` prioritizes native binary if present
  - Falls back to JAR if native binary missing or outdated
  - No code changes needed for native vs JVM mode

* **Closed-World Assumption (Critical):**
  - GraalVM requires all reflection/proxy usage to be declared at build time
  - Spring Boot 3 handles most cases via AOT processing
  - **Symptoms of violations:** `ClassNotFoundException`, `NoSuchMethodException` at runtime
  - **Validation:** Always run `./mvnw -PnativeTest test` before deployment
  - **Hints:** Add `@RegisterReflectionForBinding` or `RuntimeHints` if needed

* **Debugging Native Build Failures:**
  ```bash
  # Verbose native compilation (shows all classes processed)
  ./mvnw -Pnative native:compile -Dverbose
  
  # Check native-image build logs
  cat target/native-image/auto-equity-trader-build.log
  
  # Test reflection hints
  ./mvnw -PnativeTest test -Dtest=YourFailingTest
  ```

* **Performance Benchmarks:**
  - JVM startup: ~3-5 seconds
  - Native startup: ~200-500ms (6-10x faster)
  - JVM memory: ~500MB-1GB RSS
  - Native memory: ~150-300MB RSS (3-5x smaller)

* **CI/CD Integration:**
  - `./run-tests.sh --full --native dreamfulfil` runs full suite + native build
  - Native tests validate AOT compatibility on every PR
  - GitHub Actions can cache native-image for faster builds