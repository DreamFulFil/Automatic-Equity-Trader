#!/usr/bin/env bash
set -euo pipefail

# Continue a partially-applied version rewrite. Use on the working rewrite branch.
# Expects a dry-run CSV at logs/version-rewrite-dryrun-*.csv
# Keeps progress state in logs/version-rewrite-processed-<TS>.txt and mapping in logs/version-rewrite-proposed-changes-<TS>.csv

DRY=$(ls -1 logs/version-rewrite-dryrun-*.csv 2>/dev/null | tail -n1)
if [ -z "$DRY" ]; then
  echo "No dry-run CSV found in logs/ (version-rewrite-dryrun-*.csv)." >&2
  exit 2
fi
TS=$(date -u +%Y%m%dT%H%M%SZ)
PROG=logs/version-rewrite-processed-${TS}.txt
PROPOSED=logs/version-rewrite-proposed-changes-${TS}.csv
LOG=logs/version-rewrite-continue-${TS}.log

echo "commit,type,header,proposed_version,tag,new_sha" > "$PROPOSED"

# If there's an older processed file, seed from it
OLDPROG=$(ls -1 logs/version-rewrite-processed-*.txt 2>/dev/null | tail -n1 || true)
if [ -n "$OLDPROG" ]; then
  cp "$OLDPROG" "$PROG"
fi

# If previous proposed exists, append its lines to new PROPOSED (except header)
OLDP=$(ls -1 logs/version-rewrite-proposed-changes-*.csv 2>/dev/null | tail -n1 || true)
if [ -n "$OLDP" ]; then
  tail -n +2 "$OLDP" >> "$PROPOSED" || true
fi

echo "Continuing from dry-run: $DRY" | tee "$LOG"

# Helper to check if commit processed
is_processed(){
  local c="$1"
  if [ -f "$PROG" ] && rg -qx "$c" "$PROG" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

# Iterate dry-run rows
tail -n +2 "$DRY" | while IFS=',' read -r commit type header touched current proposed tag; do
  if is_processed "$commit"; then
    echo "Skipping already processed $commit" | tee -a "$LOG"
    continue
  fi

  echo "Processing $commit: $header" | tee -a "$LOG"

  # Backup any untracked files so cherry-pick won't fail with 'would be overwritten'
  untracked=$(git ls-files --others --exclude-standard || true)
  if [ -n "$untracked" ]; then      # Don't move known large, rebuildable directories (venv, __pycache__, .m2, target, node_modules, etc.)
      EXCLUDE_REGEX='(^python/venv(/|$)|^venv(/|$)|^\.venv(/|$)|(^|/)(__pycache__)(/|$)|^\.python-version$|^\.java-version$|^target(/|$)|^\.m2(/|$)|^node_modules(/|$))'
      filtered=$(printf '%s\n' "$untracked" | grep -E -v "$EXCLUDE_REGEX" || true)
      if [ -z "$filtered" ]; then
        echo "No movable untracked files (only excluded patterns found); skipping backup move." | tee -a "$LOG"
        # Preserve original behavior of "if [ -n \"$untracked\" ]" but skip moving if all were excluded
        untracked=""
      else
        untracked="$filtered"
      fi    BACKUP_DIR="logs/rewrite-backups-${TS}"
    mkdir -p "$BACKUP_DIR"
    echo "Found untracked files; moving to $BACKUP_DIR to avoid cherry-pick conflicts" | tee -a "$LOG"
    echo "$untracked" | while read -r uf; do
      mkdir -p "$(dirname "$BACKUP_DIR/$uf")" || true
      mv "$uf" "$BACKUP_DIR/$uf" || true
      echo "Moved $uf -> $BACKUP_DIR/$uf" | tee -a "$LOG"
    done
  fi

  # If an equivalent commit already exists in this branch (same header), mark as processed and skip
  # Look for a matching commit in the current history by header
  existing=$(git log --all --grep -F -n 1 --pretty=%H --regexp-ignore-case -- "$header" 2>/dev/null | sed -n '1p' || true)
  if [ -n "$existing" ]; then
    echo "Found existing commit matching header: $existing; marking as processed and skipping." | tee -a "$LOG"
    echo "$commit,$type,$header,,,$existing" >> "$PROPOSED"
    echo "$commit" >> "$PROG"
    continue
  fi

  # Attempt cherry-pick
  if ! git cherry-pick --no-commit "$commit"; then
    echo "Cherry-pick reported conflicts for $commit; attempting automatic resolution by preferring cherry-picked changes (theirs)." | tee -a "$LOG"
    files=$(git ls-files -u | awk '{print $4}' | sort -u || true)
    if [ -n "$files" ]; then
      echo "$files" | while read -r f; do
        echo "Resolving conflict for $f: choosing theirs (cherry-pick)" | tee -a "$LOG"
        git checkout --theirs -- "$f" || git checkout --ours -- "$f" || true
        git add "$f"
      done
    else
      echo "No unmerged files detected; aborting." | tee -a "$LOG"
      git cherry-pick --abort || true
      echo "FAIL,$commit,conflict-no-files" >> "$LOG"
      exit 1
    fi
  fi

  # If a proposed version exists, update VERSION
  if [ -n "$proposed" ]; then
    echo "Setting VERSION to $proposed" | tee -a "$LOG"
    echo "$proposed" > VERSION
    git add VERSION || true
  fi

  # If nothing is staged (no change), check if equivalent commit already exists and skip
  staged=$(git diff --cached --name-only || true)
  if [ -z "$staged" ]; then
    echo "No staged changes after applying commit; checking if equivalent commit exists." | tee -a "$LOG"
    # Try exact header match first
    existing=$(git log --all --grep -F -n 1 --pretty=%H --regexp-ignore-case -- "$header" 2>/dev/null | sed -n '1p' || true)
    if [ -z "$existing" ]; then
      # Fallback: strip leading 'type:' prefix and search shorter message
      clean_header=$(echo "$header" | sed 's/^[^:]*: //' | sed 's/\"//g' | sed 's/\s\+/ /g')
      existing=$(git log --all --grep -F -n 1 --pretty=%H --regexp-ignore-case -- "$clean_header" 2>/dev/null | sed -n '1p' || true)
    fi
    if [ -n "$existing" ]; then
      echo "Found existing commit matching header: $existing; marking as processed and skipping." | tee -a "$LOG"
      echo "$commit,$type,$header,$proposed,$tag,$existing" >> "$PROPOSED"
      echo "$commit" >> "$PROG"
      # abort attempted cherry-pick state if any
      git reset --hard HEAD >/dev/null 2>&1 || true
      continue
    else
      # Additional fallback: if HEAD commit message equals original full message, consider processed
      fullmsg=$(git log -1 --pretty=%B "$commit")
      headmsg=$(git log -1 --pretty=%B HEAD 2>/dev/null || true)
      if [ "$fullmsg" = "$headmsg" ]; then
        existing=$(git rev-parse --verify HEAD)
        echo "HEAD message equals original commit message; marking HEAD ($existing) as processed." | tee -a "$LOG"
        echo "$commit,$type,$header,$proposed,$tag,$existing" >> "$PROPOSED"
        echo "$commit" >> "$PROG"
        git reset --hard HEAD >/dev/null 2>&1 || true
        continue
      fi

      echo "No existing commit found but nothing staged — aborting to avoid empty commit." | tee -a "$LOG"
      git reset --hard HEAD >/dev/null 2>&1 || true
      exit 1
    fi
  fi

  # Commit with original author and date
  author=$(git --no-pager show -s --format='%an <%ae>' "$commit")
  date=$(git --no-pager show -s --format='%aD' "$commit")
  fullmsg=$(git log -1 --pretty=%B "$commit")
  GIT_COMMITTER_DATE="$date" git commit --author="$author" -m "$fullmsg"
  new_sha=$(git rev-parse --verify HEAD)

  # If there's a tag specified, create annotated tag pointing to this new commit
  if [ -n "$tag" ]; then
    if git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null; then
      echo "Tag $tag exists locally; updating to point to $new_sha (local force)." | tee -a "$LOG"
      git tag -f -a "$tag" -m "Release $tag" "$new_sha"
    else
      echo "Creating annotated tag $tag -> $new_sha" | tee -a "$LOG"
      git tag -a "$tag" -m "Release $tag" "$new_sha"
    fi
  fi

  # Record progress
  echo "$commit" >> "$PROG"
  echo "$commit,$type,$header,$proposed,$tag,$new_sha" >> "$PROPOSED"
  echo "Processed $commit -> $new_sha" | tee -a "$LOG"

done

# Run unit tests
echo "Running unit tests: ./run-tests.sh --unit dreamfulfil" | tee -a "$LOG"
./run-tests.sh --unit dreamfulfil 2>&1 | tee -a "$LOG"

echo "Continue run finished. Log: $LOG" | tee -a "$LOG"
exit 0
