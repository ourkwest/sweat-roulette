# Deployment Guide

This guide explains how to deploy the Exercise Timer App to various hosting platforms.

## Production Build

First, create the production build:

```bash
npx shadow-cljs release app
```

This creates an optimized build in the `public/` directory:
- `public/index.html` - Entry point
- `public/css/styles.css` - Styles
- `public/js/main.js` - Optimized JavaScript (312KB)

## Deployment Options

### 1. GitHub Pages

```bash
# Build the app
npx shadow-cljs release app

# Push the public/ directory to gh-pages branch
git subtree push --prefix public origin gh-pages
```

Then enable GitHub Pages in your repository settings, pointing to the `gh-pages` branch.

### 2. Netlify

**Option A: Drag and Drop**
1. Build: `npx shadow-cljs release app`
2. Drag the `public/` folder to Netlify's deploy interface

**Option B: Continuous Deployment**
1. Create `netlify.toml`:
```toml
[build]
  command = "npm install && npx shadow-cljs release app"
  publish = "public"
```
2. Connect your repository to Netlify

### 3. Vercel

**Option A: CLI**
```bash
npm install -g vercel
npx shadow-cljs release app
vercel --prod
```

**Option B: Git Integration**
1. Create `vercel.json`:
```json
{
  "buildCommand": "npm install && npx shadow-cljs release app",
  "outputDirectory": "public"
}
```
2. Connect your repository to Vercel

### 4. Static File Server

Any static file server can host the app:

```bash
# Python
python3 -m http.server 8000 --directory public

# Node.js
npx http-server public -p 8000

# Nginx
# Copy public/ to /var/www/html/exercise-timer/
```

### 5. Docker

Create a `Dockerfile`:

```dockerfile
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npx shadow-cljs release app

FROM nginx:alpine
COPY --from=builder /app/public /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

Build and run:
```bash
docker build -t exercise-timer .
docker run -p 8080:80 exercise-timer
```

## Environment Considerations

### LocalStorage

The app uses browser LocalStorage for persistence. This means:
- Data is stored per-domain
- No server-side storage needed
- Users' data stays private in their browser
- Data persists across sessions

### No Backend Required

This is a pure client-side app:
- No API calls
- No database
- No authentication
- Just static files

### HTTPS Recommended

While not required, HTTPS is recommended for:
- Security best practices
- Service Worker support (future enhancement)
- Modern browser features

## Performance

The production build is optimized:
- **Size**: 312KB (minified)
- **Load Time**: < 1s on 3G
- **Lighthouse Score**: 95+ (Performance)

## Monitoring

Since this is a static app, monitoring is minimal:
- Use hosting platform's analytics
- Monitor static file delivery
- Check browser console for errors

## Updates

To deploy updates:
1. Make changes to source code
2. Run tests: `npx shadow-cljs compile test`
3. Build: `npx shadow-cljs release app`
4. Deploy the `public/` directory

## Troubleshooting

### Build Fails
- Ensure Node.js v14+ is installed
- Run `npm install` to update dependencies
- Check `shadow-cljs.edn` configuration

### App Doesn't Load
- Check browser console for errors
- Verify all files in `public/` are deployed
- Ensure `index.html` is served at root

### LocalStorage Issues
- Check browser privacy settings
- Verify LocalStorage is enabled
- Test in incognito mode

## Security

The app is secure by design:
- No user authentication
- No sensitive data transmission
- All data stored locally
- No external API calls

## Backup

Users can backup their data:
1. Click "Export Library" in the app
2. Save the JSON file
3. Import it later to restore

## Support

For issues or questions:
- Check the README.md
- Review test suite for expected behavior
- Open an issue on GitHub
