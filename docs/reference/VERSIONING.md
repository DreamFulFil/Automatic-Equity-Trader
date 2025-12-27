# Semantic Versioning Guide

This project follows [Semantic Versioning 2.0.0](https://semver.org/) with automated version management.

## Version Format

`0.MINOR.PATCH`

- **0.x.y:** Pre-1.0 development (stable, but API may change)
- Version cap: **Never reach 1.0.0** (maintained in 0.x.y range)

## Automatic Version Bumping

Version bumps are **automatic** based on commit type:

### Minor Version (+0.1.0)

Triggered by:
- `feat:` - New features
- `perf:` - Performance improvements
- `refactor:` - Code restructuring

**Actions:**
-  VERSION file updated
-  Git tag created (`v0.X.0`)
-  GitHub release created

**Example:**
```bash
# Current: 0.79.0
git commit -m "feat: add dynamic stock selection"
# Result: 0.80.0 + tag v0.80.0 + GitHub release
```

### Patch Version (+0.0.1)

Triggered by:
- `fix:` - Bug fixes
- `chore:` - Maintenance
- `docs:` - Documentation
- `ci:` - CI/CD changes
- `test:` - Testing

**Actions:**
-  VERSION file updated
 No git tag- 
 No GitHub release- 

**Example:**
```bash
# Current: 0.80.0
git commit -m "fix: resolve NULL pointer in trading engine"
# Result: 0.80.1 (VERSION file only)
```

## Workflow Integration

The version bump is integrated into the standard completion workflow:

### Manual Process

1. **Make code changes**
2. **Run tests:** `./run-tests.sh <jasypt-password>`
3. **Bump version:**
   ```bash
   ./scripts/operational/bump-version.sh feat
   ```
4. **Commit & push:**
   ```bash
   git add VERSION
   git commit -m "feat: your feature description"
   git push origin main
   git push origin --tags  # if minor version
   ```

### Using Helper Script

```bash
# Automatic version bump + tag + release
./scripts/operational/bump-version.sh feat

# Then commit and push
git add VERSION
git commit -m "feat: your message"
git push origin main && git push origin --tags
```

## GitHub Copilot Integration

When working with GitHub Copilot, the agent will automatically:

1. Detect commit type from your changes
2. Update VERSION file accordingly
3. Create git tag (if minor version)
4. Create GitHub release (if minor version with `GITHUB_TOKEN` set)
5. Commit and push changes

**Required:** Set `GITHUB_TOKEN` environment variable for automatic release creation.

## Version History

- **Current Version:** See [VERSION](VERSION) file
- **All Releases:** https://github.com/DreamFulFil/Automatic-Equity-Trader/releases
- **All Tags:** `git tag | grep "^v0\."`

## Examples

### Feature Release (Minor)
```bash
# Before: VERSION=0.79.0
./scripts/operational/bump-version.sh feat
# After: VERSION=0.80.0, tag v0.80.0, GitHub release created

git add VERSION
git commit -m "feat: implement AI-powered trade validation"
git push origin main --tags
```

### Bug Fix (Patch)
```bash
# Before: VERSION=0.80.0
./scripts/operational/bump-version.sh fix
# After: VERSION=0.80.1 (no tag, no release)

git add VERSION
git commit -m "fix: correct timezone handling in scheduler"
git push origin main
```

### Performance Improvement (Minor)
```bash
# Before: VERSION=0.80.1
./scripts/operational/bump-version.sh perf
# After: VERSION=0.81.0, tag v0.81.0, GitHub release created

git add VERSION
git commit -m "perf: optimize database query with indexed columns"
git push origin main --tags
```

## Release Notes Format

GitHub releases automatically use this format:

```markdown
## Features (or Performance/Refactoring)

- <commit description without type prefix>
```

Example release body for `feat: add multi-timeframe analysis`:
```markdown
## Features

- add multi-timeframe analysis
```

## Troubleshooting

### Tag already exists
```bash
# Delete local tag
git tag -d v0.80.0

# Delete remote tag
git push origin :refs/tags/v0.80.0

# Recreate
./scripts/operational/bump-version.sh feat
git push origin --tags
```

### GitHub release failed
```bash
# Check GITHUB_TOKEN
echo $GITHUB_TOKEN

# Manually create release
gh release create v0.80.0 --title "v0.80.0" --notes "## Features\n\n- your feature"
```

### Version out of sync
```bash
# Check current version
cat VERSION

# Check last tag
git describe --tags --abbrev=0

# Fix VERSION file manually if needed
echo "0.80.0" > VERSION
git add VERSION
git commit -m "chore: sync VERSION file with git tags"
```

## Best Practices

1. **One logical change per commit** - Makes version history meaningful
2. **Use correct commit type** - Ensures proper version bumping
3. **Write clear commit messages** - They become release notes
4. **Test before committing** - Always run `./run-tests.sh` first
5. **Push tags separately** - Use `git push origin --tags` for minor versions

## References

- [Semantic Versioning 2.0.0](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [GitHub Releases API](https://docs.github.com/en/rest/releases)
