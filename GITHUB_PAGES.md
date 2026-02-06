# GitHub Pages Deployment Guide

This project is configured for easy deployment to GitHub Pages.

## Quick Setup

1. **Build the production version:**
   ```bash
   ./build.sh
   ```
   
   This script will:
   - Copy HTML and CSS from `src/public/` to `docs/`
   - Compile ClojureScript to `docs/js/`
   - Add the `.nojekyll` file

2. **Commit and push to GitHub:**
   ```bash
   git add docs/
   git commit -m "Add production build for GitHub Pages"
   git push origin main
   ```

3. **Enable GitHub Pages:**
   - Go to your repository on GitHub
   - Click **Settings** → **Pages**
   - Under **Source**, select:
     - Branch: `main` (or `master`)
     - Folder: `/docs`
   - Click **Save**

4. **Wait a minute**, then visit:
   ```
   https://yourusername.github.io/your-repo-name/
   ```

## Configuration Details

- **Build output**: The production build is in the `/docs` folder
- **`.nojekyll` file**: Prevents Jekyll processing (already included)
- **Asset paths**: Configured to work with GitHub Pages subdirectory structure

## Updating the Deployment

Whenever you make changes:

```bash
# Rebuild (copies source files and compiles JS)
./build.sh

# Commit and push
git add docs/ src/
git commit -m "Update production build"
git push origin main
```

GitHub Pages will automatically update within a minute or two.

## Project Structure

- **Source files**: `src/public/` (HTML, CSS) and `src/exercise_timer/` (ClojureScript)
- **Development build**: `public/` (gitignored, for local dev server)
- **Production build**: `docs/` (committed, for GitHub Pages)

## Custom Domain (Optional)

To use a custom domain:

1. Add a `CNAME` file to the `/docs` folder with your domain name
2. Configure your DNS provider to point to GitHub Pages
3. Enable HTTPS in GitHub Pages settings

See [GitHub's custom domain documentation](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site) for details.

## Troubleshooting

**Site not loading?**
- Check that GitHub Pages is enabled in Settings → Pages
- Verify the `/docs` folder is selected as the source
- Wait a few minutes for the deployment to complete
- Check the Actions tab for any deployment errors

**Assets not loading?**
- Ensure the `.nojekyll` file exists in `/docs`
- Check that all files in `/docs` are committed and pushed
- Verify the asset paths in `index.html` are correct

**404 errors?**
- Make sure you're using the correct URL format:
  `https://username.github.io/repo-name/` (note the trailing slash)
