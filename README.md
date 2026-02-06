# Exercise Timer App

A web-based exercise timer application built with ClojureScript and Reagent. This app helps users perform structured workout sessions with automatic exercise sequencing and timing.

## ✨ Features

- **Configurable Sessions**: Set your workout duration (default 5 minutes)
- **Weighted Exercise Distribution**: Exercises receive time proportional to their difficulty
- **Smart Exercise Selection**: No exercise repeats until all exercises have been used
- **Timer Controls**: Start, pause, resume, and restart your workout
- **Exercise Library**: 10 default exercises with ability to import/export
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
# Create optimized production build
npx shadow-cljs release app

# The app is now ready in the public/ directory (312KB optimized)
```

### Serving the Production Build

You can serve the production build with any static file server:

```bash
# Using Python
python3 -m http.server 8000 --directory public

# Using Node.js http-server
npx http-server public -p 8000

# Using any other static file server
```

Then open http://localhost:8000 in your browser.

## 📁 Project Structure

```
├── src/exercise_timer/
│   ├── core.cljs          # Main app and UI components
│   ├── library.cljs       # Exercise library management
│   ├── session.cljs       # Session generation with weighted time
│   ├── timer.cljs         # Timer management and callbacks
│   └── format.cljs        # Time formatting utilities
├── test/exercise_timer/   # Comprehensive test suite (161 tests)
├── public/
│   ├── index.html         # App entry point
│   ├── css/styles.css     # Responsive styles
│   └── js/main.js         # Compiled JavaScript (312KB optimized)
└── shadow-cljs.edn        # Build configuration
```

## 🛠 Technology Stack

- **Language**: ClojureScript
- **Build Tool**: shadow-cljs
- **Frontend**: Reagent (React wrapper)
- **Storage**: Browser LocalStorage
- **Testing**: cljs.test + test.check (property-based testing)

## ✅ Test Coverage

- **161 tests** (100+ property-based tests)
- **0 failures, 0 errors**
- **25 correctness properties** validated

All core algorithms (weighted time distribution, conflict resolution, timer management) are thoroughly tested with property-based testing to ensure correctness across all inputs.

## 💪 Default Exercises

The app comes with 10 default exercises:
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

## 📖 How It Works

1. **Configure**: Set your desired session duration (1-120 minutes)
2. **Start**: Click "Start Session" to begin your workout
3. **Exercise**: Follow the on-screen exercise with countdown timer
4. **Control**: Pause, resume, or restart as needed
5. **Complete**: Celebrate when you finish! 🎉

### Weighted Time Distribution

Exercises are assigned time based on their difficulty weight:
- Higher weight = more time (e.g., Burpees at 1.8)
- Lower weight = less time (e.g., Jumping Jacks at 0.8)
- The algorithm ensures total time equals your session duration

### Exercise Selection

- Exercises cycle through the entire library before repeating
- No consecutive duplicates
- Randomized order each cycle for variety

## 📥📤 Import/Export

- **Export**: Download your exercise library as JSON with timestamp
- **Import**: Upload a JSON file to merge exercises
  - Detects conflicts (same name, different weight)
  - Lets you choose which version to keep
  - Skips identical duplicates automatically

## 🌐 Browser Compatibility

Works in all modern browsers that support:
- ES6+ JavaScript
- LocalStorage API
- CSS Grid and Flexbox

Tested on:
- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)

## 📱 Responsive Design

- **Mobile**: 320px - 767px (optimized for touch)
- **Tablet**: 768px - 1023px
- **Desktop**: 1024px+

All buttons are touch-friendly (minimum 44px) and keyboard accessible.

## 🏗 Architecture

The app follows a clean architecture with:
- **Separation of Concerns**: Business logic separate from UI
- **Immutable Data**: All state updates use immutable operations
- **Reactive UI**: Reagent atoms for automatic UI updates
- **Property-Based Testing**: Ensures correctness across all inputs

## 📄 License

MIT

## 🙏 Acknowledgments

Built with:
- [ClojureScript](https://clojurescript.org/)
- [Reagent](https://reagent-project.github.io/)
- [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [test.check](https://github.com/clojure/test.check)
