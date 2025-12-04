# Utility Scripts

Python setup and debugging scripts for the MTXF Lunch Bot.

## Scripts

| Script | Purpose |
|--------|---------|
| `install-python312.fish` | Install Python 3.12 via Homebrew and create venv |
| `force-fix-python.fish` | Emergency fix for broken Python setup (pyenv method) |
| `check-python-compat.fish` | Check Python version compatibility with Shioaji |
| `test-python312.fish` | Test Python 3.12 setup and Shioaji import |
| `fix-python-now.fish` | Quick fix script (alternative method) |

## Usage

```bash
# Install Python 3.12 (recommended)
./scripts/install-python312.fish

# Test setup
./scripts/test-python312.fish

# Emergency fix if needed
./scripts/force-fix-python.fish
```

All scripts are executable and can be run from the project root.