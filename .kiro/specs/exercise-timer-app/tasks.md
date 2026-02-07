# Implementation Plan: Exercise Timer App - New Features

## Overview

This plan implements new features for the exercise timer app including equipment filtering, difficulty adjustments, time constraints (20s min, 2min max), progressive difficulty arrangement, session progress tracking, skip exercise functionality, web search integration, and React 18 compatibility.

## Tasks

- [ ] 1. Update Exercise data model to include equipment
  - [x] 1.1 Update make-exercise function to accept equipment parameter
    - Modify exercise data structure to include :equipment field (vector of strings)
    - Update validation to ensure equipment is a vector
    - _Requirements: 6.2, 7.4_
  
  - [ ]* 1.2 Write property test for exercise equipment field
    - **Property 34: Exercise Equipment Field**
    - **Validates: Requirements 7.4**
  
  - [x] 1.3 Update default exercises to include equipment
    - Add equipment field to all 10 default exercises
    - Most exercises: ["None"], Wall Sit: ["A wall"]
    - _Requirements: 6.1, 6.6_
  
  - [x] 1.4 Update LocalStorage schema to include equipment
    - Modify JSON serialization to include equipment field
    - Handle backward compatibility for existing stored data
    - _Requirements: 6.5_
  
  - [ ]* 1.5 Write unit tests for equipment data migration
    - Test loading old format (without equipment) and adding default equipment
    - Test loading new format with equipment
    - _Requirements: 6.5_

- [ ] 2. Rename "weight" to "difficulty" throughout codebase
  - [x] 2.1 Update all references in library namespace
    - Rename :weight to :difficulty in data structures
    - Update all function parameters and documentation
    - _Requirements: 6.2, 6.3_
  
  - [x] 2.2 Update all references in session namespace
    - Update time distribution algorithm to use :difficulty
    - Update all variable names and comments
    - _Requirements: 2.4_
  
  - [x] 2.3 Update all references in UI components
    - Update form labels from "Weight" to "Difficulty"
    - Update display text throughout UI
    - _Requirements: 7.3_
  
  - [x] 2.4 Update all test files
    - Rename weight references to difficulty in all tests
    - Update test data and assertions
    - _Requirements: All (testing)_

- [ ] 3. Implement alphabetical sorting for exercise library
  - [x] 3.1 Add sort-by-name function to library namespace
    - Implement function to sort exercises alphabetically by name
    - Apply sorting when loading from storage
    - Apply sorting after any library modification
    - _Requirements: 6.7_
  
  - [ ]* 3.2 Write property test for alphabetical sorting
    - **Property 33: Alphabetical Library Sorting**
    - **Validates: Requirements 6.7**
  
  - [x] 3.3 Update UI to display sorted exercises
    - Ensure exercise library panel shows sorted list
    - _Requirements: 6.7_

- [ ] 4. Implement equipment filtering system
  - [x] 4.1 Add get-equipment-types function to library namespace
    - Extract all unique equipment types from exercise library
    - Return set of equipment type strings
    - _Requirements: 13.1_
  
  - [x] 4.2 Add filter-by-equipment function to library namespace
    - Filter exercises to only those requiring selected equipment or "None"
    - Accept equipment set as parameter
    - _Requirements: 2.10, 13.3_
  
  - [ ]* 4.3 Write property test for equipment filtering
    - **Property 30: Equipment Filtering**
    - **Validates: Requirements 2.10, 13.3**
  
  - [x] 4.4 Update session configuration state to include equipment selection
    - Add :equipment field to session-config (set of strings)
    - Initialize with all equipment types by default
    - _Requirements: 13.2, 13.4_
  
  - [ ]* 4.5 Write property test for equipment selection storage
    - **Property 37: Equipment Selection Storage**
    - **Validates: Requirements 13.2**
  
  - [x] 4.6 Update generate-session to filter by equipment
    - Apply equipment filter before generating session
    - Pass selected equipment from config
    - _Requirements: 2.10_

- [ ] 5. Implement time constraints (20s min, 2min max)
  - [x] 5.1 Add apply-time-constraints function to session namespace
    - Enforce minimum 20 seconds per exercise
    - Enforce maximum 120 seconds (2 minutes) per exercise
    - _Requirements: 2.6, 2.7_
  
  - [ ]* 5.2 Write property test for minimum exercise duration
    - **Property 26: Minimum Exercise Duration**
    - **Validates: Requirements 2.6**
  
  - [ ]* 5.3 Write property test for maximum exercise duration
    - **Property 27: Maximum Exercise Duration**
    - **Validates: Requirements 2.7**
  
  - [x] 5.4 Add split-long-exercises function to session namespace
    - Split exercises exceeding 2 minutes into multiple occurrences
    - Distribute split exercises throughout session
    - _Requirements: 2.8_
  
  - [ ]* 5.5 Write property test for exercise splitting
    - **Property 28: Exercise Splitting for Long Durations**
    - **Validates: Requirements 2.8**
  
  - [x] 5.6 Integrate time constraints into generate-session
    - Apply constraints after initial time distribution
    - Apply splitting before final arrangement
    - _Requirements: 2.6, 2.7, 2.8_

- [ ] 6. Implement progressive difficulty arrangement
  - [x] 6.1 Add sort-by-difficulty function to session namespace
    - Sort exercises by difficulty value (ascending)
    - Return sorted exercise list
    - _Requirements: 2.9_
  
  - [x] 6.2 Add arrange-progressive-difficulty function to session namespace
    - Place lower difficulty exercises at start and end
    - Place higher difficulty exercises in middle
    - Maintain time conservation
    - _Requirements: 2.9_
  
  - [ ]* 6.3 Write property test for progressive difficulty arrangement
    - **Property 29: Progressive Difficulty Arrangement**
    - **Validates: Requirements 2.9**
  
  - [x] 6.4 Integrate progressive arrangement into generate-session
    - Apply arrangement as final step before returning session plan
    - _Requirements: 2.9_

- [x] 7. Checkpoint - Ensure session generation tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Implement session progress tracking
  - [x] 8.1 Add total-elapsed-seconds to timer state
    - Track total elapsed time across all exercises
    - Increment on each timer tick
    - Reset on session restart
    - _Requirements: 9.3_
  
  - [x] 8.2 Add calculate-progress-percentage function to timer namespace
    - Calculate (elapsed / total) × 100
    - Return percentage value (0-100)
    - _Requirements: 9.3_
  
  - [ ]* 8.3 Write property test for progress calculation
    - **Property 35: Progress Calculation**
    - **Validates: Requirements 9.3**
  
  - [x] 8.4 Update timer tick logic to track elapsed time
    - Increment total-elapsed-seconds on each tick
    - Maintain across exercise transitions
    - _Requirements: 9.1, 9.3_

- [ ] 9. Implement skip exercise functionality
  - [x] 9.1 Add skip-exercise! function to timer namespace
    - Cancel current exercise
    - Calculate remaining time for current exercise
    - Reallocate time to future exercises or add extra exercise
    - _Requirements: 5.6_
  
  - [ ]* 9.2 Write property test for skip exercise time conservation
    - **Property 31: Skip Exercise Time Conservation**
    - **Validates: Requirements 5.6**
  
  - [x] 9.3 Add time reallocation logic
    - Distribute remaining time proportionally to future exercises
    - If no future exercises, add a new exercise from library
    - _Requirements: 5.7_
  
  - [ ]* 9.4 Write unit tests for skip exercise scenarios
    - Test skipping first exercise
    - Test skipping middle exercise
    - Test skipping last exercise
    - _Requirements: 5.6, 5.7_

- [ ] 10. Implement web search functionality
  - [x] 10.1 Add search-exercise function to timer namespace
    - Generate search URL with exercise name
    - Use js/window.open to open in new tab
    - _Requirements: 5.9_
  
  - [ ]* 10.2 Write property test for search URL generation
    - **Property 32: Search URL Generation**
    - **Validates: Requirements 5.9**
  
  - [x] 10.3 Add search button to control panel
    - Show only when session is paused
    - Wire to search-exercise function
    - _Requirements: 5.8_
  
  - [ ]* 10.4 Write unit test for search button visibility
    - Test button appears when paused
    - Test button hidden when running
    - _Requirements: 5.8_

- [ ] 11. Implement dynamic difficulty adjustment
  - [x] 11.1 Add update-exercise-difficulty! function to library namespace
    - Update difficulty for specified exercise
    - Persist change to local storage immediately
    - _Requirements: 14.2, 14.3_
  
  - [ ]* 11.2 Write property test for difficulty adjustment updates library
    - **Property 38: Difficulty Adjustment Updates Library**
    - **Validates: Requirements 14.2**
  
  - [ ]* 11.3 Write property test for difficulty adjustment persistence
    - **Property 39: Difficulty Adjustment Persistence**
    - **Validates: Requirements 14.3**
  
  - [x] 11.4 Add difficulty adjustment controls to exercise display
    - Add increase/decrease buttons
    - Show current difficulty value
    - Only visible during active exercise
    - _Requirements: 14.1_
  
  - [x] 11.5 Wire difficulty controls to update function
    - Call update-exercise-difficulty! on button click
    - Update UI to show new difficulty
    - Ensure current timer is not affected
    - _Requirements: 14.2, 14.3, 14.4_
  
  - [ ]* 11.6 Write property test for difficulty adjustment preserves time
    - **Property 40: Difficulty Adjustment Preserves Time**
    - **Validates: Requirements 14.4**

- [x] 12. Checkpoint - Ensure timer and control tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Update UI components for new features
  - [x] 13.1 Add equipment checkboxes to configuration panel
    - Display checkbox for each unique equipment type
    - Initialize all as checked (default)
    - Store selection in session-config state
    - _Requirements: 13.1, 13.2, 13.4_
  
  - [ ]* 13.2 Write property test for equipment types display
    - **Property 36: Equipment Types Display**
    - **Validates: Requirements 13.1**
  
  - [x] 13.3 Update add-exercise-dialog to include equipment field
    - Add multi-select or text input for equipment
    - Allow multiple equipment types per exercise
    - Default to ["None"]
    - _Requirements: 7.4_
  
  - [x] 13.4 Create progress-bar component
    - Display visual progress indicator
    - Calculate percentage from timer state
    - Update in real-time as session progresses
    - Consider using title background as progress bar
    - _Requirements: 9.1, 9.2_
  
  - [x] 13.5 Add skip button to control panel
    - Show when session is running
    - Wire to skip-exercise! function
    - _Requirements: 5.6_
  
  - [x] 13.6 Update control panel with search button
    - Show when session is paused
    - Wire to search-exercise function
    - _Requirements: 5.8, 5.9_
  
  - [x] 13.7 Add difficulty adjustment controls to exercise display
    - Add +/- buttons next to exercise name
    - Show current difficulty value
    - Only show during active exercise
    - _Requirements: 14.1_
  
  - [ ]* 13.8 Write unit tests for new UI components
    - Test equipment checkboxes rendering
    - Test progress bar calculation and display
    - Test skip button visibility and behavior
    - Test search button visibility and behavior
    - Test difficulty controls visibility and behavior
    - _Requirements: 9.1, 13.1, 14.1_

- [ ] 14. Update React 18 compatibility
  - [x] 14.1 Replace ReactDOM.render with createRoot
    - Import createRoot from react-dom/client
    - Update root component initialization
    - Remove deprecated render call
    - _Requirements: 15.1_
  
  - [ ]* 14.2 Verify no deprecation warnings
    - Run app and check browser console
    - Ensure no React 18 warnings appear
    - _Requirements: 15.2_

- [ ] 15. Update import/export to include equipment
  - [x] 15.1 Update export-to-json to include equipment field
    - Ensure equipment is serialized in JSON
    - _Requirements: 10.4_
  
  - [x] 15.2 Update import-from-json to handle equipment field
    - Parse equipment from imported JSON
    - Validate equipment is a vector
    - Handle missing equipment (default to ["None"])
    - _Requirements: 11.4_
  
  - [x] 15.3 Update conflict detection to consider equipment
    - Conflicts occur when name matches but difficulty OR equipment differs
    - _Requirements: 11.7_
  
  - [ ]* 15.4 Write unit tests for import/export with equipment
    - Test export includes equipment
    - Test import with equipment
    - Test import without equipment (backward compatibility)
    - Test conflict detection with equipment differences
    - _Requirements: 10.4, 11.4, 11.7_

- [ ] 16. Integration and final wiring
  - [x] 16.1 Wire equipment selection to session generation
    - Pass selected equipment from config to generate-session
    - Update session when equipment selection changes
    - _Requirements: 2.10, 13.2_
  
  - [x] 16.2 Wire progress bar to timer updates
    - Update progress bar on each timer tick
    - Calculate percentage from elapsed and total time
    - _Requirements: 9.1, 9.2_
  
  - [x] 16.3 Wire skip button to timer and session
    - Handle skip action
    - Update session plan with reallocated time
    - Advance to next exercise
    - _Requirements: 5.6, 5.7_
  
  - [x] 16.4 Wire difficulty controls to library updates
    - Update library on difficulty change
    - Persist to storage
    - Update UI to reflect change
    - _Requirements: 14.2, 14.3_
  
  - [ ]* 16.5 Write integration tests for new features
    - Test complete workflow: select equipment → start session → verify filtered exercises
    - Test skip exercise → verify time reallocation
    - Test adjust difficulty → verify persistence
    - Test progress bar updates throughout session
    - _Requirements: 2.10, 5.6, 9.1, 14.2_

- [ ] 17. Update CSS for new UI elements
  - [x] 17.1 Style equipment checkboxes
    - Ensure checkboxes are touch-friendly
    - Add clear labels
    - _Requirements: 12.3, 13.1_
  
  - [x] 17.2 Style progress bar
    - Create visual progress indicator
    - Consider using title background
    - Ensure visibility and accessibility
    - _Requirements: 9.1, 12.1_
  
  - [x] 17.3 Style difficulty adjustment controls
    - Make +/- buttons touch-friendly
    - Position near exercise name
    - _Requirements: 12.3, 14.1_
  
  - [x] 17.4 Style skip and search buttons
    - Consistent with existing control buttons
    - Clear visual distinction
    - _Requirements: 5.6, 5.8_

- [x] 18. Final checkpoint - Ensure all tests pass
  - Run complete test suite
  - Verify all property tests pass with 100+ iterations
  - Verify all unit tests pass
  - Test in multiple browsers (Chrome, Firefox, Safari)
  - Test on mobile and desktop devices
  - Verify no React 18 deprecation warnings
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks are required for comprehensive implementation of new features
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties with 100+ iterations
- Unit tests validate specific examples, edge cases, and error conditions
- Checkpoints ensure incremental validation throughout implementation
- All property tests must include metadata tags: `{:feature "exercise-timer-app" :property N :description "..."}`
- The "difficulty" terminology replaces "weight" throughout the application
- Equipment filtering ensures users only see exercises they can perform
- Time constraints (20s-2min) ensure exercises are appropriately sized
- Progressive difficulty arrangement provides better workout structure
- Session progress tracking gives users visibility into workout completion
