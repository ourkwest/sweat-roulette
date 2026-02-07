#!/bin/bash
# Build script for GitHub Pages deployment

echo "Building for GitHub Pages..."

# Build production JavaScript
echo "Compiling ClojureScript..."
npx shadow-cljs release app

# Clean docs directory
echo "Cleaning docs directory..."
rm -rf docs/*

# Copy built files to docs
echo "Copying files to docs/..."
cp -r public/* docs/

# Add .nojekyll file (prevents GitHub Pages from ignoring files starting with _)
touch docs/.nojekyll

echo "✅ Build complete! Files are in /docs directory"
echo "Ready to commit and push to GitHub Pages"
