#!/bin/bash
# Semantic Version Bump Script (moved to scripts/operational)
# Usage: ./scripts/operational/bump-version.sh <commit-type>

set -e

COMMIT_TYPE="${1:-}"
VERSION_FILE="VERSION"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

if [ -z "$COMMIT_TYPE" ]; then
    echo "ERROR: Commit type required"
    echo "Usage: $0 <commit-type>"
    echo "Types: feat, fix, perf, refactor, chore, docs, ci, test"
    exit 1
fi

if [ ! -f "$VERSION_FILE" ]; then
    echo "ERROR: VERSION file not found"
    exit 1
fi

# Read current version
CURRENT_VERSION=$(cat "$VERSION_FILE")
echo "Current version: $CURRENT_VERSION"

# Parse version components
if [[ $CURRENT_VERSION =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    MAJOR="${BASH_REMATCH[1]}"
    MINOR="${BASH_REMATCH[2]}"
    PATCH="${BASH_REMATCH[3]}"
else
    echo "ERROR: Invalid version format in VERSION file"
    exit 1
fi

# Determine bump type
case "$COMMIT_TYPE" in
    feat|perf|refactor)
        BUMP_TYPE="minor"
        MINOR=$((MINOR + 1))
        PATCH=0
        CREATE_TAG=true
        ;;
    fix|chore|docs|ci|test)
        BUMP_TYPE="patch"
        PATCH=$((PATCH + 1))
        CREATE_TAG=false
        ;;
    *)
        echo "ERROR: Unknown commit type: $COMMIT_TYPE"
        exit 1
        ;;
esac

# Construct new version
NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"

# Version cap check
if [ "$MAJOR" -ge 1 ]; then
    echo "ERROR: Version would exceed 0.x.y limit"
    exit 1
fi

echo "New version: $NEW_VERSION (${BUMP_TYPE} bump)"

# Update VERSION file
echo "$NEW_VERSION" > "$VERSION_FILE"
echo "[OK] Updated VERSION file"

# Create git tag for minor versions
if [ "$CREATE_TAG" = true ]; then
    TAG_NAME="v${NEW_VERSION}"
    
    if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
        echo "[SKIP] Tag $TAG_NAME already exists"
    else
        git tag -a "$TAG_NAME" -m "Release $TAG_NAME"
        echo "[OK] Created git tag: $TAG_NAME"
    fi
    
    # Create GitHub release if token is available
    if [ -n "$GITHUB_TOKEN" ]; then
        COMMIT_MSG=$(git log -1 --pretty=%B)
        CLEAN_MSG=$(echo "$COMMIT_MSG" | sed -E 's/^(feat|perf|refactor):\s*//')
        
        case "$COMMIT_TYPE" in
            feat) SECTION="Features" ;;
            perf) SECTION="Performance" ;;
            refactor) SECTION="Refactoring" ;;
        esac
        
        RELEASE_BODY="## ${SECTION}

- ${CLEAN_MSG}"
        
        if command -v gh &> /dev/null; then
            gh release create "$TAG_NAME" \
                --title "$TAG_NAME" \
                --notes "$RELEASE_BODY" \
                2>/dev/null && echo "[OK] Created GitHub release: $TAG_NAME" || echo "[SKIP] GitHub release creation failed"
        else
            echo "[SKIP] gh CLI not available"
        fi
    else
        echo "[SKIP] GITHUB_TOKEN not set, skipping GitHub release"
    fi
fi

echo ""
echo "===== Version Bump Complete ====="
echo "Version: $CURRENT_VERSION -> $NEW_VERSION"
echo "Bump type: $BUMP_TYPE"
echo "Tag created: $CREATE_TAG"
echo "================================="

exit 0
