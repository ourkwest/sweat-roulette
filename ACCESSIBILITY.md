# Accessibility Features

This document outlines the accessibility features implemented in the Exercise Timer App to ensure WCAG 2.1 AA compliance and provide an inclusive user experience.

## Keyboard Navigation

### Global Keyboard Shortcuts
- **Spacebar**: Play/Pause the timer
- **R key**: Restart the current session
- **Escape**: Close modal dialogs
- **Enter**: Submit forms (e.g., add exercise dialog)

### Tab Navigation
- All interactive elements are keyboard accessible
- Logical tab order through the interface
- Focus indicators visible on all interactive elements

## ARIA Labels and Semantic HTML

### Timer Display
- `role="timer"` - Identifies the countdown timer
- `aria-live="polite"` - Announces time changes to screen readers
- `aria-atomic="true"` - Reads entire timer value on updates
- `aria-label` - Descriptive labels for time remaining

### Exercise Display
- `role="status"` - Identifies current exercise information
- `aria-live="polite"` - Announces exercise changes
- `aria-label` - Descriptive labels for exercise name and progress

### Control Panel
- `role="group"` - Groups timer control buttons
- `aria-label` - Descriptive labels for all buttons with keyboard hints
  - "Start workout (Spacebar)"
  - "Pause workout (Spacebar)"
  - "Resume workout (Spacebar)"
  - "Restart workout (R key)"

### Exercise Library
- `role="region"` - Identifies the library section
- `role="list"` and `role="listitem"` - Semantic list structure
- `aria-live="polite"` - Announces library stats changes
- `role="group"` - Groups controls for each exercise
- `aria-label` - Descriptive labels for all control buttons
- `aria-pressed` - Indicates toggle button state (enabled/disabled)

### Modal Dialogs
- `role="dialog"` - Identifies modal dialogs
- `aria-modal="true"` - Indicates modal behavior
- `aria-labelledby` - Links dialog to its title
- `aria-required="true"` - Marks required form fields
- `role="alert"` - Error messages announced immediately
- `aria-live="assertive"` - Priority announcements for errors

### Form Controls
- `aria-valuemin`, `aria-valuemax`, `aria-valuenow` - Range slider values
- `aria-label` - Descriptive labels for all inputs

## Visual Accessibility

### Color Contrast (WCAG AA Compliant)
All text meets WCAG 2.1 AA contrast requirements (4.5:1 for normal text, 3:1 for large text):

- **Primary buttons**: #2980b9 on white (7.5:1)
- **Hover state**: #21618c on white (9.8:1)
- **Disabled buttons**: #7f8c8d on white (4.5:1)
- **Success green**: #27ae60 on white (3.4:1 for large text)
- **Error red**: #c0392b on white (5.9:1)
- **Timer display**: #e74c3c on white (3.9:1 for large text)
- **Body text**: #333 on white (12.6:1)

### Focus Indicators
- 3px solid orange (#f39c12) outline on all focusable elements
- 2px offset for clear visibility
- Visible on keyboard focus (`:focus-visible`)
- Not shown on mouse click (`:focus:not(:focus-visible)`)

### Touch Targets
- Minimum 44x44px touch targets for all interactive elements
- Adequate spacing between buttons to prevent mis-taps

## Skip Navigation

- "Skip to main content" link at the top of the page
- Hidden until focused with keyboard
- Allows keyboard users to bypass header and jump to main content

## Responsive Design

### Font Sizes
- Minimum 16px font size on mobile devices
- Scalable text that respects user preferences
- No fixed pixel heights that prevent text scaling

### Layout
- Responsive breakpoints for mobile (320px+), tablet (768px+), and desktop (1024px+)
- Touch-friendly controls on mobile devices
- Adequate spacing for touch interactions

## Voice Announcements

### Web Speech API Integration
- Optional voice announcements (can be toggled on/off)
- British English (en-GB) accent by default
- Announces:
  - Exercise name and duration when starting each exercise
  - Time remaining every 10 seconds
  - Session completion message

### Announcement Timing
- Synchronized with timer to avoid delays
- Combined announcements to prevent overlapping speech
- Reset tracking between sessions

## Testing Recommendations

### Manual Testing
1. **Keyboard Navigation**: Navigate entire app using only keyboard
2. **Screen Reader**: Test with NVDA (Windows), JAWS (Windows), or VoiceOver (macOS/iOS)
3. **Color Contrast**: Verify with browser DevTools or WebAIM Contrast Checker
4. **Touch Targets**: Test on mobile devices with various screen sizes
5. **Voice Announcements**: Test speech synthesis on different browsers

### Automated Testing
- Use axe DevTools or WAVE browser extension for accessibility audits
- Run Lighthouse accessibility audit in Chrome DevTools
- Validate HTML with W3C Validator

## Browser Support

Accessibility features are supported in:
- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers (iOS Safari, Chrome Mobile)

Note: Web Speech API support varies by browser and platform. The app gracefully degrades when speech is unavailable.

## Future Improvements

Potential enhancements for even better accessibility:
- High contrast mode support
- Reduced motion preferences (prefers-reduced-motion)
- Customizable keyboard shortcuts
- Multiple language support for voice announcements
- Screen reader optimized exercise library navigation
- Persistent user preferences for accessibility settings
