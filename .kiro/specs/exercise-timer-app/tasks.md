# Implementation Plan: Exercise Timer App

## Overview

This plan implements a ClojureScript-based exercise timer web application with weighted exercise sequencing, local storage persistence, and responsive UI. The implementation follows a bottom-up approach, building core data structures and business logic first, then integrating with UI components.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Initialize shadow-cljs project with Reagent
  - Configure build settings for development and production
  - Set up directory structure (src/exercise_timer/)
  - Add test.check dependency for property-based testing
  - Create initial namespace files (library.cljs, session.cljs, timer.cljs, core.cljs)
  - _Requirements: All (project foundation)_

- [x] 2. Implement Exercise Library Manager
  - [x] 2.1 Create exercise data structure and validation
    - Define `make-exercise` function with name and weight
    - Implement weight validation (0.5 to 2.0 range)
    - Implement name validation (non-empty, unique)
    - _Requirements: 6.2, 6.3, 6.4, 7.2, 7.3_
  
  - [x] 2.2 Write property test for exercise validation
    - **Property 13: Exercise Data Integrity**
    - **Property 14: Weight Validation**
    - **Validates: Requirements 6.2, 6.3, 6.4**
  
  - [x] 2.3 Implement LocalStorage wrapper with js interop
    - Create functions for reading/writing to localStorage
    - Implement JSON serialization/deserialization
    - Handle storage unavailable scenarios
    - _Requirements: 6.5_
  
  - [x] 2.4 Write property test for storage round-trip
    - **Property 15: Storage Round-Trip**
    - **Validates: Requirements 6.5**
  
  - [x] 2.5 Implement library CRUD operations
    - Implement `load-library` function
    - Implement `save-library!` function
    - Implement `add-exercise!` with validation
    - Implement `get-all-exercises` function
    - Implement `exercise-exists?` predicate
    - _Requirements: 6.1, 6.5, 7.1, 7.4, 7.5_
  
  - [x] 2.6 Write property test for add exercise operations
    - **Property 16: Add Exercise Validation**
    - **Property 17: Add Exercise Persistence**
    - **Validates: Requirements 7.2, 7.3, 7.4, 7.5, 7.6**
  
  - [x] 2.7 Implement default exercise initialization
    - Create `initialize-defaults!` function with 10 default exercises
    - Implement first-run detection and initialization
    - _Requirements: 6.1, 6.6_
  
  - [x] 2.8 Write unit tests for library manager
    - Test default initialization with specific exercises
    - Test duplicate name rejection with error messages
    - Test corrupted storage recovery
    - _Requirements: 6.1, 6.4, 6.6, 7.4_

- [ ] 3. Implement Session Generator
  - [x] 3.1 Create session data structure
    - Define session plan structure with exercises and durations
    - Implement session configuration structure
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [x] 3.2 Implement weighted time distribution algorithm
    - Calculate base time from total duration and sum of weights
    - Assign time to each exercise based on weight
    - Handle rounding and distribute remaining seconds
    - _Requirements: 2.4, 2.5_
  
  - [x] 3.3 Write property test for time conservation
    - **Property 5: Time Conservation**
    - **Validates: Requirements 2.5**
  
  - [x] 3.4 Implement exercise selection with no-repeat logic
    - Create round-robin selection without repetition
    - Ensure all library exercises used before repeating
    - _Requirements: 2.2, 2.3_
  
  - [x] 3.5 Write property test for exercise selection
    - **Property 2: Minimum Exercise Count**
    - **Property 3: Exercise Library Membership**
    - **Property 4: No Repetition Until Library Exhausted**
    - **Validates: Requirements 2.1, 2.2, 2.3**
  
  - [ ] 3.6 Implement `generate-session` function
    - Integrate time distribution and exercise selection
    - Convert minutes to seconds
    - Return complete session plan
    - _Requirements: 1.2, 2.1, 2.2, 2.3, 2.4, 2.5_
  
  - [ ] 3.7 Write property test for session configuration
    - **Property 1: Session Configuration Round-Trip**
    - **Validates: Requirements 1.1, 1.2, 1.3**
  
  - [ ] 3.8 Write unit tests for session generator
    - Test default 5-minute session duration
    - Test single-exercise session
    - Test specific weight calculations with known values
    - _Requirements: 1.4, 2.1_

- [ ] 4. Checkpoint - Ensure core logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Timer Manager
  - [x] 5.1 Create timer state structure
    - Define timer state with current exercise index, remaining seconds, session state
    - Implement state validation (not_started, running, paused, completed)
    - _Requirements: 8.1, 8.2, 8.3_
  
  - [x] 5.2 Write property test for session state validity
    - **Property 18: Session State Validity**
    - **Validates: Requirements 8.3**
  
  - [x] 5.3 Implement timer control functions
    - Implement `start!` function with js/setInterval
    - Implement `pause!` function with interval clearing
    - Implement `restart!` function with state reset
    - Implement `get-state` function
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [x] 5.4 Write property test for pause/resume behavior
    - **Property 11: Pause Preserves State**
    - **Validates: Requirements 5.2, 5.4**
  
  - [x] 5.5 Write property test for restart behavior
    - **Property 12: Restart Resets Session**
    - **Validates: Requirements 5.5**
  
  - [x] 5.6 Implement timer tick logic
    - Decrement remaining seconds each tick
    - Trigger exercise advancement when reaching zero
    - Trigger session completion on final exercise
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  
  - [x] 5.7 Write property test for timer countdown
    - **Property 7: Timer Countdown Behavior**
    - **Property 8: Exercise Advancement**
    - **Property 9: Session Completion**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**
  
  - [x] 5.8 Implement callback registration
    - Implement `on-tick` callback registration
    - Implement `on-exercise-change` callback registration
    - Implement `on-complete` callback registration
    - _Requirements: 3.4, 4.3, 4.4_
  
  - [x] 5.9 Write unit tests for timer manager
    - Test specific pause/resume scenarios
    - Test completion message display
    - Test callback invocation
    - _Requirements: 4.4, 5.2, 5.4_

- [x] 6. Implement Import/Export functionality
  - [x] 6.1 Implement export to JSON
    - Create `export-to-json` function with serialization
    - Generate filename with timestamp
    - Trigger browser download
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [x] 6.2 Write property test for export filename format
    - **Property 20: Export Filename Format**
    - **Validates: Requirements 10.5**
  
  - [x] 6.3 Implement import from JSON with validation
    - Parse JSON file content
    - Validate JSON structure and exercise data
    - Handle malformed JSON errors
    - _Requirements: 11.1, 11.2, 11.3, 11.4_
  
  - [x] 6.4 Write property test for import validation
    - **Property 21: Import Validation**
    - **Property 22: Import Preserves Library on Error**
    - **Validates: Requirements 11.2, 11.4, 11.5**
  
  - [x] 6.5 Implement import merge logic
    - Add new exercises from import
    - Detect conflicts (same name, different weight)
    - Skip identical duplicates
    - Return conflict information for user resolution
    - _Requirements: 11.6, 11.7, 11.8_
  
  - [x] 6.6 Write property test for import merge behavior
    - **Property 19: Export-Import Round-Trip**
    - **Property 23: Import Merge Behavior**
    - **Property 24: Import Conflict Detection**
    - **Property 25: Import Persistence**
    - **Validates: Requirements 10.2, 10.4, 11.3, 11.6, 11.7, 11.8, 11.9**
  
  - [x] 6.7 Write unit tests for import/export
    - Test specific conflict resolution scenarios
    - Test error messages for invalid JSON
    - Test file download trigger
    - _Requirements: 11.5, 11.7_

- [x] 7. Checkpoint - Ensure all business logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement State Management
  - [x] 8.1 Create global app state atom
    - Define app-state structure with exercises, session, timer, and UI state
    - Initialize with Reagent atom
    - _Requirements: All (state foundation)_
  
  - [x] 8.2 Implement state update functions
    - Create helper functions for updating exercises
    - Create helper functions for updating session
    - Create helper functions for updating timer state
    - Create helper functions for updating UI state
    - _Requirements: 8.4_
  
  - [x] 8.3 Write unit tests for state management
    - Test atomic state transitions
    - Test state update helpers
    - _Requirements: 8.4_

- [x] 9. Implement time formatting utility
  - [x] 9.1 Create MM:SS formatter
    - Implement function to convert seconds to MM:SS format
    - Ensure zero-padding for minutes and seconds
    - _Requirements: 4.5_
  
  - [x] 9.2 Write property test for time format
    - **Property 10: Time Format Validation**
    - **Validates: Requirements 4.5**

- [ ] 10. Implement UI Components
  - [ ] 10.1 Create configuration panel component
    - Build form for session duration input
    - Add start session button
    - Implement default 5-minute value
    - Wire to session generator
    - _Requirements: 1.1, 1.3, 1.4_
  
  - [ ] 10.2 Create exercise display component
    - Display current exercise name
    - Display exercise progress (X of Y)
    - Add visual indication for active exercise
    - Ensure 100ms update responsiveness
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [ ] 10.3 Write property test for display information
    - **Property 6: Display Required Information**
    - **Validates: Requirements 3.1, 3.2**
  
  - [ ] 10.4 Create timer display component
    - Display countdown in MM:SS format
    - Update every second via timer callback
    - _Requirements: 4.2, 4.5_
  
  - [ ] 10.5 Create control panel component
    - Add pause button (visible when running)
    - Add resume button (visible when paused)
    - Add restart button (always visible)
    - Wire to timer manager functions
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [ ] 10.6 Create completion screen component
    - Display completion message
    - Show session summary
    - Provide option to start new session
    - _Requirements: 4.4_
  
  - [ ] 10.7 Create exercise library panel component
    - Display list of all exercises with names and weights
    - Add button to open add exercise dialog
    - Add export button
    - Add import button
    - _Requirements: 6.1, 7.1, 10.1, 11.1_
  
  - [ ] 10.8 Create add exercise dialog component
    - Build form with name and weight inputs
    - Implement validation and error display
    - Wire to add-exercise! function
    - _Requirements: 7.1, 7.2, 7.3, 7.4_
  
  - [ ] 10.9 Create import conflict dialog component
    - Display conflicts with existing vs imported values
    - Provide choice buttons for each conflict
    - Handle user selections and apply merge
    - _Requirements: 11.7_
  
  - [ ] 10.10 Create root app component
    - Compose all components
    - Handle conditional rendering based on session state
    - Initialize library on mount
    - _Requirements: All (UI integration)_
  
  - [ ] 10.11 Write unit tests for UI components
    - Test configuration panel rendering and interaction
    - Test exercise display with specific data
    - Test timer display formatting
    - Test control panel button states
    - Test completion screen rendering
    - _Requirements: 1.4, 3.1, 3.2, 4.5, 5.1_

- [ ] 11. Implement responsive CSS styling
  - [ ] 11.1 Create base styles and layout
    - Set up CSS file structure
    - Implement flexbox/grid layout
    - Define color scheme and typography
    - _Requirements: 12.1, 12.2_
  
  - [ ] 11.2 Implement responsive breakpoints
    - Add mobile styles (320px - 767px)
    - Add tablet styles (768px - 1023px)
    - Add desktop styles (1024px+)
    - Ensure 16px minimum font size on mobile
    - _Requirements: 12.1, 12.2, 12.4_
  
  - [ ] 11.3 Implement touch and mouse accessibility
    - Ensure buttons are touch-friendly (min 44px)
    - Add hover states for mouse users
    - Test on touch and mouse devices
    - _Requirements: 12.3_
  
  - [ ] 11.4 Write unit tests for responsive behavior
    - Test layout at different viewport widths
    - Test font size on mobile
    - _Requirements: 12.1, 12.4_

- [ ] 12. Implement accessibility features
  - [ ] 12.1 Add keyboard navigation
    - Ensure all controls are keyboard accessible
    - Implement logical tab order
    - Add keyboard shortcuts for play/pause
    - _Requirements: 12.3_
  
  - [ ] 12.2 Add ARIA labels and roles
    - Add ARIA labels to timer display
    - Add ARIA labels to exercise information
    - Add ARIA live regions for dynamic updates
    - _Requirements: 3.1, 4.2_
  
  - [ ] 12.3 Ensure color contrast compliance
    - Verify WCAG AA compliance for all text
    - Add clear focus indicators
    - _Requirements: 12.1, 12.2_

- [ ] 13. Integration and final wiring
  - [ ] 13.1 Wire timer callbacks to UI updates
    - Connect on-tick to timer display updates
    - Connect on-exercise-change to exercise display updates
    - Connect on-complete to completion screen display
    - _Requirements: 3.4, 4.2, 4.3, 4.4_
  
  - [ ] 13.2 Wire library operations to UI
    - Connect add exercise to library panel refresh
    - Connect import/export to file operations
    - Handle error display in UI
    - _Requirements: 7.5, 7.6, 10.3, 11.5_
  
  - [ ] 13.3 Implement localStorage initialization
    - Load library on app mount
    - Initialize defaults if storage empty
    - Handle storage unavailable scenario
    - _Requirements: 6.5, 6.6_
  
  - [ ] 13.4 Write integration tests
    - Test complete user workflow: configure → start → pause → resume → complete
    - Test add exercise → start session → verify exercise appears
    - Test export → import → verify library preserved
    - _Requirements: 1.1, 2.1, 4.1, 5.2, 5.4, 7.5, 10.2, 11.3_

- [ ] 14. Final checkpoint - Ensure all tests pass
  - Run complete test suite
  - Verify all property tests pass with 100+ iterations
  - Verify all unit tests pass
  - Test in multiple browsers (Chrome, Firefox, Safari)
  - Test on mobile and desktop devices
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks are required for comprehensive implementation
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties with 100+ iterations
- Unit tests validate specific examples, edge cases, and error conditions
- Checkpoints ensure incremental validation throughout implementation
- All property tests must include metadata tags: `{:feature "exercise-timer-app" :property N :description "..."}`
