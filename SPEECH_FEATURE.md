# Voice Announcements Feature

## Overview

The Exercise Timer App now includes **voice announcements** using the Web Speech API! The app will speak:
- Exercise names when switching to a new exercise
- Remaining time every 10 seconds
- Completion message when workout finishes

## How It Works

### Browser Support
- ✅ Chrome/Edge (excellent)
- ✅ Safari (excellent)
- ✅ Firefox (good)
- ✅ Opera (good)

The feature automatically detects if speech synthesis is available and only shows the toggle if supported.

### What Gets Announced

1. **Exercise Names**: When starting or switching exercises
   - "Push-ups"
   - "Squats"
   - etc.

2. **Time Remaining**: Every 10 seconds (at exact multiples: 10, 20, 30, etc.)
   - "Two minutes thirty seconds"
   - "One minute"
   - "Thirty seconds"
   - "Ten" (countdown for last 10 seconds)

3. **Completion**: When workout finishes
   - "Workout complete! Great job!"

### User Control

Users can toggle voice announcements on/off with a checkbox in the configuration panel:
- ☑️ 🔊 Voice announcements (exercise names + time every 10s)

The setting is enabled by default but can be turned off at any time.

## Implementation Details

### Code Structure

**New File**: `src/exercise_timer/speech.cljs`
- `speak!` - Core text-to-speech function
- `speak-exercise-name!` - Announce exercise names
- `speak-time-remaining!` - Announce time in natural language
- `speak-completion!` - Announce workout completion
- `should-announce-time?` - Check if time is at 10-second multiple (10, 20, 30, etc.)
- `reset-announcement-tracking!` - Reset tracking when starting new session

**Integration**: `src/exercise_timer/core.cljs`
- Added speech toggle to configuration panel
- Integrated speech into timer callbacks
- Announces on exercise change, every 10 seconds, and on completion

### Technical Details

**API Used**: Web Speech API (`window.speechSynthesis`)
- Native browser API (no dependencies)
- Works offline
- Multiple voices available (browser-dependent)
- Customizable rate, pitch, volume

**Performance**: Negligible impact
- Async API (non-blocking)
- Only speaks when needed
- Can be cancelled anytime

**Accessibility**: Enhances workout experience
- Hands-free operation
- No need to look at screen
- Helpful for visually impaired users

## Usage Example

```clojure
;; Speak exercise name
(speech/speak-exercise-name! "Push-ups")

;; Speak time remaining
(speech/speak-time-remaining! 65)  ; "One minute five seconds"
(speech/speak-time-remaining! 30)  ; "Thirty seconds"
(speech/speak-time-remaining! 5)   ; "Five"

;; Speak completion
(speech/speak-completion!)  ; "Workout complete! Great job!"

;; Check if speech is available
(speech/speech-available?)  ; true/false

;; Cancel ongoing speech
(speech/cancel-speech!)
```

## Customization Options

The speech can be customized with options:

```clojure
(speech/speak! "Hello" 
  {:rate 0.9    ; Speed (0.1 to 10, default 1.0)
   :pitch 1.0   ; Pitch (0 to 2, default 1.0)
   :volume 0.8  ; Volume (0 to 1, default 1.0)
   :lang "en-US"}) ; Language (default "en-US")
```

## Future Enhancements

Potential improvements:
- [ ] Voice selection (male/female, different accents)
- [ ] Custom announcement intervals (5s, 15s, 30s)
- [ ] Motivational messages ("Keep going!", "Almost there!")
- [ ] Language selection
- [ ] Volume control slider
- [ ] Announcement preview button

## Testing

To test the feature:
1. Start the app
2. Ensure the voice announcements checkbox is checked
3. Start a workout session
4. Listen for:
   - Exercise name announcement at start
   - Time announcements at exact 10-second intervals (10s, 20s, 30s, etc.)
   - Completion message at end

## Timing Fix

**Issue**: Initial implementation announced at 19s, 29s, 39s (one second late)
**Cause**: Timer ticks after decrementing, so announcements were off by 1 second
**Fix**: Changed logic to announce when `seconds % 10 == 0` (at exact multiples)
**Result**: Now announces at 10s, 20s, 30s, 40s, etc. as expected

## Browser Compatibility

| Browser | Support | Notes |
|---------|---------|-------|
| Chrome | ✅ Excellent | Multiple voices, good quality |
| Edge | ✅ Excellent | Multiple voices, good quality |
| Safari | ✅ Excellent | High-quality voices |
| Firefox | ✅ Good | Works well, fewer voices |
| Opera | ✅ Good | Based on Chromium |

## Privacy

- No data sent to servers
- All speech synthesis happens locally in the browser
- No microphone access required
- No recording or storage of audio

## Accessibility Benefits

- **Hands-free**: No need to look at screen during workout
- **Visually impaired**: Audio feedback for all timer events
- **Multitasking**: Can focus on exercise form
- **Motivation**: Voice feedback keeps you engaged

## Implementation Time

- **Development**: 30 minutes
- **Testing**: 15 minutes
- **Total**: 45 minutes

Very straightforward implementation using native browser APIs!
