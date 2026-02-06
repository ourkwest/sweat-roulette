#!/bin/bash
# Build script for GitHub Pages deployment

echo "Building for GitHub Pages..."

# Clean docs directory
echo "Cleaning docs directory..."
rm -rf docs/*

# Copy source files to docs
echo "Copying HTML and CSS..."
cp src/public/index.html docs/
cp -r src/public/css docs/

# Build production JavaScript
echo "Compiling ClojureScript..."
npx shadow-cljs release release

# Add .nojekyll file
touch docs/.nojekyll

echo "✅ Build complete! Files are in /docs directory"
echo "Ready to commit and push to GitHub Pages"
