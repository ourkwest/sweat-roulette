(ns exercise-timer.library
  "Exercise Library Manager - handles CRUD operations for exercises with persistence")

;; Forward declaration
(declare save-library!)

;; ============================================================================
;; LocalStorage Constants
;; ============================================================================

(def ^:private storage-key "exercise-timer-library")
(def ^:private storage-version 1)

;; ============================================================================
;; LocalStorage Wrapper with JS Interop
;; ============================================================================

(defn- storage-available?
  "Check if localStorage is available in the browser.
   Returns true if localStorage is accessible, false otherwise.
   Handles cases where localStorage is disabled (e.g., private browsing mode).
   Validates: Requirements 6.5"
  []
  (try
    (let [test-key "__storage_test__"]
      (when (exists? js/localStorage)
        (.setItem js/localStorage test-key "test")
        (.removeItem js/localStorage test-key)
        true))
    (catch js/Error _
      false)))

(defn- write-to-storage!
  "Write data to localStorage with JSON serialization.
   
   Parameters:
   - data: ClojureScript data structure to store
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if storage is unavailable or write fails
   
   Validates: Requirements 6.5"
  [data]
  (try
    (if (storage-available?)
      (let [storage-data {:version storage-version
                          :exercises data}
            json-str (js/JSON.stringify (clj->js storage-data))]
        (.setItem js/localStorage storage-key json-str)
        {:ok true})
      {:error "LocalStorage is not available"})
    (catch js/Error e
      {:error (str "Failed to write to storage: " (.-message e))})))

(defn- migrate-exercise
  "Migrate an exercise to the current schema version.
   Adds default equipment field if missing (backward compatibility).
   
   Parameters:
   - exercise: Exercise map to migrate
   
   Returns:
   - Migrated exercise map with all required fields
   
   Validates: Requirements 6.5"
  [exercise]
  (if (contains? exercise :equipment)
    exercise
    ;; Add default equipment for backward compatibility
    (assoc exercise :equipment ["None"])))

(defn- read-from-storage
  "Read data from localStorage with JSON deserialization.
   Handles backward compatibility by migrating old data format.
   
   Returns:
   - {:ok exercises} on success with vector of exercises
   - {:error \"message\"} if storage is unavailable, empty, or corrupted
   
   Validates: Requirements 6.5"
  []
  (try
    (if (storage-available?)
      (if-let [json-str (.getItem js/localStorage storage-key)]
        (try
          (let [parsed (js->clj (js/JSON.parse json-str) :keywordize-keys true)
                exercises (:exercises parsed)]
            (if (vector? exercises)
              ;; Migrate exercises to ensure equipment field exists
              {:ok (mapv migrate-exercise exercises)}
              {:error "Invalid storage format: exercises must be a vector"}))
          (catch js/Error e
            {:error (str "Failed to parse storage data: " (.-message e))}))
        {:error "No data found in storage"})
      {:error "LocalStorage is not available"})
    (catch js/Error e
      {:error (str "Failed to read from storage: " (.-message e))})))

(defn- clear-storage!
  "Clear all data from localStorage.
   Used for testing and recovery from corrupted state.
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if storage is unavailable
   
   Validates: Requirements 6.5"
  []
  (try
    (if (storage-available?)
      (do
        (.removeItem js/localStorage storage-key)
        {:ok true})
      {:error "LocalStorage is not available"})
    (catch js/Error e
      {:error (str "Failed to clear storage: " (.-message e))})))

(defn clear-library-for-testing!
  "Clear the library state for testing purposes.
   This clears both the in-memory state and localStorage.
   
   Returns:
   - {:ok true}
   
   Side effects:
   - Resets library-state atom to empty vector
   - Clears localStorage
   
   Note: This is intended for testing only."
  []
  (save-library! [])
  (clear-storage!)
  {:ok true})

;; ============================================================================
;; Exercise Data Structure and Validation
;; ============================================================================

(defn valid-difficulty?
  "Check if difficulty is within valid range (0.5 to 2.0 inclusive).
   Validates: Requirements 6.3, 7.3"
  [difficulty]
  (and (number? difficulty)
       (>= difficulty 0.5)
       (<= difficulty 2.0)))

(defn valid-name?
  "Check if name is non-empty string.
   Validates: Requirements 6.2, 7.2"
  [name]
  (and (string? name)
       (not (empty? (clojure.string/trim name)))))

(defn valid-equipment?
  "Check if equipment is a valid vector.
   Validates: Requirements 7.4"
  [equipment]
  (vector? equipment))

(defn make-exercise
  "Create an exercise with name, difficulty, and equipment.
   Returns exercise map if valid, or error map if invalid.
   
   Parameters:
   - name: Non-empty string, must be unique in library
   - difficulty: Number between 0.5 and 2.0 inclusive
   - equipment: Vector of equipment type strings (e.g., [\"None\"], [\"A wall\"])
   
   Returns:
   - {:exercise {:name \"...\" :difficulty N :equipment [...]}} on success
   - {:error \"message\"} on validation failure
   
   Validates: Requirements 6.2, 6.3, 6.4, 7.2, 7.3, 7.4"
  [name difficulty equipment]
  (cond
    (not (valid-name? name))
    {:error "Exercise name must be a non-empty string"}
    
    (not (valid-difficulty? difficulty))
    {:error "Exercise difficulty must be between 0.5 and 2.0"}
    
    (not (valid-equipment? equipment))
    {:error "Exercise equipment must be a vector"}
    
    :else
    {:exercise {:name (clojure.string/trim name)
                :difficulty difficulty
                :equipment equipment}}))

;; ============================================================================
;; Library State Management
;; ============================================================================

(defonce ^:private library-state
  (atom []))

;; ============================================================================
;; Sorting Utility
;; ============================================================================

(defn sort-by-name
  "Sort exercises alphabetically by name in ascending order.
   
   Parameters:
   - exercises: vector of exercise maps
   
   Returns:
   - Vector of exercises sorted alphabetically by name
   
   Validates: Requirements 6.7"
  [exercises]
  (vec (sort-by :name exercises)))

;; ============================================================================
;; Default Exercise Initialization
;; ============================================================================

(def ^:private default-exercises
  "Default set of exercises to initialize the library.
   Validates: Requirements 6.1, 6.6"
  [{:name "Push-ups" :difficulty 1.2 :equipment ["None"]}
   {:name "Squats" :difficulty 1.0 :equipment ["None"]}
   {:name "Plank" :difficulty 1.5 :equipment ["None"]}
   {:name "Jumping Jacks" :difficulty 0.8 :equipment ["None"]}
   {:name "Lunges" :difficulty 1.0 :equipment ["None"]}
   {:name "Mountain Climbers" :difficulty 1.3 :equipment ["None"]}
   {:name "Burpees" :difficulty 1.8 :equipment ["None"]}
   {:name "High Knees" :difficulty 0.9 :equipment ["None"]}
   {:name "Sit-ups" :difficulty 1.0 :equipment ["None"]}
   {:name "Wall Sit" :difficulty 1.4 :equipment ["A wall"]}
   {:name "Russian Twists" :difficulty 1.1 :equipment ["None"]}
   {:name "Kneel to Stand" :difficulty 1.6 :equipment ["None"]}
   {:name "Air Punches" :difficulty 0.7 :equipment ["None"]}
   {:name "Plank Shoulder Taps" :difficulty 1.4 :equipment ["None"]}])

(defn initialize-defaults!
  "Initialize the exercise library with default exercises.
   This function should be called on first run when the library is empty.
   Default exercises are sorted alphabetically by name.
   
   Returns:
   - {:ok true :count N} on success with number of exercises initialized
   - {:error \"message\"} on failure
   
   Side effects:
   - Updates library-state atom with default exercises (sorted)
   - Persists to localStorage
   
   Validates: Requirements 6.1, 6.6, 6.7"
  []
  (let [sorted-defaults (sort-by-name default-exercises)]
    (reset! library-state sorted-defaults)
    (let [save-result (write-to-storage! sorted-defaults)]
      (if (contains? save-result :ok)
        {:ok true :count (count sorted-defaults)}
        save-result))))

;; ============================================================================
;; Library CRUD Operations
;; ============================================================================

(defn load-library
  "Load exercises from local storage into memory.
   If storage is empty or unavailable, initializes with default exercises.
   Returns exercises sorted alphabetically by name.
   
   Returns:
   - Vector of exercises sorted by name on success
   - Vector of default exercises sorted by name if storage is empty (first run)
   
   Side effects:
   - Updates library-state atom with loaded exercises (sorted)
   - Initializes defaults on first run
   
   Validates: Requirements 6.1, 6.5, 6.6, 6.7"
  []
  (let [result (read-from-storage)]
    (if (contains? result :ok)
      (let [exercises (:ok result)]
        (if (empty? exercises)
          ;; First run: storage exists but is empty, initialize defaults
          (do
            (initialize-defaults!)
            @library-state)
          ;; Normal case: return loaded exercises sorted by name
          (let [sorted-exercises (sort-by-name exercises)]
            (reset! library-state sorted-exercises)
            sorted-exercises)))
      ;; Storage doesn't exist or failed to read
      ;; Only initialize defaults if in-memory state is also empty
      (if (empty? @library-state)
        (do
          (initialize-defaults!)
          @library-state)
        ;; In-memory state has data, keep it (graceful degradation)
        @library-state))))

(defn save-library!
  "Save current library state to local storage.
   Exercises are sorted alphabetically by name before saving.
   
   Parameters:
   - exercises: Vector of exercise maps to save
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} on failure
   
   Side effects:
   - Updates library-state atom with sorted exercises
   - Writes sorted exercises to localStorage
   
   Validates: Requirements 6.5, 6.7, 7.5"
  [exercises]
  (let [sorted-exercises (sort-by-name exercises)]
    (reset! library-state sorted-exercises)
    (write-to-storage! sorted-exercises)))

(defn get-all-exercises
  "Get all exercises from current library state, sorted alphabetically by name.
   
   Returns:
   - Vector of all exercises in the library, sorted by name
   
   Validates: Requirements 6.1, 6.7"
  []
  (sort-by-name @library-state))

(defn exercise-exists?
  "Check if an exercise with the given name exists in the library.
   Name comparison is case-sensitive after trimming.
   
   Parameters:
   - name: Exercise name to check
   
   Returns:
   - true if exercise exists, false otherwise
   
   Validates: Requirements 6.4, 7.4"
  [name]
  (let [trimmed-name (clojure.string/trim name)
        exercises @library-state]
    (boolean (some #(= (:name %) trimmed-name) exercises))))

(defn add-exercise!
  "Add a new exercise to the library with validation.
   
   Parameters:
   - exercise: Exercise map with :name, :difficulty, and optionally :equipment keys
   
   Returns:
   - {:ok exercise} on success
   - {:error \"message\"} on validation failure
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage
   
   Validates: Requirements 6.4, 7.1, 7.2, 7.3, 7.4, 7.5"
  [exercise]
  (let [{:keys [name difficulty equipment]} exercise
        ;; Default equipment to ["None"] if not provided
        equipment (or equipment ["None"])]
    (cond
      ;; Validate name
      (not (valid-name? name))
      {:error "Exercise name must be a non-empty string"}
      
      ;; Validate difficulty
      (not (valid-difficulty? difficulty))
      {:error "Exercise difficulty must be between 0.5 and 2.0"}
      
      ;; Validate equipment
      (not (valid-equipment? equipment))
      {:error "Exercise equipment must be a vector"}
      
      ;; Check for duplicate name
      (exercise-exists? name)
      {:error (str "Exercise with name '" (clojure.string/trim name) "' already exists")}
      
      ;; All validations passed, add exercise
      :else
      (let [trimmed-exercise {:name (clojure.string/trim name)
                              :difficulty difficulty
                              :equipment equipment}
            updated-library (conj @library-state trimmed-exercise)
            save-result (save-library! updated-library)]
        (if (contains? save-result :ok)
          {:ok trimmed-exercise}
          save-result)))))

;; ============================================================================
;; Import/Export Functionality
;; ============================================================================

(defn- generate-timestamp
  "Generate a timestamp string for filenames.
   Format: YYYYMMDD-HHMMSS
   
   Returns:
   - String timestamp
   
   Validates: Requirements 10.5"
  []
  (let [now (js/Date.)
        year (.getFullYear now)
        month (inc (.getMonth now))
        day (.getDate now)
        hours (.getHours now)
        minutes (.getMinutes now)
        seconds (.getSeconds now)
        pad (fn [n] (if (< n 10) (str "0" n) (str n)))]
    (str year (pad month) (pad day) "-" (pad hours) (pad minutes) (pad seconds))))

(defn export-to-json
  "Export the exercise library to JSON format.
   
   Returns:
   - {:ok {:json string :filename string}} on success with JSON string and suggested filename
   - {:error \"message\"} on failure
   
   The returned JSON can be used to trigger a browser download.
   
   Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5"
  []
  (try
    (let [exercises @library-state
          export-data {:version storage-version
                       :exercises exercises}
          json-str (js/JSON.stringify (clj->js export-data) nil 2)
          timestamp (generate-timestamp)
          filename (str "exercise-library-" timestamp ".json")]
      {:ok {:json json-str
            :filename filename}})
    (catch js/Error e
      {:error (str "Failed to export library: " (.-message e))})))

(defn trigger-download
  "Trigger a browser download of the exported JSON.
   
   Parameters:
   - json-str: JSON string to download
   - filename: suggested filename for the download
   
   Side effects:
   - Creates a temporary blob URL and triggers browser download
   - Cleans up the blob URL after download
   
   Validates: Requirements 10.3"
  [json-str filename]
  (try
    (let [blob (js/Blob. #js [json-str] #js {:type "application/json"})
          url (js/URL.createObjectURL blob)
          link (.createElement js/document "a")]
      (set! (.-href link) url)
      (set! (.-download link) filename)
      (.appendChild (.-body js/document) link)
      (.click link)
      (.removeChild (.-body js/document) link)
      (js/URL.revokeObjectURL url)
      {:ok true})
    (catch js/Error e
      {:error (str "Failed to trigger download: " (.-message e))})))

(defn export-and-download!
  "Export the exercise library and trigger a browser download.
   
   This is a convenience function that combines export-to-json and trigger-download.
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} on failure
   
   Side effects:
   - Triggers browser download of JSON file
   
   Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5"
  []
  (let [export-result (export-to-json)]
    (if (contains? export-result :ok)
      (let [{:keys [json filename]} (:ok export-result)]
        (trigger-download json filename))
      export-result)))


(defn- validate-import-exercise
  "Validate a single exercise from import data.
   Handles backward compatibility by adding default equipment if missing.
   
   Parameters:
   - exercise: exercise map to validate
   
   Returns:
   - {:ok exercise} if valid (with equipment field added if missing)
   - {:error \"message\"} if invalid
   
   Validates: Requirements 11.4"
  [exercise]
  (cond
    (not (map? exercise))
    {:error "Exercise must be a map"}
    
    (not (contains? exercise :name))
    {:error "Exercise missing required field: name"}
    
    (not (contains? exercise :difficulty))
    {:error "Exercise missing required field: difficulty"}
    
    (not (valid-name? (:name exercise)))
    {:error (str "Invalid exercise name: " (:name exercise))}
    
    (not (valid-difficulty? (:difficulty exercise)))
    {:error (str "Invalid exercise difficulty: " (:difficulty exercise) " (must be between 0.5 and 2.0)")}
    
    ;; Validate equipment if present
    (and (contains? exercise :equipment)
         (not (valid-equipment? (:equipment exercise))))
    {:error (str "Invalid exercise equipment: " (:equipment exercise) " (must be a vector)")}
    
    :else
    ;; Add default equipment if missing (backward compatibility)
    {:ok (if (contains? exercise :equipment)
           exercise
           (assoc exercise :equipment ["None"]))}))

(defn- validate-import-data
  "Validate imported JSON data structure.
   
   Parameters:
   - data: parsed JSON data
   
   Returns:
   - {:ok exercises} if valid with vector of exercises (with equipment field added if missing)
   - {:error \"message\"} if invalid
   
   Validates: Requirements 11.2, 11.4"
  [data]
  (cond
    (not (map? data))
    {:error "Import data must be a JSON object"}
    
    (not (contains? data :exercises))
    {:error "Import data missing required field: exercises"}
    
    (not (vector? (:exercises data)))
    {:error "Exercises field must be an array"}
    
    (empty? (:exercises data))
    {:error "Import data contains no exercises"}
    
    :else
    ;; Validate each exercise
    (let [exercises (:exercises data)
          validation-results (map validate-import-exercise exercises)
          errors (filter #(contains? % :error) validation-results)]
      (if (empty? errors)
        ;; Extract validated exercises (with equipment field added if missing)
        {:ok (mapv :ok validation-results)}
        {:error (str "Invalid exercises: " (clojure.string/join ", " (map :error errors)))}))))

(defn parse-import-json
  "Parse and validate JSON string for import.
   
   Parameters:
   - json-str: JSON string to parse
   
   Returns:
   - {:ok exercises} on success with vector of validated exercises
   - {:error \"message\"} on failure
   
   Validates: Requirements 11.2, 11.3, 11.4"
  [json-str]
  (try
    (let [parsed (js->clj (js/JSON.parse json-str) :keywordize-keys true)
          validation-result (validate-import-data parsed)]
      validation-result)
    (catch js/Error e
      {:error (str "Failed to parse JSON: " (.-message e))})))

(defn- detect-conflicts
  "Detect conflicts between imported exercises and existing library.
   
   A conflict occurs when an exercise with the same name exists in both
   the current library and the import, but with different difficulties or equipment.
   
   Parameters:
   - imported-exercises: vector of exercises to import
   - existing-exercises: vector of current library exercises
   
   Returns:
   - vector of conflict maps: [{:name string :existing-difficulty number :imported-difficulty number 
                                 :existing-equipment vector :imported-equipment vector}]
   
   Validates: Requirements 11.7, 11.8"
  [imported-exercises existing-exercises]
  (let [existing-map (into {} (map (fn [ex] [(:name ex) ex]) existing-exercises))]
    (reduce
     (fn [conflicts imported-ex]
       (let [name (:name imported-ex)
             imported-difficulty (:difficulty imported-ex)
             imported-equipment (:equipment imported-ex)
             existing-ex (get existing-map name)]
         (if existing-ex
           (let [existing-difficulty (:difficulty existing-ex)
                 existing-equipment (:equipment existing-ex)]
             ;; Conflict if difficulty OR equipment differs
             (if (or (not= existing-difficulty imported-difficulty)
                     (not= existing-equipment imported-equipment))
               (conj conflicts {:name name
                                :existing-difficulty existing-difficulty
                                :imported-difficulty imported-difficulty
                                :existing-equipment existing-equipment
                                :imported-equipment imported-equipment})
               conflicts))
           conflicts)))
     []
     imported-exercises)))

(defn- merge-exercises
  "Merge imported exercises with existing library.
   
   Strategy:
   - Skip exercises that are identical (same name, difficulty, and equipment)
   - Add exercises with new names
   - For conflicts (same name, different difficulty or equipment), use the resolution map
   
   Parameters:
   - imported-exercises: vector of exercises to import
   - existing-exercises: vector of current library exercises
   - conflict-resolutions: map of {name -> :keep-existing or :use-imported}
   
   Returns:
   - {:added [...] :skipped [...] :updated [...] :merged-library [...]} with lists of exercise names and final library
   
   Validates: Requirements 11.6, 11.7, 11.8"
  [imported-exercises existing-exercises conflict-resolutions]
  (let [existing-map (into {} (map (fn [ex] [(:name ex) ex]) existing-exercises))
        ;; First pass: determine what to do with each imported exercise
        result (reduce
                (fn [acc imported-ex]
                  (let [name (:name imported-ex)
                        existing-ex (get existing-map name)]
                    (cond
                      ;; Identical exercise (same name, difficulty, and equipment), skip
                      (and existing-ex 
                           (= (:difficulty existing-ex) (:difficulty imported-ex))
                           (= (:equipment existing-ex) (:equipment imported-ex)))
                      (update acc :skipped conj name)
                      
                      ;; New exercise, add
                      (nil? existing-ex)
                      (update acc :added conj name)
                      
                      ;; Conflict, check resolution
                      :else
                      (let [resolution (get conflict-resolutions name :keep-existing)]
                        (if (= resolution :use-imported)
                          (update acc :updated conj name)
                          (update acc :skipped conj name))))))
                {:added []
                 :skipped []
                 :updated []}
                imported-exercises)
        ;; Second pass: build the final library
        ;; Start with existing exercises, but exclude those that will be updated
        names-to-update (set (:updated result))
        filtered-existing (filterv #(not (contains? names-to-update (:name %))) existing-exercises)
        ;; Add new and updated exercises from import
        names-to-add (set (concat (:added result) (:updated result)))
        exercises-to-add (filterv #(contains? names-to-add (:name %)) imported-exercises)
        final-library (vec (concat filtered-existing exercises-to-add))]
    (assoc result :merged-library final-library)))

(defn import-from-json
  "Import exercises from JSON string with conflict detection.
   
   This function parses the JSON, validates the exercises, detects conflicts
   with the existing library, and returns information about what would be
   imported. It does NOT automatically merge - the caller must handle conflicts
   and call merge-and-save-import! to complete the import.
   
   Parameters:
   - json-str: JSON string to import
   
   Returns:
   - {:ok {:exercises [...] :conflicts [...]}} on success
   - {:error \"message\"} on validation failure
   
   Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5, 11.7, 11.8"
  [json-str]
  (let [parse-result (parse-import-json json-str)]
    (if (contains? parse-result :ok)
      (let [imported-exercises (:ok parse-result)
            existing-exercises @library-state
            conflicts (detect-conflicts imported-exercises existing-exercises)]
        {:ok {:exercises imported-exercises
              :conflicts conflicts}})
      parse-result)))

(defn merge-and-save-import!
  "Merge imported exercises with existing library and save.
   
   Parameters:
   - imported-exercises: vector of exercises to import
   - conflict-resolutions: map of {name -> :keep-existing or :use-imported}
   
   Returns:
   - {:ok {:added [...] :skipped [...] :updated [...]}} on success
   - {:error \"message\"} on failure
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage
   
   Validates: Requirements 11.6, 11.9"
  [imported-exercises conflict-resolutions]
  (let [existing-exercises @library-state
        merge-result (merge-exercises imported-exercises existing-exercises conflict-resolutions)
        merged-library (:merged-library merge-result)
        save-result (save-library! merged-library)]
    (if (contains? save-result :ok)
      {:ok (dissoc merge-result :merged-library)}
      save-result)))

;; ============================================================================
;; Equipment Filtering
;; ============================================================================

(defn get-equipment-types
  "Get all unique equipment types from the exercise library.
   
   Returns:
   - Set of equipment type strings from all exercises in the library
   
   Example:
   - If library has exercises with equipment [\"None\"], [\"A wall\"], [\"None\"]
   - Returns #{\"None\" \"A wall\"}
   
   Validates: Requirements 13.1"
  []
  (let [exercises @library-state
        all-equipment (mapcat :equipment exercises)]
    (set all-equipment)))

(defn filter-by-equipment
  "Filter exercises to only those requiring equipment in the provided set or \"None\".
   
   An exercise is included if ALL of its required equipment types are either:
   - In the provided equipment-set, OR
   - Equal to \"None\"
   
   Parameters:
   - exercises: vector of exercise maps
   - equipment-set: set of equipment type strings that are available
   
   Returns:
   - Vector of exercises that can be performed with the available equipment
   
   Example:
   - exercises: [{:name \"Push-ups\" :equipment [\"None\"]}
                 {:name \"Wall Sit\" :equipment [\"A wall\"]}
                 {:name \"Dumbbell Curls\" :equipment [\"Dumbbells\"]}]
   - equipment-set: #{\"A wall\"}
   - Returns: [{:name \"Push-ups\" :equipment [\"None\"]}
               {:name \"Wall Sit\" :equipment [\"A wall\"]}]
   
   Validates: Requirements 2.10, 13.3"
  [exercises equipment-set]
  (filterv
   (fn [exercise]
     (let [required-equipment (:equipment exercise)]
       ;; Exercise is included if all required equipment is available or is \"None\"
       (every? (fn [eq]
                 (or (= eq "None")
                     (contains? equipment-set eq)))
               required-equipment)))
   exercises))

;; ============================================================================
;; Exercise Update Operations
;; ============================================================================

(defn update-exercise-difficulty!
  "Update the difficulty of an exercise by name.
   
   Parameters:
   - exercise-name: string name of the exercise to update
   - new-difficulty: new difficulty value (must be between 0.5 and 2.0)
   
   Returns:
   - {:ok exercise} on success with updated exercise
   - {:error \"message\"} on validation failure or if exercise not found
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage"
  [exercise-name new-difficulty]
  (cond
    (not (valid-difficulty? new-difficulty))
    {:error "Difficulty must be between 0.5 and 2.0"}
    
    (not (exercise-exists? exercise-name))
    {:error (str "Exercise '" exercise-name "' not found")}
    
    :else
    (let [updated-library (mapv (fn [ex]
                                  (if (= (:name ex) exercise-name)
                                    (assoc ex :difficulty new-difficulty)
                                    ex))
                                @library-state)
          save-result (save-library! updated-library)]
      (if (contains? save-result :ok)
        {:ok (first (filter #(= (:name %) exercise-name) updated-library))}
        save-result))))

(defn toggle-exercise-enabled!
  "Toggle the enabled/disabled status of an exercise.
   Disabled exercises are excluded from session generation.
   
   Parameters:
   - exercise-name: string name of the exercise to toggle
   
   Returns:
   - {:ok exercise} on success with updated exercise
   - {:error \"message\"} if exercise not found
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage"
  [exercise-name]
  (if (not (exercise-exists? exercise-name))
    {:error (str "Exercise '" exercise-name "' not found")}
    (let [updated-library (mapv (fn [ex]
                                  (if (= (:name ex) exercise-name)
                                    (assoc ex :enabled (not (:enabled ex true)))
                                    ex))
                                @library-state)
          save-result (save-library! updated-library)]
      (if (contains? save-result :ok)
        {:ok (first (filter #(= (:name %) exercise-name) updated-library))}
        save-result))))

(defn get-enabled-exercises
  "Get only the enabled exercises from the library.
   Exercises without an :enabled key are considered enabled by default.
   
   Returns:
   - Vector of enabled exercises"
  []
  (filterv #(:enabled % true) @library-state))


(defn delete-exercise!
  "Delete an exercise from the library by name.
   
   Parameters:
   - exercise-name: string name of the exercise to delete
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if exercise not found
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage"
  [exercise-name]
  (if (not (exercise-exists? exercise-name))
    {:error (str "Exercise '" exercise-name "' not found")}
    (let [updated-library (filterv #(not= (:name %) exercise-name) @library-state)
          save-result (save-library! updated-library)]
      (if (contains? save-result :ok)
        {:ok true}
        save-result))))
