#!/usr/bin/env bash
###############################################################################
# GraalVM Installation Script
# 
# This script installs GraalVM 21 with native-image support for AOT compilation.
# It detects the OS and uses the appropriate package manager.
#
# Usage: ./scripts/setup/install-graalvm.sh
###############################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║          GraalVM 21 Installation Script                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if GraalVM is already installed
if command -v native-image >/dev/null 2>&1 && java -version 2>&1 | grep -q "GraalVM"; then
    echo -e "${GREEN}✅ GraalVM is already installed:${NC}"
    java -version
    echo ""
    native-image --version
    exit 0
fi

# Detect OS
OS="$(uname -s)"
case "$OS" in
    Darwin*)
        echo -e "${YELLOW}📦 Detected macOS - Using Homebrew${NC}"
        
        if ! command -v brew >/dev/null 2>&1; then
            echo -e "${RED}❌ Homebrew not found. Install from https://brew.sh${NC}"
            exit 1
        fi
        
        echo "Installing GraalVM JDK 21..."
        brew install --cask graalvm-jdk21
        
        # Setup environment variables
        GRAALVM_HOME="/Library/Java/JavaVirtualMachines/graalvm-jdk-21/Contents/Home"
        
        if [ ! -d "$GRAALVM_HOME" ]; then
            # Try alternative path
            GRAALVM_HOME="$(find /Library/Java/JavaVirtualMachines -name "graalvm-*21*" -maxdepth 1 | head -1)/Contents/Home"
        fi
        
        if [ -d "$GRAALVM_HOME" ]; then
            echo ""
            echo -e "${GREEN}✅ GraalVM installed successfully${NC}"
            echo -e "${YELLOW}⚠️  Add to your shell profile (~/.zshrc or ~/.bashrc):${NC}"
            echo ""
            echo "export GRAALVM_HOME=\"$GRAALVM_HOME\""
            echo "export JAVA_HOME=\"$GRAALVM_HOME\""
            echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\""
            echo ""
            echo -e "${YELLOW}Then run: source ~/.zshrc (or ~/.bashrc)${NC}"
        else
            echo -e "${RED}❌ GraalVM installation directory not found${NC}"
            exit 1
        fi
        ;;
        
    Linux*)
        echo -e "${YELLOW}📦 Detected Linux - Using SDKMAN${NC}"
        
        # Install SDKMAN if not present
        if [ ! -d "$HOME/.sdkman" ]; then
            echo "Installing SDKMAN..."
            curl -s "https://get.sdkman.io" | bash
            source "$HOME/.sdkman/bin/sdkman-init.sh"
        else
            source "$HOME/.sdkman/bin/sdkman-init.sh"
        fi
        
        echo "Installing GraalVM 21 via SDKMAN..."
        sdk install java 21-graal
        sdk use java 21-graal
        
        echo ""
        echo -e "${GREEN}✅ GraalVM installed successfully${NC}"
        echo -e "${YELLOW}⚠️  SDKMAN automatically manages JAVA_HOME${NC}"
        echo ""
        echo "To make GraalVM default:"
        echo "  sdk default java 21-graal"
        ;;
        
    *)
        echo -e "${RED}❌ Unsupported OS: $OS${NC}"
        echo "Please install GraalVM manually from: https://www.graalvm.org/downloads/"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Verification Steps:${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "1. Restart your shell or run: source ~/.zshrc (or ~/.bashrc)"
echo "2. Verify GraalVM: java -version"
echo "3. Verify native-image: native-image --version"
echo "4. Build native executable: ./mvnw -Pnative native:compile -DskipTests"
echo ""
