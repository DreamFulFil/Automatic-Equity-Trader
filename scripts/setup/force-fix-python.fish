#!/usr/bin/env fish

# Force fix Python setup - run this now
echo "🔧 FORCE FIXING PYTHON SETUP"
echo "============================="

cd (dirname (status --current-filename))

# Kill everything first
pkill -f "python.*bridge" 2>/dev/null || true
pkill -f "java.*mtxf" 2>/dev/null || true

# Remove broken venv completely
echo "Removing broken venv..."
rm -rf python/venv

# Setup pyenv path
set -x PATH $HOME/.pyenv/bin $PATH
if test -f $HOME/.pyenv/bin/pyenv
    eval ($HOME/.pyenv/bin/pyenv init --path)
end

# Install pyenv if needed
if not command -v pyenv > /dev/null
    echo "Installing pyenv..."
    curl -L https://github.com/pyenv/pyenv-installer/raw/master/bin/pyenv-installer | bash
    set -x PATH $HOME/.pyenv/bin $PATH
    eval ($HOME/.pyenv/bin/pyenv init --path)
end

# Force install Python 3.12
echo "Installing Python 3.12.7..."
pyenv install -s 3.12.7
pyenv local 3.12.7

# Verify Python version
echo "Python version: "(python3 --version)
echo "Python path: "(which python3)

# Create new venv with Python 3.12
echo "Creating venv with Python 3.12..."
cd python
python3 -m venv venv
source venv/bin/activate.fish

# Verify venv Python
echo "Venv Python: "(python --version)
echo "Venv path: "(which python)

# Install dependencies
pip install --upgrade pip
pip install -r requirements.txt

echo ""
echo "✅ FIXED! Python 3.12 venv ready"
echo "Test with: python/venv/bin/python --version"