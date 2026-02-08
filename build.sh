#!/bin/bash
# Build script for GitHub Pages deployment

echo "Building for GitHub Pages..."

# Get git hash and build time
GIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BUILD_TIME=$(date -u +"%Y-%m-%d %H:%M UTC")

echo "Version: $GIT_HASH"
echo "Build time: $BUILD_TIME"

# Clean docs directory completely
echo "Cleaning docs directory..."
rm -rf docs/*

# Copy static assets from source
echo "Copying static assets from src/public/..."
cp src/public/index.html docs/
cp src/public/favicon.svg docs/
cp -r src/public/css docs/

# Build production JavaScript with advanced optimizations and version info
echo "Compiling ClojureScript with advanced optimizations..."
npx shadow-cljs release prod --config-merge "{:closure-defines {exercise-timer.version/VERSION \"$GIT_HASH\" exercise-timer.version/BUILD_TIME \"$BUILD_TIME\"}}"

# Add .nojekyll file (prevents GitHub Pages from ignoring files starting with _)
touch docs/.nojekyll

echo "✅ Build complete! Files are in /docs directory"
echo "Ready to commit and push to GitHub Pages"
