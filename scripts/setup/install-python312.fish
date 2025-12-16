#!/usr/bin/env fish

# Install Python 3.12 and fix venv
echo "🐍 Installing Python 3.12 via Homebrew"
echo "======================================="

# Kill any running processes
pkill -f "python.*bridge" 2>/dev/null || true

# Install Python 3.12 via brew
echo "Installing python@3.12..."
brew install python@3.12

# Verify installation
if command -v python3.12 > /dev/null
    echo "✅ Python 3.12 installed: "(python3.12 --version)
    echo "✅ Path: "(which python3.12)
else
    echo "❌ Python 3.12 installation failed!"
    exit 1
end

# Remove old venv and create new one
echo "Creating new venv with Python 3.12..."
cd python
rm -rf venv
python3.12 -m venv venv
source venv/bin/activate.fish

echo "Venv Python: "(python --version)
echo "Installing dependencies..."
pip install --upgrade pip
pip install -r requirements.txt

echo ""
echo "✅ SUCCESS! Python 3.12 venv ready"
echo "Test: python/venv/bin/python --version"