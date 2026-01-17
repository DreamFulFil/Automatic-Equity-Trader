#!/usr/bin/env bash
set -euo pipefail
TS=$(date -u +%Y%m%dT%H%M%SZ)
MSGFILE=logs/commit-msg-$TS.txt
LOGFILE=logs/run-commit-$TS.log
mkdir -p logs
printf "Run /commit workflow: %s\n" "$TS" > "$LOGFILE"
# detect changes (staged+unstaged+untracked)
CHANGES=$(git status --porcelain || true)
if [ -z "$CHANGES" ]; then
  printf "NO_CHANGES: nothing to commit\n" | tee -a "$LOGFILE"
  exit 0
fi
# collect file list
FILES=$(git diff --name-only; git ls-files --others --exclude-standard)
FIRSTFILE=$(echo "$FILES" | sed -n '1p')
# infer type
if echo "$FILES" | grep -qiE '\b(test|tests|Test)\b'; then
  TYPE=test
elif echo "$FILES" | grep -qi docs; then
  TYPE=docs
elif echo "$FILES" | grep -qiE 'src/main/java|python/|scripts/|pyproject|pom.xml'; then
  TYPE=fix
else
  TYPE=chore
fi
# infer scope (top-level directory or filename)
if [ -z "$FIRSTFILE" ]; then
  SCOPE=repo
else
  SCOPE=$(echo "$FIRSTFILE" | cut -d/ -f1)
  if [ -z "$SCOPE" ]; then SCOPE=repo; fi
fi
SUMMARY="$(basename "$FIRSTFILE") auto-commit"
HEADER="$TYPE($SCOPE): $SUMMARY"
# compose body
{
  echo "$HEADER"
  echo
  echo "Auto-generated commit of unstaged and staged changes. Files changed:"
  echo
  echo "$FILES" | sed 's/^/ - /'
  echo
  echo "Tests: run with provided JASYPT_PASSWORD or env var"
} > "$MSGFILE"
# run tests (use provided password)
JPW="${1:-}"
if [ -n "$JPW" ]; then
  echo "Running unit tests with provided JASYPT_PASSWORD (hidden)" | tee -a "$LOGFILE"
  ./run-tests.sh --unit "$JPW" 2>&1 | tee -a "$LOGFILE"
else
  echo "Running unit tests with env JASYPT_PASSWORD" | tee -a "$LOGFILE"
  ./run-tests.sh --unit "${JASYPT_PASSWORD:-}" 2>&1 | tee -a "$LOGFILE"
fi
# validator
echo "--- Validator ---" | tee -a "$LOGFILE"
./scripts/operational/validate-commit-and-version.sh "$MSGFILE" 2>&1 | tee -a "$LOGFILE"
# stage all changes
git add -A 2>&1 | tee -a "$LOGFILE"
# commit
git commit -F "$MSGFILE" 2>&1 | tee -a "$LOGFILE"
SHA=$(git rev-parse --verify HEAD)
echo "Commit SHA: $SHA" | tee -a "$LOGFILE"
# push
git push origin HEAD 2>&1 | tee -a "$LOGFILE"
echo "WROTE $MSGFILE and $LOGFILE"
cat "$MSGFILE"
