# Exercise Timer App

A web-based exercise timer application built with ClojureScript and Reagent. This app helps users perform structured workout sessions with automatic exercise sequencing and timing.

**🌐 Live Demo:** [https://ourkwest.github.io/sweat-roulette/](https://ourkwest.github.io/sweat-roulette/)

## ✨ Features

- **Configurable Sessions**: Set your workout duration (default 5 minutes)
- **Balanced Time Distribution**: Harder exercises get less time, easier exercises get more time for a balanced workout
- **Smart Exercise Selection**: No exercise repeats until all exercises have been used
- **Timer Controls**: Start, pause, resume, and restart your workout
- **Voice Announcements**: British-accented voice announces exercise names, durations, and time remaining (optional)
- **Exercise Library Management**: 
  - 14 default exercises included
  - Add new exercises with custom difficulty weights
  - Adjust exercise weights with +/− buttons
  - Enable/disable exercises for session inclusion
  - Delete exercises you don't want
  - Import/export your library as JSON
- **Responsive Design**: Works on mobile, tablet, and desktop
- **Local Storage**: Your exercise library persists in the browser

## 🚀 Quick Start

### Development

```bash
# Install dependencies
npm install

# Start development server with hot reload
npx shadow-cljs watch app

# Run tests
npx shadow-cljs compile test

# Open http://localhost:8020 in your browser
```

### Production Build

```bash
# Easy way: Use the build script
./build.sh

# Manual way:
# 1. Copy source files
cp src/public/index.html docs/
cp -r src/public/css docs/

# 2. Build JavaScript
npx shadow-cljs release release

# The app is now ready in the docs/ directory (313KB optimized)
```

### Deploying to GitHub Pages

The project is configured to deploy from the `/docs` folder:

1. Push your code to GitHub
2. Go to your repository Settings → Pages
3. Under "Source", select "Deploy from a branch"
4. Select branch: `main` (or `master`) and folder: `/docs`
5. Click Save
6. Your app will be live at `https://yourusername.github.io/your-repo-name/`

The `/docs` folder contains the production build and is ready for GitHub Pages deployment.

### Serving Locally

You can serve the production build with any static file server:

```bash
# Using Python
python3 -m http.server 8000 --directory docs

# Using Node.js http-server
npx http-server docs -p 8000

# Using any other static file server
```

Then open http://localhost:8000 in your browser.

## 📁 Project Structure

```
├── src/
│   ├── exercise_timer/    # ClojureScript source code
│   │   ├── core.cljs      # Main app and UI components
│   │   ├── library.cljs   # Exercise library management
│   │   ├── session.cljs   # Session generation with weighted time
│   │   ├── timer.cljs     # Timer management and callbacks
│   │   ├── speech.cljs    # Voice announcements (Web Speech API)
│   │   └── format.cljs    # Time formatting utilities
│   └── public/            # Source HTML and CSS
│       ├── index.html     # App entry point (source)
│       └── css/styles.css # Responsive styles (source)
├── test/exercise_timer/   # Comprehensive test suite (161 tests)
├── docs/                  # Production build (GitHub Pages ready)
│   ├── index.html         # Generated from src/public/
│   ├── css/styles.css     # Generated from src/public/
│   ├── js/main.js         # Compiled JavaScript (313KB optimized)
│   └── .nojekyll          # GitHub Pages config
├── public/                # Development build (generated, gitignored)
├── build.sh               # Build script for GitHub Pages
└── shadow-cljs.edn        # Build configuration
```

**Key Points:**
- **Source files** live in `src/` (both ClojureScript and HTML/CSS)
- **Development build** goes to `public/` (gitignored, for local dev server)
- **Production build** goes to `docs/` (committed, for GitHub Pages)

## 🛠 Technology Stack

- **Language**: ClojureScript
- **Build Tool**: shadow-cljs
- **Frontend**: Reagent (React wrapper)
- **Storage**: Browser LocalStorage
- **Speech**: Web Speech API (native browser)
- **Testing**: cljs.test + test.check (property-based testing)

## ✅ Test Coverage

- **161 tests** (100+ property-based tests)
- **0 failures, 0 errors**
- **25 correctness properties** validated

All core algorithms (weighted time distribution, conflict resolution, timer management) are thoroughly tested with property-based testing to ensure correctness across all inputs.

## 💪 Default Exercises

The app comes with 14 default exercises:
- Push-ups (weight: 1.2)
- Squats (weight: 1.0)
- Plank (weight: 1.5)
- Jumping Jacks (weight: 0.8)
- Lunges (weight: 1.0)
- Mountain Climbers (weight: 1.3)
- Burpees (weight: 1.8)
- High Knees (weight: 0.9)
- Sit-ups (weight: 1.0)
- Wall Sit (weight: 1.4)
- Russian Twists (weight: 1.1)
- Kneel to Stand (weight: 1.6)
- Air Punches (weight: 0.7)
- Plank Shoulder Taps (weight: 1.4)

## 📖 How It Works

1. **Configure**: Set your desired session duration (1-120 minutes)
2. **Customize**: Enable/disable exercises, adjust weights, or add new ones
3. **Start**: Click "Start Session" to generate a workout from enabled exercises
4. **Exercise**: Follow the on-screen exercise with countdown timer and voice guidance
5. **Control**: Pause, resume, or restart as needed
6. **Complete**: Celebrate when you finish! 🎉

### Balanced Time Distribution

Exercises are assigned time **inversely** proportional to their difficulty weight:
- **Higher weight = harder exercise = LESS time** (e.g., Burpees at 1.8 get ~20 seconds)
- **Lower weight = easier exercise = MORE time** (e.g., Air Punches at 0.7 get ~50 seconds)
- This creates a balanced workout with shorter bursts of intense exercises
- The algorithm ensures total time equals your session duration exactly

### Exercise Selection

- Only **enabled** exercises are included in sessions
- Exercises cycle through the entire library before repeating
- No consecutive duplicates
- Randomized order each cycle for variety

### Voice Announcements

- **British English accent** by default
- Announces exercise name and duration when starting each exercise
- Announces time remaining every 10 seconds (at 10, 20, 30, etc.)
- Announces completion message
- Can be toggled on/off in the configuration panel
- Works offline (uses native browser speech synthesis)

## 🎛 Exercise Library Controls

Each exercise has four controls:
- **− button**: Decrease difficulty weight by 0.1 (minimum 0.5)
- **+ button**: Increase difficulty weight by 0.1 (maximum 2.0)
- **✓/✗ button**: Toggle enabled/disabled (green = enabled, red = disabled)
- **🗑 button**: Delete exercise (with confirmation)

## 📥📤 Import/Export

### Export
- Click "Export Library" to download your exercise library as JSON
- File is named with timestamp: `exercise-library-YYYYMMDD-HHMMSS.json`
- Contains all exercises with their names, weights, and enabled status

### Import
- Click "Import Library" to select a JSON file
- The app validates the file format and exercises
- **Automatic merge** for new exercises and identical duplicates
- **Conflict resolution** for exercises with same name but different weights:
  - Dialog shows each conflict with existing vs imported values
  - Choose which version to keep for each exercise
  - Complete the import with your selections

### Import Behavior
- **New exercises**: Added to your library automatically
- **Identical duplicates**: Skipped (same name and weight)
- **Conflicts**: You choose which version to keep
- **Invalid data**: Import fails with clear error message

Example import file format:
```json
{
  "version": 1,
  "exercises": [
    {"name": "Push-ups", "weight": 1.2},
    {"name": "Squats", "weight": 1.0}
  ]
}
```

See `example-import.json` for a sample import file.

## 🌐 Browser Compatibility

Works in all modern browsers that support:
- ES6+ JavaScript
- LocalStorage API
- CSS Grid and Flexbox
- Web Speech API (for voice announcements)

Tested on:
- Chrome/Edge (latest) - Excellent speech support
- Firefox (latest) - Good speech support
- Safari (latest) - Excellent speech support

## 📱 Responsive Design

- **Mobile**: 320px - 767px (optimized for touch)
- **Tablet**: 768px - 1023px
- **Desktop**: 1024px+

All buttons are touch-friendly (minimum 44px) and keyboard accessible.

## ♿ Accessibility

The app is built with accessibility in mind and meets WCAG 2.1 AA standards:

- **Keyboard Navigation**: Full keyboard support with shortcuts (Space, R, Escape)
- **Screen Reader Support**: ARIA labels, roles, and live regions throughout
- **Color Contrast**: All text meets WCAG AA contrast requirements (4.5:1+)
- **Focus Indicators**: Clear 3px orange outlines on all interactive elements
- **Touch Targets**: Minimum 44x44px for all buttons
- **Skip Navigation**: "Skip to main content" link for keyboard users
- **Voice Announcements**: Optional audio feedback for exercise changes and timing

See [ACCESSIBILITY.md](ACCESSIBILITY.md) for detailed accessibility documentation.

## 🏗 Architecture

The app follows a clean architecture with:
- **Separation of Concerns**: Business logic separate from UI
- **Immutable Data**: All state updates use immutable operations
- **Reactive UI**: Reagent atoms for automatic UI updates
- **Property-Based Testing**: Ensures correctness across all inputs
- **Callback System**: Timer events trigger UI updates and voice announcements

## 📄 License

MIT

## 🙏 Acknowledgments

Built with:
- [ClojureScript](https://clojurescript.org/)
- [Reagent](https://reagent-project.github.io/)
- [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [test.check](https://github.com/clojure/test.check)
- [Web Speech API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API)
