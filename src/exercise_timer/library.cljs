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

(defn- read-from-storage
  "Read data from localStorage with JSON deserialization.
   
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
              {:ok exercises}
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

(defn valid-weight?
  "Check if weight is within valid range (0.5 to 2.0 inclusive).
   Validates: Requirements 6.3, 7.3"
  [weight]
  (and (number? weight)
       (>= weight 0.5)
       (<= weight 2.0)))

(defn valid-name?
  "Check if name is non-empty string.
   Validates: Requirements 6.2, 7.2"
  [name]
  (and (string? name)
       (not (empty? (clojure.string/trim name)))))

(defn make-exercise
  "Create an exercise with name and weight.
   Returns exercise map if valid, or error map if invalid.
   
   Parameters:
   - name: Non-empty string, must be unique in library
   - weight: Number between 0.5 and 2.0 inclusive
   
   Returns:
   - {:exercise {:name \"...\" :weight N}} on success
   - {:error \"message\"} on validation failure
   
   Validates: Requirements 6.2, 6.3, 6.4, 7.2, 7.3"
  [name weight]
  (cond
    (not (valid-name? name))
    {:error "Exercise name must be a non-empty string"}
    
    (not (valid-weight? weight))
    {:error "Exercise weight must be between 0.5 and 2.0"}
    
    :else
    {:exercise {:name (clojure.string/trim name)
                :weight weight}}))

;; ============================================================================
;; Library State Management
;; ============================================================================

(defonce ^:private library-state
  (atom []))

;; ============================================================================
;; Default Exercise Initialization
;; ============================================================================

(def ^:private default-exercises
  "Default set of exercises to initialize the library.
   Validates: Requirements 6.1, 6.6"
  [{:name "Push-ups" :weight 1.2}
   {:name "Squats" :weight 1.0}
   {:name "Plank" :weight 1.5}
   {:name "Jumping Jacks" :weight 0.8}
   {:name "Lunges" :weight 1.0}
   {:name "Mountain Climbers" :weight 1.3}
   {:name "Burpees" :weight 1.8}
   {:name "High Knees" :weight 0.9}
   {:name "Sit-ups" :weight 1.0}
   {:name "Wall Sit" :weight 1.4}
   {:name "Russian Twists" :weight 1.1}
   {:name "Kneel to Stand" :weight 1.6}
   {:name "Air Punches" :weight 0.7}
   {:name "Plank Shoulder Taps" :weight 1.4}])

(defn initialize-defaults!
  "Initialize the exercise library with default exercises.
   This function should be called on first run when the library is empty.
   
   Returns:
   - {:ok true :count N} on success with number of exercises initialized
   - {:error \"message\"} on failure
   
   Side effects:
   - Updates library-state atom with default exercises
   - Persists to localStorage
   
   Validates: Requirements 6.1, 6.6"
  []
  (reset! library-state default-exercises)
  (let [save-result (write-to-storage! default-exercises)]
    (if (contains? save-result :ok)
      {:ok true :count (count default-exercises)}
      save-result)))

;; ============================================================================
;; Library CRUD Operations
;; ============================================================================

(defn load-library
  "Load exercises from local storage into memory.
   If storage is empty or unavailable, initializes with default exercises.
   
   Returns:
   - Vector of exercises on success
   - Vector of default exercises if storage is empty (first run)
   
   Side effects:
   - Updates library-state atom with loaded exercises
   - Initializes defaults on first run
   
   Validates: Requirements 6.1, 6.5, 6.6"
  []
  (let [result (read-from-storage)]
    (if (contains? result :ok)
      (let [exercises (:ok result)]
        (if (empty? exercises)
          ;; First run: storage exists but is empty, initialize defaults
          (do
            (initialize-defaults!)
            @library-state)
          ;; Normal case: return loaded exercises
          (do
            (reset! library-state exercises)
            exercises)))
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
   
   Parameters:
   - exercises: Vector of exercise maps to save
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} on failure
   
   Side effects:
   - Updates library-state atom
   - Writes to localStorage
   
   Validates: Requirements 6.5, 7.5"
  [exercises]
  (reset! library-state exercises)
  (write-to-storage! exercises))

(defn get-all-exercises
  "Get all exercises from current library state.
   
   Returns:
   - Vector of all exercises in the library
   
   Validates: Requirements 6.1"
  []
  @library-state)

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
   - exercise: Exercise map with :name and :weight keys
   
   Returns:
   - {:ok exercise} on success
   - {:error \"message\"} on validation failure
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage
   
   Validates: Requirements 6.4, 7.1, 7.2, 7.3, 7.4, 7.5"
  [exercise]
  (let [{:keys [name weight]} exercise]
    (cond
      ;; Validate name
      (not (valid-name? name))
      {:error "Exercise name must be a non-empty string"}
      
      ;; Validate weight
      (not (valid-weight? weight))
      {:error "Exercise weight must be between 0.5 and 2.0"}
      
      ;; Check for duplicate name
      (exercise-exists? name)
      {:error (str "Exercise with name '" (clojure.string/trim name) "' already exists")}
      
      ;; All validations passed, add exercise
      :else
      (let [trimmed-exercise {:name (clojure.string/trim name)
                              :weight weight}
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
   
   Parameters:
   - exercise: exercise map to validate
   
   Returns:
   - {:ok exercise} if valid
   - {:error \"message\"} if invalid
   
   Validates: Requirements 11.4"
  [exercise]
  (cond
    (not (map? exercise))
    {:error "Exercise must be a map"}
    
    (not (contains? exercise :name))
    {:error "Exercise missing required field: name"}
    
    (not (contains? exercise :weight))
    {:error "Exercise missing required field: weight"}
    
    (not (valid-name? (:name exercise)))
    {:error (str "Invalid exercise name: " (:name exercise))}
    
    (not (valid-weight? (:weight exercise)))
    {:error (str "Invalid exercise weight: " (:weight exercise) " (must be between 0.5 and 2.0)")}
    
    :else
    {:ok exercise}))

(defn- validate-import-data
  "Validate imported JSON data structure.
   
   Parameters:
   - data: parsed JSON data
   
   Returns:
   - {:ok exercises} if valid with vector of exercises
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
        {:ok exercises}
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
   the current library and the import, but with different weights.
   
   Parameters:
   - imported-exercises: vector of exercises to import
   - existing-exercises: vector of current library exercises
   
   Returns:
   - vector of conflict maps: [{:name string :existing-weight number :imported-weight number}]
   
   Validates: Requirements 11.7, 11.8"
  [imported-exercises existing-exercises]
  (let [existing-map (into {} (map (fn [ex] [(:name ex) (:weight ex)]) existing-exercises))]
    (reduce
     (fn [conflicts imported-ex]
       (let [name (:name imported-ex)
             imported-weight (:weight imported-ex)
             existing-weight (get existing-map name)]
         (if (and existing-weight (not= existing-weight imported-weight))
           (conj conflicts {:name name
                            :existing-weight existing-weight
                            :imported-weight imported-weight})
           conflicts)))
     []
     imported-exercises)))

(defn- merge-exercises
  "Merge imported exercises with existing library.
   
   Strategy:
   - Skip exercises that are identical (same name and weight)
   - Add exercises with new names
   - For conflicts (same name, different weight), use the resolution map
   
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
                      ;; Identical exercise, skip
                      (and existing-ex (= (:weight existing-ex) (:weight imported-ex)))
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
;; Exercise Update Operations
;; ============================================================================

(defn update-exercise-weight!
  "Update the weight of an exercise by name.
   
   Parameters:
   - exercise-name: string name of the exercise to update
   - new-weight: new weight value (must be between 0.5 and 2.0)
   
   Returns:
   - {:ok exercise} on success with updated exercise
   - {:error \"message\"} on validation failure or if exercise not found
   
   Side effects:
   - Updates library-state atom
   - Persists to localStorage"
  [exercise-name new-weight]
  (cond
    (not (valid-weight? new-weight))
    {:error "Weight must be between 0.5 and 2.0"}
    
    (not (exercise-exists? exercise-name))
    {:error (str "Exercise '" exercise-name "' not found")}
    
    :else
    (let [updated-library (mapv (fn [ex]
                                  (if (= (:name ex) exercise-name)
                                    (assoc ex :weight new-weight)
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
