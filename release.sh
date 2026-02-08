#!/bin/bash
# Release script - builds and commits the production release

set -e  # Exit on any error

echo "🚀 Starting release process..."
echo ""

# Check if there are uncommitted changes in source files
if ! git diff-index --quiet HEAD -- src/ public/ shadow-cljs.edn package.json; then
    echo "⚠️  Warning: You have uncommitted changes in source files."
    echo "It's recommended to commit source changes before releasing."
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Release cancelled."
        exit 1
    fi
fi

# Run the build script
echo "📦 Building production release..."
./build.sh

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Release cancelled."
    exit 1
fi

echo ""
echo "📝 Staging docs/ directory..."
git add docs/

# Check if there are changes to commit
if git diff --cached --quiet; then
    echo "ℹ️  No changes to commit in docs/ directory."
    echo "Release complete (no new changes)."
    exit 0
fi

# Get git hash and build time for commit message
GIT_HASH=$(git rev-parse --short HEAD)
BUILD_TIME=$(date -u +"%Y-%m-%d %H:%M UTC")

# Create commit
COMMIT_MSG="Release build $GIT_HASH - $BUILD_TIME"
echo "💾 Creating commit: $COMMIT_MSG"
git commit -m "$COMMIT_MSG"
git push

echo ""
echo "✅ Release complete!"

