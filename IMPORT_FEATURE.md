# Import Feature Documentation

## Overview

The Exercise Timer App now includes a fully functional import feature that allows users to import exercise libraries from JSON files with intelligent conflict resolution.

## How It Works

### 1. File Selection
- Click the "Import Library" button in the Exercise Library panel
- A hidden file input opens the browser's file picker
- Select a JSON file with the correct format
- The file is read and parsed automatically

### 2. Validation
The import process validates:
- JSON syntax is correct
- Required fields are present (`version`, `exercises`)
- Each exercise has `name` and `weight` fields
- Exercise names are non-empty strings
- Weights are between 0.5 and 2.0

If validation fails, an error message is displayed and the import is cancelled.

### 3. Conflict Detection
The app compares imported exercises with your existing library:

- **New exercises**: Exercises with names that don't exist in your library
- **Identical duplicates**: Exercises with the same name AND weight
- **Conflicts**: Exercises with the same name but DIFFERENT weights

### 4. Automatic Merge (No Conflicts)
If there are no conflicts:
- New exercises are added automatically
- Identical duplicates are skipped
- A success message shows the results:
  - Number of exercises added
  - Number of duplicates skipped
  - Number of exercises updated

### 5. Conflict Resolution Dialog
If conflicts are detected, a dialog appears showing:
- Each conflicting exercise name
- Existing weight in your library
- Imported weight from the file
- Radio buttons to choose which version to keep

For each conflict, you can choose:
- **Keep existing**: Ignore the imported version, keep your current weight
- **Use imported**: Replace your current weight with the imported weight

After making your selections, click "Import" to complete the merge.

## File Format

Import files must be valid JSON with this structure:

```json
{
  "version": 1,
  "exercises": [
    {
      "name": "Exercise Name",
      "weight": 1.2,
      "enabled": true
    }
  ]
}
```

### Required Fields
- `version`: Must be `1` (for future compatibility)
- `exercises`: Array of exercise objects
- `name`: Non-empty string (required for each exercise)
- `weight`: Number between 0.5 and 2.0 (required for each exercise)

### Optional Fields
- `enabled`: Boolean (defaults to `true` if not specified)

## Example Import File

See `example-import.json` in the project root for a sample import file:

```json
{
  "version": 1,
  "exercises": [
    {"name": "Push-ups", "weight": 1.5},
    {"name": "Yoga Flow", "weight": 0.9},
    {"name": "Box Jumps", "weight": 1.7},
    {"name": "Bicycle Crunches", "weight": 1.0}
  ]
}
```

This example includes:
- One conflict: "Push-ups" exists in default library with weight 1.2
- Three new exercises: "Yoga Flow", "Box Jumps", "Bicycle Crunches"

## User Experience

### Success Flow (No Conflicts)
1. Click "Import Library"
2. Select JSON file
3. See success alert with summary
4. Library is updated immediately
5. New exercises appear in the list

### Conflict Resolution Flow
1. Click "Import Library"
2. Select JSON file
3. Conflict dialog appears
4. Review each conflict
5. Choose which version to keep (defaults to "Keep existing")
6. Click "Import" to complete
7. See success alert with summary
8. Library is updated with your choices

### Error Handling
- **Invalid JSON**: "Failed to parse JSON: [error details]"
- **Missing fields**: "Import data missing required field: [field name]"
- **Invalid weight**: "Invalid exercise weight: [value] (must be between 0.5 and 2.0)"
- **Invalid name**: "Invalid exercise name: [name]"
- **Empty file**: "Import data contains no exercises"

## Technical Implementation

### Components
- **File Input**: Hidden input with `.json` accept filter
- **Import Handler**: `handle-import-file!` function reads and processes file
- **Conflict Dialog**: `import-conflict-dialog` component for resolution UI
- **Complete Import**: `complete-import!` function merges with resolutions

### State Management
UI state includes:
- `show-import-dialog`: Boolean to show/hide conflict dialog
- `import-exercises`: Array of exercises being imported
- `import-conflicts`: Array of detected conflicts
- `conflict-resolutions`: Map of user's choices for each conflict

### Library Functions
From `exercise-timer.library` namespace:
- `import-from-json`: Parse and validate JSON, detect conflicts
- `merge-and-save-import!`: Merge exercises with conflict resolutions
- `parse-import-json`: Validate JSON structure
- `detect-conflicts`: Find exercises with same name, different weight
- `merge-exercises`: Apply conflict resolutions and build final library

## Accessibility

The import feature is fully accessible:
- File input is keyboard accessible (Tab to button, Enter to open)
- Conflict dialog has proper ARIA labels and roles
- Radio buttons are keyboard navigable
- Clear focus indicators on all interactive elements
- Screen reader announces conflict information

## Testing

To test the import feature:

1. **Export your current library** to create a backup
2. **Modify the exported file** to create conflicts
3. **Import the modified file** to test conflict resolution
4. **Use example-import.json** to test with sample data

### Test Scenarios
- Import file with only new exercises (no conflicts)
- Import file with identical duplicates (should skip)
- Import file with conflicts (should show dialog)
- Import invalid JSON (should show error)
- Import file with invalid weights (should show error)
- Cancel import dialog (should not change library)

## Future Enhancements

Potential improvements:
- Drag-and-drop file upload
- Preview imported exercises before merging
- Bulk conflict resolution (keep all existing / use all imported)
- Import from URL
- Merge multiple files at once
- Undo last import
