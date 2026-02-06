# Exercise Timer App

A web-based exercise timer application that helps users perform structured workout sessions with automatic exercise sequencing and timing.

## Features

- Configure session duration
- Automatic exercise sequence generation with weighted time distribution
- Countdown timer for each exercise
- Session controls (pause, resume, restart)
- Exercise library management with local storage persistence
- Import/export exercise library as JSON
- Responsive design for mobile, tablet, and desktop

## Technology Stack

- **Language**: ClojureScript
- **Build Tool**: shadow-cljs
- **Frontend Framework**: Reagent (React wrapper)
- **Storage**: Browser LocalStorage
- **Testing**: cljs.test + test.check (property-based testing)

## Development

### Prerequisites

- Node.js (v14 or higher)
- npm or yarn

### Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Open browser to http://localhost:8020
```

### Testing

```bash
# Run tests once
npm test

# Run tests in watch mode
npm run test-watch
```

### Build for Production

```bash
npm run build
```

The production build will be output to `public/js/`.

## Project Structure

```
exercise-timer-app/
├── src/exercise_timer/
│   ├── core.cljs          # Main app entry point
│   ├── library.cljs       # Exercise library manager
│   ├── session.cljs       # Session generator
│   └── timer.cljs         # Timer manager
├── public/
│   ├── index.html         # HTML entry point
│   └── css/
│       └── styles.css     # Application styles
├── shadow-cljs.edn        # Build configuration
└── package.json           # Node dependencies
```

## License

MIT
