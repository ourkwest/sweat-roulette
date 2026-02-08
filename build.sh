#!/bin/bash
# Build script for GitHub Pages deployment

echo "Building for GitHub Pages..."

# Clean docs directory completely
echo "Cleaning docs directory..."
rm -rf docs/*

# Copy static assets from source
echo "Copying static assets from src/public/..."
cp src/public/index.html docs/
cp -r src/public/css docs/

# Build production JavaScript with advanced optimizations
echo "Compiling ClojureScript with advanced optimizations..."
npx shadow-cljs release prod

# Add .nojekyll file (prevents GitHub Pages from ignoring files starting with _)
touch docs/.nojekyll

echo "✅ Build complete! Files are in /docs directory"
echo "Ready to commit and push to GitHub Pages"
