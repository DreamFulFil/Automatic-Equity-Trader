#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <release-tag> <body-file>"
  echo "Example: $0 v0.92.0 scripts/operational/release-bodies/v0.92.0.md"
  exit 2
fi

RELEASE_TAG=$1
BODY_FILE=$2
REPO_OWNER=${REPO_OWNER:-DreamFulFil}
REPO_NAME=${REPO_NAME:-Automatic-Equity-Trader}

if [ ! -f "$BODY_FILE" ]; then
  echo "Body file not found: $BODY_FILE"
  exit 1
fi

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "GITHUB_TOKEN environment variable is not set. Export it and retry."
  exit 1
fi

# fetch release id
RELEASE_JSON=$(curl -sS -H "Accept: application/vnd.github+json" -H "Authorization: Bearer $GITHUB_TOKEN" -H "X-GitHub-Api-Version: 2022-11-28" "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/tags/${RELEASE_TAG}")
RELEASE_ID=$(echo "$RELEASE_JSON" | jq -r .id)
if [ -z "$RELEASE_ID" ] || [ "$RELEASE_ID" = "null" ]; then
  echo "Could not find release id for tag ${RELEASE_TAG}. Response was:"
  echo "$RELEASE_JSON"
  exit 1
fi

BODY=$(sed -e ':a' -e 'N' -e '$!ba' "$BODY_FILE")
BODY_ESCAPED=$(printf "%s" "$BODY" | jq -Rs .)

mkdir -p logs
LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
LOGFILE="logs/release-update-${LOG_TS}-${RELEASE_TAG}.log"

curl -sS -X PATCH \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/${RELEASE_ID}" \
  -d "{\"body\": ${BODY_ESCAPED}}" 2>&1 | tee "$LOGFILE"

# Check if the response contains the updated body
if grep -q "$(echo "$BODY" | head -n1 | sed 's/"/\"/g')" "$LOGFILE"; then
  echo "Release ${RELEASE_TAG} updated successfully. Log: $LOGFILE"
  exit 0
else
  echo "Failed to update release ${RELEASE_TAG}. See log: $LOGFILE"
  exit 1
fi
