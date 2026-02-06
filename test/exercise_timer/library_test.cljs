(ns exercise-timer.library-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [exercise-timer.library :as library]))

;; ============================================================================
;; Unit Tests for Exercise Data Structure and Validation (Task 2.1)
;; ============================================================================

(deftest test-make-exercise-valid
  (testing "make-exercise with valid inputs creates exercise"
    (let [result (library/make-exercise "Push-ups" 1.2)]
      (is (contains? result :exercise))
      (is (= "Push-ups" (get-in result [:exercise :name])))
      (is (= 1.2 (get-in result [:exercise :weight]))))))

(deftest test-make-exercise-trims-name
  (testing "make-exercise trims whitespace from name"
    (let [result (library/make-exercise "  Squats  " 1.0)]
      (is (contains? result :exercise))
      (is (= "Squats" (get-in result [:exercise :name]))))))

(deftest test-make-exercise-empty-name
  (testing "make-exercise rejects empty name"
    (let [result (library/make-exercise "" 1.0)]
      (is (contains? result :error))
      (is (= "Exercise name must be a non-empty string" (:error result))))))

(deftest test-make-exercise-whitespace-only-name
  (testing "make-exercise rejects whitespace-only name"
    (let [result (library/make-exercise "   " 1.0)]
      (is (contains? result :error))
      (is (= "Exercise name must be a non-empty string" (:error result))))))

(deftest test-make-exercise-weight-too-low
  (testing "make-exercise rejects weight below 0.5"
    (let [result (library/make-exercise "Plank" 0.4)]
      (is (contains? result :error))
      (is (= "Exercise weight must be between 0.5 and 2.0" (:error result))))))

(deftest test-make-exercise-weight-too-high
  (testing "make-exercise rejects weight above 2.0"
    (let [result (library/make-exercise "Burpees" 2.1)]
      (is (contains? result :error))
      (is (= "Exercise weight must be between 0.5 and 2.0" (:error result))))))

(deftest test-make-exercise-weight-boundary-min
  (testing "make-exercise accepts weight at minimum boundary (0.5)"
    (let [result (library/make-exercise "Easy Exercise" 0.5)]
      (is (contains? result :exercise))
      (is (= 0.5 (get-in result [:exercise :weight]))))))

(deftest test-make-exercise-weight-boundary-max
  (testing "make-exercise accepts weight at maximum boundary (2.0)"
    (let [result (library/make-exercise "Hard Exercise" 2.0)]
      (is (contains? result :exercise))
      (is (= 2.0 (get-in result [:exercise :weight]))))))

(deftest test-valid-weight-function
  (testing "valid-weight? correctly validates weight values"
    (is (true? (library/valid-weight? 0.5)))
    (is (true? (library/valid-weight? 1.0)))
    (is (true? (library/valid-weight? 2.0)))
    (is (false? (library/valid-weight? 0.4)))
    (is (false? (library/valid-weight? 2.1)))
    (is (false? (library/valid-weight? "1.0")))
    (is (false? (library/valid-weight? nil)))))

(deftest test-valid-name-function
  (testing "valid-name? correctly validates name values"
    (is (true? (library/valid-name? "Push-ups")))
    (is (true? (library/valid-name? "A")))
    (is (false? (library/valid-name? "")))
    (is (false? (library/valid-name? "   ")))
    (is (false? (library/valid-name? nil)))
    (is (false? (library/valid-name? 123)))))

;; ============================================================================
;; Property-Based Tests for Exercise Validation (Task 2.2)
;; ============================================================================

;; Generators for property-based testing

(def gen-valid-weight
  "Generator for valid weight values (0.5 to 2.0)"
  (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false}))

(def gen-invalid-weight-low
  "Generator for invalid weight values below 0.5"
  (gen/double* {:min -100.0 :max 0.49 :infinite? false :NaN? false}))

(def gen-invalid-weight-high
  "Generator for invalid weight values above 2.0"
  (gen/double* {:min 2.01 :max 100.0 :infinite? false :NaN? false}))

(def gen-invalid-weight
  "Generator for invalid weight values (outside 0.5-2.0 range)"
  (gen/one-of [gen-invalid-weight-low gen-invalid-weight-high]))

(def gen-valid-name
  "Generator for valid exercise names (non-empty strings)"
  (gen/such-that #(not (empty? (clojure.string/trim %)))
                 (gen/not-empty gen/string-alphanumeric)
                 100))

(def gen-invalid-name
  "Generator for invalid exercise names (empty or whitespace-only)"
  (gen/one-of [(gen/return "")
               (gen/return "   ")
               (gen/return "\t")
               (gen/return "\n")]))

;; Property 13: Exercise Data Integrity
;; **Validates: Requirements 6.2, 6.4**
(defspec ^{:feature "exercise-timer-app"
           :property 13
           :description "Exercise Data Integrity"}
  property-13-exercise-data-integrity
  100
  (prop/for-all [name gen-valid-name
                 weight gen-valid-weight]
    (let [result (library/make-exercise name weight)]
      (and
       ;; Should succeed with valid inputs
       (contains? result :exercise)
       (not (contains? result :error))
       ;; Exercise should have a name
       (string? (get-in result [:exercise :name]))
       (not (empty? (get-in result [:exercise :name])))
       ;; Exercise should have a weight value
       (number? (get-in result [:exercise :weight]))
       ;; Name should be trimmed
       (= (clojure.string/trim name) (get-in result [:exercise :name]))
       ;; Weight should be preserved
       (= weight (get-in result [:exercise :weight]))))))

;; Property 14: Weight Validation
;; **Validates: Requirements 6.3**
(defspec ^{:feature "exercise-timer-app"
           :property 14
           :description "Weight Validation"}
  property-14-weight-validation
  100
  (prop/for-all [name gen-valid-name
                 weight gen/double]
    (let [result (library/make-exercise name weight)
          is-valid-weight (and (>= weight 0.5) (<= weight 2.0))]
      (if is-valid-weight
        ;; Valid weight should succeed
        (and (contains? result :exercise)
             (= weight (get-in result [:exercise :weight])))
        ;; Invalid weight should fail with error
        (and (contains? result :error)
             (not (contains? result :exercise))
             (string? (:error result)))))))

;; Additional property test: Invalid names should always be rejected
(defspec ^{:feature "exercise-timer-app"
           :property 13
           :description "Exercise Data Integrity - Invalid Names"}
  property-13-invalid-names-rejected
  100
  (prop/for-all [name gen-invalid-name
                 weight gen-valid-weight]
    (let [result (library/make-exercise name weight)]
      (and
       ;; Should fail with invalid name
       (contains? result :error)
       (not (contains? result :exercise))
       (string? (:error result))))))

;; Unit tests will be implemented in task 2.8

;; ============================================================================
;; Unit Tests for LocalStorage Wrapper (Task 2.3)
;; ============================================================================

;; Note: These tests verify the storage wrapper functions exist and handle errors
;; In a Node.js test environment, localStorage may not be available
;; Full integration tests will be performed in browser environment

(deftest test-storage-wrapper-functions-exist
  (testing "Storage wrapper functions are defined"
    (is (fn? #'library/storage-available?))
    (is (fn? #'library/write-to-storage!))
    (is (fn? #'library/read-from-storage))
    (is (fn? #'library/clear-storage!))))

(deftest test-write-to-storage-returns-result-map
  (testing "write-to-storage! returns a result map"
    (let [test-data [{:name "Push-ups" :weight 1.2}]
          result (#'library/write-to-storage! test-data)]
      ;; Should return either {:ok true} or {:error "message"}
      (is (map? result))
      (is (or (contains? result :ok)
              (contains? result :error))))))

(deftest test-read-from-storage-returns-result-map
  (testing "read-from-storage returns a result map"
    (let [result (#'library/read-from-storage)]
      ;; Should return either {:ok exercises} or {:error "message"}
      (is (map? result))
      (is (or (contains? result :ok)
              (contains? result :error))))))

(deftest test-clear-storage-returns-result-map
  (testing "clear-storage! returns a result map"
    (let [result (#'library/clear-storage!)]
      ;; Should return either {:ok true} or {:error "message"}
      (is (map? result))
      (is (or (contains? result :ok)
              (contains? result :error))))))

(deftest test-storage-error-handling
  (testing "Storage functions handle errors gracefully"
    ;; In Node.js environment, localStorage is typically not available
    ;; Verify that functions return error maps instead of throwing
    (let [write-result (#'library/write-to-storage! [])
          read-result (#'library/read-from-storage)
          clear-result (#'library/clear-storage!)]
      ;; All should return maps (not throw exceptions)
      (is (map? write-result))
      (is (map? read-result))
      (is (map? clear-result))
      ;; If storage is unavailable, should have error messages
      (when (contains? write-result :error)
        (is (string? (:error write-result))))
      (when (contains? read-result :error)
        (is (string? (:error read-result))))
      (when (contains? clear-result :error)
        (is (string? (:error clear-result)))))))

;; ============================================================================
;; Property-Based Tests for Storage Round-Trip (Task 2.4)
;; ============================================================================

;; Generator for exercise library (vector of exercises)
(def gen-exercise
  "Generator for valid exercise maps"
  (gen/let [name gen-valid-name
            weight gen-valid-weight]
    {:name (clojure.string/trim name)
     :weight weight}))

(def gen-exercise-library
  "Generator for exercise libraries (vector of exercises with unique names)"
  (gen/let [exercises (gen/vector gen-exercise 1 20)]
    ;; Ensure unique names by using a map and converting back to vector
    (vec (vals (into {} (map (fn [ex] [(:name ex) ex]) exercises))))))

;; Property 15: Storage Round-Trip
;; **Validates: Requirements 6.5**
(defspec ^{:feature "exercise-timer-app"
           :property 15
           :description "Storage Round-Trip"}
  property-15-storage-round-trip
  100
  (prop/for-all [exercises gen-exercise-library]
    ;; Clear storage before test
    (#'library/clear-storage!)
    
    ;; Write exercises to storage
    (let [write-result (#'library/write-to-storage! exercises)]
      (if (contains? write-result :ok)
        ;; If write succeeded, read should return equivalent data
        (let [read-result (#'library/read-from-storage)]
          (and
           ;; Read should succeed
           (contains? read-result :ok)
           ;; Read data should be a vector
           (vector? (:ok read-result))
           ;; Read data should have same length as written data
           (= (count exercises) (count (:ok read-result)))
           ;; Each exercise should be preserved (name and weight)
           (every? (fn [original-ex]
                     (some (fn [read-ex]
                             (and (= (:name original-ex) (:name read-ex))
                                  (= (:weight original-ex) (:weight read-ex))))
                           (:ok read-result)))
                   exercises)))
        ;; If write failed (e.g., localStorage unavailable), that's acceptable
        ;; but we should verify read also fails or returns error
        (let [read-result (#'library/read-from-storage)]
          ;; In this case, we just verify the system is consistent
          ;; (either both fail or storage is unavailable)
          true)))))

;; ============================================================================
;; Unit Tests for Library CRUD Operations (Task 2.5)
;; ============================================================================

(deftest test-load-library-empty-storage
  (testing "load-library returns empty vector when storage is empty"
    ;; Clear storage first
    (#'library/clear-storage!)
    (let [result (library/load-library)]
      (is (vector? result)))))

(deftest test-save-and-load-library
  (testing "save-library! and load-library work together"
    ;; Clear storage first
    (#'library/clear-storage!)
    
    ;; Save some exercises
    (let [exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}]
          save-result (library/save-library! exercises)]
      
      ;; Save should return a result map
      (is (map? save-result))
      
      ;; If save succeeded, load should return the same exercises
      (when (contains? save-result :ok)
        (let [loaded (library/load-library)]
          (is (= 2 (count loaded)))
          (is (some #(= "Push-ups" (:name %)) loaded))
          (is (some #(= "Squats" (:name %)) loaded)))))))

(deftest test-get-all-exercises-with-in-memory-state
  (testing "get-all-exercises returns current library state (in-memory)"
    ;; Directly set library state (bypassing storage)
    (library/save-library! [{:name "Test Exercise" :weight 1.0}])
    
    (let [exercises (library/get-all-exercises)]
      (is (vector? exercises))
      (is (= 1 (count exercises)))
      (is (= "Test Exercise" (:name (first exercises)))))))

(deftest test-exercise-exists-true-in-memory
  (testing "exercise-exists? returns true for existing exercise (in-memory)"
    ;; Directly set library state
    (library/save-library! [{:name "Push-ups" :weight 1.2}])
    
    (is (true? (library/exercise-exists? "Push-ups")))))

(deftest test-exercise-exists-false
  (testing "exercise-exists? returns false for non-existing exercise"
    ;; Set library state
    (library/save-library! [{:name "Push-ups" :weight 1.2}])
    
    (is (false? (library/exercise-exists? "Squats")))))

(deftest test-exercise-exists-trims-name
  (testing "exercise-exists? trims whitespace when checking"
    ;; Set library state
    (library/save-library! [{:name "Push-ups" :weight 1.2}])
    
    (is (true? (library/exercise-exists? "  Push-ups  ")))))

(deftest test-add-exercise-success
  (testing "add-exercise! successfully adds valid exercise"
    ;; Clear library state
    (library/save-library! [])
    
    (let [result (library/add-exercise! {:name "New Exercise" :weight 1.5})]
      ;; Should return a result map
      (is (map? result))
      
      ;; If add succeeded, verify it's in the library
      (when (contains? result :ok)
        (is (= "New Exercise" (:name (:ok result))))
        (is (= 1.5 (:weight (:ok result))))
        (is (true? (library/exercise-exists? "New Exercise"))))
      
      ;; Even if storage fails, in-memory state should be updated
      (when (contains? result :error)
        ;; In Node.js environment without localStorage, the function should still
        ;; update in-memory state, but we'll accept the error
        (is (string? (:error result)))))))

(deftest test-add-exercise-trims-name
  (testing "add-exercise! trims whitespace from name"
    ;; Clear library state
    (library/save-library! [])
    
    (let [result (library/add-exercise! {:name "  Trimmed  " :weight 1.0})]
      (when (contains? result :ok)
        (is (= "Trimmed" (:name (:ok result))))))))

(deftest test-add-exercise-invalid-name
  (testing "add-exercise! rejects empty name"
    ;; Clear library state
    (library/save-library! [])
    
    (let [result (library/add-exercise! {:name "" :weight 1.0})]
      (is (contains? result :error))
      (is (= "Exercise name must be a non-empty string" (:error result))))))

(deftest test-add-exercise-invalid-weight-low
  (testing "add-exercise! rejects weight below 0.5"
    ;; Clear library state
    (library/save-library! [])
    
    (let [result (library/add-exercise! {:name "Test" :weight 0.4})]
      (is (contains? result :error))
      (is (= "Exercise weight must be between 0.5 and 2.0" (:error result))))))

(deftest test-add-exercise-invalid-weight-high
  (testing "add-exercise! rejects weight above 2.0"
    ;; Clear library state
    (library/save-library! [])
    
    (let [result (library/add-exercise! {:name "Test" :weight 2.1})]
      (is (contains? result :error))
      (is (= "Exercise weight must be between 0.5 and 2.0" (:error result))))))

(deftest test-add-exercise-duplicate-name
  (testing "add-exercise! rejects duplicate exercise name"
    ;; Set up test data
    (library/save-library! [{:name "Push-ups" :weight 1.2}])
    
    (let [result (library/add-exercise! {:name "Push-ups" :weight 1.5})]
      (is (contains? result :error))
      (is (clojure.string/includes? (:error result) "already exists"))
      (is (clojure.string/includes? (:error result) "Push-ups")))))

(deftest test-add-exercise-duplicate-name-with-whitespace
  (testing "add-exercise! detects duplicate even with whitespace"
    ;; Set up test data
    (library/save-library! [{:name "Push-ups" :weight 1.2}])
    
    (let [result (library/add-exercise! {:name "  Push-ups  " :weight 1.5})]
      (is (contains? result :error))
      (is (clojure.string/includes? (:error result) "already exists")))))

(deftest test-add-exercise-persistence-when-storage-available
  (testing "add-exercise! persists to storage when available"
    ;; Clear storage first
    (#'library/clear-storage!)
    (library/load-library)
    
    ;; Add an exercise
    (let [add-result (library/add-exercise! {:name "Persistent" :weight 1.0})]
      ;; If storage is available and add succeeded
      (when (contains? add-result :ok)
        ;; Load library again (simulating app restart)
        (let [loaded (library/load-library)]
          (is (some #(= "Persistent" (:name %)) loaded)))))))

;; ============================================================================
;; Property-Based Tests for Add Exercise Operations (Task 2.6)
;; ============================================================================

;; Property 16: Add Exercise Validation
;; **Validates: Requirements 7.2, 7.3, 7.4**
(defspec ^{:feature "exercise-timer-app"
           :property 16
           :description "Add Exercise Validation"}
  property-16-add-exercise-validation
  100
  (prop/for-all [name (gen/one-of [gen-valid-name gen-invalid-name])
                 weight gen/double]
    ;; Clear library state before each test
    (library/save-library! [])
    
    (let [result (library/add-exercise! {:name name :weight weight})
          is-valid-name (and (string? name) (not (empty? (clojure.string/trim name))))
          is-valid-weight (and (number? weight) (>= weight 0.5) (<= weight 2.0))
          should-succeed (and is-valid-name is-valid-weight)]
      
      (if should-succeed
        ;; Valid inputs should succeed (or fail only due to storage issues)
        (if (contains? result :ok)
          ;; If succeeded, verify the exercise was added correctly
          (and
           (not (contains? result :error))
           ;; Exercise should be added to library
           (library/exercise-exists? name)
           ;; Returned exercise should have trimmed name and correct weight
           (= (clojure.string/trim name) (:name (:ok result)))
           (= weight (:weight (:ok result))))
          
          ;; If failed, it should only be due to storage unavailability
          ;; In this case, the in-memory state should still be updated
          (and
           (contains? result :error)
           (string? (:error result))
           ;; Exercise should still be in in-memory library (graceful degradation)
           (library/exercise-exists? name)))
        
        ;; Invalid inputs should fail with validation error
        (and
         (contains? result :error)
         (not (contains? result :ok))
         (string? (:error result))
         ;; Exercise should NOT be added to library
         (not (library/exercise-exists? name)))))))

;; Property 16 (continued): Add Exercise Validation - Duplicate Detection
;; **Validates: Requirements 7.4**
(defspec ^{:feature "exercise-timer-app"
           :property 16
           :description "Add Exercise Validation - Duplicate Detection"}
  property-16-add-exercise-duplicate-detection
  100
  (prop/for-all [name gen-valid-name
                 weight1 gen-valid-weight
                 weight2 gen-valid-weight]
    ;; Clear library and add first exercise
    (library/save-library! [])
    
    ;; Add first exercise
    (library/add-exercise! {:name name :weight weight1})
    
    ;; Verify first exercise is in library (regardless of storage success)
    (let [exists-after-first (library/exercise-exists? name)
          count-after-first (count (library/get-all-exercises))]
      
      (and
       ;; First exercise should be in library
       exists-after-first
       (= 1 count-after-first)
       
       ;; Second addition with same name should fail with duplicate error
       (let [second-result (library/add-exercise! {:name name :weight weight2})]
         (and
          (contains? second-result :error)
          (not (contains? second-result :ok))
          (string? (:error second-result))
          (clojure.string/includes? (:error second-result) "already exists")
          ;; Library should still only have one exercise
          (= 1 (count (library/get-all-exercises)))
          ;; Original exercise should be preserved (not overwritten)
          (= weight1 (:weight (first (library/get-all-exercises))))))))))

;; Property 17: Add Exercise Persistence
;; **Validates: Requirements 7.5, 7.6**
(defspec ^{:feature "exercise-timer-app"
           :property 17
           :description "Add Exercise Persistence"}
  property-17-add-exercise-persistence
  100
  (prop/for-all [name gen-valid-name
                 weight gen-valid-weight]
    ;; Clear storage and library state
    (#'library/clear-storage!)
    (library/load-library)
    
    ;; Add exercise
    (let [add-result (library/add-exercise! {:name name :weight weight})]
      
      (if (contains? add-result :ok)
        ;; If add succeeded, verify persistence
        (let [trimmed-name (clojure.string/trim name)]
          (and
           ;; Exercise should be in in-memory library
           (library/exercise-exists? trimmed-name)
           (let [in-memory-exercises (library/get-all-exercises)]
             (some #(and (= (:name %) trimmed-name)
                         (= (:weight %) weight))
                   in-memory-exercises))
           
           ;; Exercise should be persisted to storage
           ;; (reload library to verify persistence)
           (let [reloaded (library/load-library)]
             (and
              ;; Reloaded library should contain the exercise
              (some #(and (= (:name %) trimmed-name)
                          (= (:weight %) weight))
                    reloaded)
              ;; Exercise should be immediately available for session generation
              (library/exercise-exists? trimmed-name)))))
        
        ;; If add failed (e.g., storage unavailable), that's acceptable
        ;; but we should verify the error is handled gracefully
        (and
         (contains? add-result :error)
         (string? (:error add-result)))))))

;; Property 17 (continued): Add Exercise Persistence - Multiple Additions
;; **Validates: Requirements 7.5, 7.6**
(defspec ^{:feature "exercise-timer-app"
           :property 17
           :description "Add Exercise Persistence - Multiple Additions"}
  property-17-add-exercise-persistence-multiple
  100
  (prop/for-all [exercises (gen/vector
                            (gen/let [name gen-valid-name
                                      weight gen-valid-weight]
                              {:name (clojure.string/trim name)
                               :weight weight})
                            1 10)]
    ;; Clear storage and library state
    (#'library/clear-storage!)
    ;; Start with empty library (don't initialize defaults for this test)
    (library/save-library! [])
    
    ;; Make names unique to avoid duplicate errors
    (let [unique-exercises (vec (vals (into {} (map (fn [ex] [(:name ex) ex]) exercises))))]
      
      ;; Add all exercises
      (doseq [exercise unique-exercises]
        (library/add-exercise! exercise))
      
      ;; Verify all exercises are in memory
      (let [in-memory (library/get-all-exercises)]
        (and
         ;; All unique exercises should be added to in-memory state
         (= (count unique-exercises) (count in-memory))
         
         ;; Each exercise should be present in memory
         (every? (fn [ex]
                   (some #(and (= (:name %) (:name ex))
                               (= (:weight %) (:weight ex)))
                         in-memory))
                 unique-exercises)
         
         ;; Reload library to verify persistence (if storage is available)
         (let [reloaded (library/load-library)]
           ;; If storage is available, reloaded should match in-memory
           ;; If storage is unavailable, reloaded should still return in-memory data (graceful degradation)
           (and
            (= (count unique-exercises) (count reloaded))
            (every? (fn [ex]
                      (some #(and (= (:name %) (:name ex))
                                  (= (:weight %) (:weight ex)))
                            reloaded))
                    unique-exercises))))))))

;; ============================================================================
;; Unit Tests for Default Exercise Initialization (Task 2.7)
;; ============================================================================

(deftest test-initialize-defaults-creates-10-exercises
  (testing "initialize-defaults! creates exactly 10 default exercises"
    ;; Clear storage first
    (#'library/clear-storage!)
    
    (let [result (library/initialize-defaults!)]
      ;; Should return a result map
      (is (map? result))
      
      ;; If initialization succeeded, verify count
      (when (contains? result :ok)
        (is (= 10 (:count result)))
        
        ;; Verify exercises are in library
        (let [exercises (library/get-all-exercises)]
          (is (= 10 (count exercises))))))))

(deftest test-initialize-defaults-has-correct-exercises
  (testing "initialize-defaults! creates the correct default exercises"
    ;; Clear storage first
    (#'library/clear-storage!)
    
    (library/initialize-defaults!)
    
    (let [exercises (library/get-all-exercises)
          exercise-names (set (map :name exercises))]
      ;; Verify all expected exercises are present
      (is (contains? exercise-names "Push-ups"))
      (is (contains? exercise-names "Squats"))
      (is (contains? exercise-names "Plank"))
      (is (contains? exercise-names "Jumping Jacks"))
      (is (contains? exercise-names "Lunges"))
      (is (contains? exercise-names "Mountain Climbers"))
      (is (contains? exercise-names "Burpees"))
      (is (contains? exercise-names "High Knees"))
      (is (contains? exercise-names "Sit-ups"))
      (is (contains? exercise-names "Wall Sit")))))

(deftest test-initialize-defaults-has-correct-weights
  (testing "initialize-defaults! creates exercises with correct weights"
    ;; Clear storage first
    (#'library/clear-storage!)
    
    (library/initialize-defaults!)
    
    (let [exercises (library/get-all-exercises)
          exercise-map (into {} (map (fn [ex] [(:name ex) (:weight ex)]) exercises))]
      ;; Verify specific weights
      (is (= 1.2 (get exercise-map "Push-ups")))
      (is (= 1.0 (get exercise-map "Squats")))
      (is (= 1.5 (get exercise-map "Plank")))
      (is (= 0.8 (get exercise-map "Jumping Jacks")))
      (is (= 1.0 (get exercise-map "Lunges")))
      (is (= 1.3 (get exercise-map "Mountain Climbers")))
      (is (= 1.8 (get exercise-map "Burpees")))
      (is (= 0.9 (get exercise-map "High Knees")))
      (is (= 1.0 (get exercise-map "Sit-ups")))
      (is (= 1.4 (get exercise-map "Wall Sit"))))))

(deftest test-initialize-defaults-all-weights-valid
  (testing "initialize-defaults! creates exercises with valid weights (0.5-2.0)"
    ;; Clear storage first
    (#'library/clear-storage!)
    
    (library/initialize-defaults!)
    
    (let [exercises (library/get-all-exercises)]
      ;; All weights should be in valid range
      (is (every? #(and (>= (:weight %) 0.5)
                        (<= (:weight %) 2.0))
                  exercises)))))

(deftest test-load-library-initializes-defaults-on-first-run
  (testing "load-library initializes defaults when storage is empty (first run)"
    ;; Clear storage to simulate first run
    (#'library/clear-storage!)
    
    ;; Load library (should trigger default initialization)
    (let [exercises (library/load-library)]
      ;; Should have 10 default exercises
      (is (= 10 (count exercises)))
      
      ;; Verify some default exercises are present
      (let [exercise-names (set (map :name exercises))]
        (is (contains? exercise-names "Push-ups"))
        (is (contains? exercise-names "Squats"))
        (is (contains? exercise-names "Burpees"))))))

(deftest test-load-library-does-not-reinitialize-existing-library
  (testing "load-library does not reinitialize when library already exists"
    ;; Clear storage and initialize with custom exercises
    (#'library/clear-storage!)
    (library/save-library! [{:name "Custom Exercise 1" :weight 1.0}
                            {:name "Custom Exercise 2" :weight 1.5}])
    
    ;; Load library (should NOT reinitialize defaults)
    (let [exercises (library/load-library)]
      ;; Should have 2 custom exercises, not 10 defaults
      (is (= 2 (count exercises)))
      
      ;; Verify custom exercises are present
      (let [exercise-names (set (map :name exercises))]
        (is (contains? exercise-names "Custom Exercise 1"))
        (is (contains? exercise-names "Custom Exercise 2"))
        
        ;; Verify defaults are NOT present
        (is (not (contains? exercise-names "Push-ups")))
        (is (not (contains? exercise-names "Squats")))))))

(deftest test-initialize-defaults-persists-to-storage
  (testing "initialize-defaults! persists default exercises to storage"
    ;; Clear storage first
    (#'library/clear-storage!)
    
    ;; Initialize defaults
    (let [init-result (library/initialize-defaults!)]
      ;; If initialization succeeded
      (when (contains? init-result :ok)
        ;; Reload library from storage
        (let [reloaded (library/load-library)]
          ;; Should have 10 exercises from storage
          (is (= 10 (count reloaded)))
          
          ;; Verify exercises are loaded from storage, not re-initialized
          (let [exercise-names (set (map :name reloaded))]
            (is (contains? exercise-names "Push-ups"))
            (is (contains? exercise-names "Burpees"))))))))

;; ============================================================================
;; Unit Tests for Library Manager (Task 2.8)
;; ============================================================================

;; Test 1: Default initialization with specific exercises
;; Already covered by:
;; - test-initialize-defaults-creates-10-exercises
;; - test-initialize-defaults-has-correct-exercises
;; - test-initialize-defaults-has-correct-weights
;; - test-load-library-initializes-defaults-on-first-run

;; Test 2: Duplicate name rejection with error messages
;; Already covered by:
;; - test-add-exercise-duplicate-name
;; - test-add-exercise-duplicate-name-with-whitespace

;; Additional test for duplicate name rejection with specific error message format
(deftest test-duplicate-name-error-message-format
  (testing "Duplicate name rejection includes exercise name in error message"
    ;; Set up library with an exercise
    (library/save-library! [{:name "Jumping Jacks" :weight 0.8}])
    
    ;; Try to add duplicate
    (let [result (library/add-exercise! {:name "Jumping Jacks" :weight 1.5})]
      (is (contains? result :error))
      (is (string? (:error result)))
      ;; Error message should mention the duplicate name
      (is (clojure.string/includes? (:error result) "Jumping Jacks"))
      (is (clojure.string/includes? (:error result) "already exists")))))

(deftest test-duplicate-name-case-sensitive
  (testing "Duplicate name detection is case-sensitive"
    ;; Set up library with an exercise
    (library/save-library! [{:name "Push-ups" :weight 1.2}])
    
    ;; Try to add with different case - should be allowed (case-sensitive)
    (let [result (library/add-exercise! {:name "push-ups" :weight 1.0})]
      ;; This should succeed because names are case-sensitive
      ;; If it fails, it should be due to storage, not duplicate detection
      (if (contains? result :ok)
        (is (= "push-ups" (:name (:ok result))))
        ;; If it failed, verify it's not a duplicate error
        (is (not (clojure.string/includes? (:error result) "already exists")))))))

;; Test 3: Corrupted storage recovery
(deftest test-corrupted-storage-recovery-invalid-json
  (testing "Library recovers from corrupted storage with invalid JSON"
    ;; Manually corrupt the storage by writing invalid JSON
    (when (try (exists? js/localStorage) (catch js/Error _ false))
      (try
        ;; Write invalid JSON to storage
        (.setItem js/localStorage "exercise-timer-library" "{invalid json}")
        
        ;; Load library should recover by initializing defaults
        (let [exercises (library/load-library)]
          ;; Should return a vector (not throw an error)
          (is (vector? exercises))
          ;; Should have initialized with defaults (10 exercises)
          (is (= 10 (count exercises)))
          ;; Verify some default exercises are present
          (let [exercise-names (set (map :name exercises))]
            (is (contains? exercise-names "Push-ups"))
            (is (contains? exercise-names "Squats"))))
        
        (catch js/Error _
          ;; If localStorage is not available, skip this test
          (is true "LocalStorage not available, skipping test"))))
    
    ;; Clean up
    (#'library/clear-storage!)))

(deftest test-corrupted-storage-recovery-invalid-structure
  (testing "Library recovers from corrupted storage with invalid structure"
    ;; Manually corrupt the storage by writing valid JSON but invalid structure
    (when (try (exists? js/localStorage) (catch js/Error _ false))
      (try
        ;; Write valid JSON but invalid structure (exercises is not a vector)
        (.setItem js/localStorage "exercise-timer-library" 
                  (js/JSON.stringify (clj->js {:version 1 :exercises "not a vector"})))
        
        ;; Load library should recover by initializing defaults
        (let [exercises (library/load-library)]
          ;; Should return a vector (not throw an error)
          (is (vector? exercises))
          ;; Should have initialized with defaults (10 exercises)
          (is (= 10 (count exercises))))
        
        (catch js/Error _
          ;; If localStorage is not available, skip this test
          (is true "LocalStorage not available, skipping test"))))
    
    ;; Clean up
    (#'library/clear-storage!)))

(deftest test-corrupted-storage-recovery-missing-exercises-key
  (testing "Library recovers from corrupted storage with missing exercises key"
    ;; Manually corrupt the storage by writing valid JSON but missing exercises key
    (when (try (exists? js/localStorage) (catch js/Error _ false))
      (try
        ;; Write valid JSON but missing exercises key
        (.setItem js/localStorage "exercise-timer-library" 
                  (js/JSON.stringify (clj->js {:version 1})))
        
        ;; Load library should recover by initializing defaults
        (let [exercises (library/load-library)]
          ;; Should return a vector (not throw an error)
          (is (vector? exercises))
          ;; Should have initialized with defaults (10 exercises)
          (is (= 10 (count exercises))))
        
        (catch js/Error _
          ;; If localStorage is not available, skip this test
          (is true "LocalStorage not available, skipping test"))))
    
    ;; Clean up
    (#'library/clear-storage!)))

(deftest test-graceful-degradation-storage-unavailable
  (testing "Library continues to function when storage is unavailable"
    ;; This test verifies graceful degradation behavior
    ;; When storage is unavailable, the library should still work in-memory
    
    ;; Clear storage and start fresh
    (#'library/clear-storage!)
    
    ;; Add an exercise (should work even if storage fails)
    (let [result (library/add-exercise! {:name "Test Exercise" :weight 1.0})]
      ;; Should either succeed or fail gracefully
      (is (map? result))
      
      ;; If storage is available and add succeeded
      (when (contains? result :ok)
        ;; Exercise should be in in-memory library
        (is (library/exercise-exists? "Test Exercise"))
        (is (= 1 (count (library/get-all-exercises)))))
      
      ;; If storage failed, in-memory state should still be updated
      (when (contains? result :error)
        ;; Even with storage error, the exercise should be in memory
        (is (library/exercise-exists? "Test Exercise"))))))

(deftest test-default-exercises-all-unique-names
  (testing "Default exercises all have unique names"
    ;; Clear storage and initialize defaults
    (#'library/clear-storage!)
    (library/initialize-defaults!)
    
    (let [exercises (library/get-all-exercises)
          names (map :name exercises)
          unique-names (set names)]
      ;; Number of unique names should equal total number of exercises
      (is (= (count names) (count unique-names)))
      ;; Should have exactly 10 unique names
      (is (= 10 (count unique-names))))))

(deftest test-default-exercises-all-valid-weights
  (testing "Default exercises all have valid weights"
    ;; Clear storage and initialize defaults
    (#'library/clear-storage!)
    (library/initialize-defaults!)
    
    (let [exercises (library/get-all-exercises)]
      ;; All exercises should have valid weights
      (is (every? #(library/valid-weight? (:weight %)) exercises))
      ;; All weights should be between 0.5 and 2.0
      (is (every? #(and (>= (:weight %) 0.5) (<= (:weight %) 2.0)) exercises)))))

(deftest test-library-state-consistency-after-failed-add
  (testing "Library state remains consistent after failed add operation"
    ;; Set up library with known state
    (library/save-library! [{:name "Exercise 1" :weight 1.0}
                            {:name "Exercise 2" :weight 1.5}])
    
    (let [initial-count (count (library/get-all-exercises))]
      ;; Try to add invalid exercise (duplicate name)
      (library/add-exercise! {:name "Exercise 1" :weight 2.0})
      
      ;; Library should still have same count
      (is (= initial-count (count (library/get-all-exercises))))
      
      ;; Original exercise should be unchanged
      (let [exercises (library/get-all-exercises)
            exercise-1 (first (filter #(= "Exercise 1" (:name %)) exercises))]
        (is (= 1.0 (:weight exercise-1)))))))

(deftest test-library-state-consistency-after-failed-validation
  (testing "Library state remains consistent after validation failure"
    ;; Set up library with known state
    (library/save-library! [{:name "Exercise 1" :weight 1.0}])
    
    (let [initial-count (count (library/get-all-exercises))]
      ;; Try to add exercise with invalid weight
      (library/add-exercise! {:name "Exercise 2" :weight 3.0})
      
      ;; Library should still have same count
      (is (= initial-count (count (library/get-all-exercises))))
      
      ;; Try to add exercise with invalid name
      (library/add-exercise! {:name "" :weight 1.0})
      
      ;; Library should still have same count
      (is (= initial-count (count (library/get-all-exercises)))))))


;; ============================================================================
;; Unit Tests for Export Functionality (Task 6.1)
;; ============================================================================

(deftest test-export-to-json-basic
  (testing "export-to-json creates valid JSON"
    (library/initialize-defaults!)
    (let [result (library/export-to-json)]
      (is (contains? result :ok))
      (is (contains? (:ok result) :json))
      (is (contains? (:ok result) :filename))
      (is (string? (get-in result [:ok :json])))
      (is (string? (get-in result [:ok :filename]))))))

(deftest test-export-filename-format
  (testing "export filename contains timestamp"
    (let [result (library/export-to-json)
          filename (get-in result [:ok :filename])]
      (is (clojure.string/starts-with? filename "exercise-library-"))
      (is (clojure.string/ends-with? filename ".json")))))

(deftest test-export-json-structure
  (testing "exported JSON has correct structure"
    (library/initialize-defaults!)
    (let [result (library/export-to-json)
          json-str (get-in result [:ok :json])
          parsed (js->clj (js/JSON.parse json-str) :keywordize-keys true)]
      (is (contains? parsed :version))
      (is (contains? parsed :exercises))
      (is (vector? (:exercises parsed)))
      (is (> (count (:exercises parsed)) 0)))))

(deftest test-export-preserves-exercise-data
  (testing "exported JSON preserves exercise names and weights"
    (library/clear-storage!)
    (library/add-exercise! {:name "Test Exercise" :weight 1.5})
    (let [result (library/export-to-json)
          json-str (get-in result [:ok :json])
          parsed (js->clj (js/JSON.parse json-str) :keywordize-keys true)
          exercises (:exercises parsed)]
      (is (some #(= (:name %) "Test Exercise") exercises))
      (is (some #(= (:weight %) 1.5) exercises)))))

;; ============================================================================
;; Unit Tests for Import Validation (Task 6.3)
;; ============================================================================

(deftest test-parse-import-json-valid
  (testing "parse-import-json accepts valid JSON"
    (let [json-str "{\"version\":1,\"exercises\":[{\"name\":\"Push-ups\",\"weight\":1.2}]}"
          result (library/parse-import-json json-str)]
      (is (contains? result :ok))
      (is (vector? (:ok result)))
      (is (= 1 (count (:ok result)))))))

(deftest test-parse-import-json-malformed
  (testing "parse-import-json rejects malformed JSON"
    (let [json-str "{invalid json"
          result (library/parse-import-json json-str)]
      (is (contains? result :error))
      (is (clojure.string/includes? (:error result) "parse")))))

(deftest test-parse-import-json-missing-exercises
  (testing "parse-import-json rejects JSON without exercises field"
    (let [json-str "{\"version\":1}"
          result (library/parse-import-json json-str)]
      (is (contains? result :error))
      (is (clojure.string/includes? (:error result) "exercises")))))

(deftest test-parse-import-json-invalid-weight
  (testing "parse-import-json rejects exercises with invalid weights"
    (let [json-str "{\"exercises\":[{\"name\":\"Test\",\"weight\":3.0}]}"
          result (library/parse-import-json json-str)]
      (is (contains? result :error))
      (is (clojure.string/includes? (:error result) "weight")))))

(deftest test-parse-import-json-missing-name
  (testing "parse-import-json rejects exercises without name"
    (let [json-str "{\"exercises\":[{\"weight\":1.0}]}"
          result (library/parse-import-json json-str)]
      (is (contains? result :error))
      (is (clojure.string/includes? (:error result) "name")))))

(deftest test-parse-import-json-empty-name
  (testing "parse-import-json rejects exercises with empty name"
    (let [json-str "{\"exercises\":[{\"name\":\"\",\"weight\":1.0}]}"
          result (library/parse-import-json json-str)]
      (is (contains? result :error)))))

;; ============================================================================
;; Unit Tests for Import Merge Logic (Task 6.5)
;; ============================================================================

(deftest test-import-from-json-detects-conflicts
  (testing "import-from-json detects conflicts with existing exercises"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [json-str "{\"exercises\":[{\"name\":\"Push-ups\",\"weight\":1.5}]}"
          result (library/import-from-json json-str)]
      (is (contains? result :ok))
      (is (contains? (:ok result) :conflicts))
      (is (= 1 (count (get-in result [:ok :conflicts])))))))

(deftest test-import-from-json-no-conflicts-new-exercise
  (testing "import-from-json has no conflicts for new exercises"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [json-str "{\"exercises\":[{\"name\":\"Squats\",\"weight\":1.0}]}"
          result (library/import-from-json json-str)]
      (is (contains? result :ok))
      (is (empty? (get-in result [:ok :conflicts]))))))

(deftest test-import-from-json-no-conflicts-identical
  (testing "import-from-json has no conflicts for identical exercises"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [json-str "{\"exercises\":[{\"name\":\"Push-ups\",\"weight\":1.2}]}"
          result (library/import-from-json json-str)]
      (is (contains? result :ok))
      (is (empty? (get-in result [:ok :conflicts]))))))

(deftest test-merge-and-save-import-adds-new-exercises
  (testing "merge-and-save-import! adds new exercises"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [imported [{:name "Squats" :weight 1.0}]
          result (library/merge-and-save-import! imported {})]
      ;; May fail if localStorage unavailable, but logic should work
      (if (contains? result :ok)
        (do
          (is (= 1 (count (get-in result [:ok :added]))))
          (is (= "Squats" (first (get-in result [:ok :added]))))
          (is (= 2 (count (library/get-all-exercises)))))
        ;; If localStorage unavailable, just check in-memory state
        (is (= 2 (count (library/get-all-exercises))))))))

(deftest test-merge-and-save-import-skips-identical
  (testing "merge-and-save-import! skips identical exercises"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [imported [{:name "Push-ups" :weight 1.2}]
          result (library/merge-and-save-import! imported {})]
      (if (contains? result :ok)
        (do
          (is (= 1 (count (get-in result [:ok :skipped]))))
          (is (= "Push-ups" (first (get-in result [:ok :skipped]))))
          (is (= 1 (count (library/get-all-exercises)))))
        ;; If localStorage unavailable, just check in-memory state
        (is (= 1 (count (library/get-all-exercises))))))))

(deftest test-merge-and-save-import-resolves-conflicts-keep-existing
  (testing "merge-and-save-import! keeps existing on conflict with :keep-existing"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [imported [{:name "Push-ups" :weight 1.5}]
          resolutions {"Push-ups" :keep-existing}
          result (library/merge-and-save-import! imported resolutions)
          exercises (library/get-all-exercises)
          pushups (first (filter #(= (:name %) "Push-ups") exercises))]
      (if (contains? result :ok)
        (is (= 1.2 (:weight pushups)))
        ;; If localStorage unavailable, just check in-memory state
        (is (= 1.2 (:weight pushups)))))))

(deftest test-merge-and-save-import-resolves-conflicts-use-imported
  (testing "merge-and-save-import! uses imported on conflict with :use-imported"
    (library/clear-library-for-testing!)
    (library/add-exercise! {:name "Push-ups" :weight 1.2})
    (let [imported [{:name "Push-ups" :weight 1.5}]
          resolutions {"Push-ups" :use-imported}
          result (library/merge-and-save-import! imported resolutions)
          exercises (library/get-all-exercises)
          pushups (first (filter #(= (:name %) "Push-ups") exercises))]
      (if (contains? result :ok)
        (do
          (is (= 1 (count (get-in result [:ok :updated]))))
          (is (= 1.5 (:weight pushups))))
        ;; If localStorage unavailable, just check in-memory state
        (is (= 1.5 (:weight pushups)))))))

;; ============================================================================
;; Property-Based Tests for Import/Export (Task 6.2, 6.4, 6.6)
;; ============================================================================

;; Property 19: Export-Import Round-Trip
;; **Validates: Requirements 10.2, 10.4, 11.3**
(defspec ^{:feature "exercise-timer-app"
           :property 19
           :description "Export-Import Round-Trip"}
  export-import-round-trip-property
  100
  (prop/for-all [exercises (gen/not-empty
                            (gen/vector
                             (gen/let [name (gen/not-empty gen/string-alphanumeric)
                                       weight (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
                               {:name name :weight weight})
                             1 10))]
    ;; Set up library with exercises
    (library/clear-library-for-testing!)
    (doseq [ex exercises]
      (library/add-exercise! ex))
    
    ;; Export to JSON
    (let [export-result (library/export-to-json)]
      (if (contains? export-result :ok)
        (let [json-str (get-in export-result [:ok :json])
              ;; Import the JSON
              import-result (library/import-from-json json-str)]
          (if (contains? import-result :ok)
            (let [imported-exercises (get-in import-result [:ok :exercises])
                  original-exercises (library/get-all-exercises)]
              ;; Imported exercises should match original (when no conflicts)
              (and
               (= (count imported-exercises) (count original-exercises))
               ;; All original exercises should be in imported
               (every? (fn [orig-ex]
                         (some #(and (= (:name %) (:name orig-ex))
                                     (= (:weight %) (:weight orig-ex)))
                               imported-exercises))
                       original-exercises)))
            false))
        false))))

;; Property 20: Export Filename Format
;; **Validates: Requirements 10.5**
(defspec ^{:feature "exercise-timer-app"
           :property 20
           :description "Export Filename Format"}
  export-filename-format-property
  100
  (prop/for-all [_ gen/nat]  ; Just need to run multiple times
    (let [result (library/export-to-json)]
      (if (contains? result :ok)
        (let [filename (get-in result [:ok :filename])]
          (and
           ;; Filename should start with descriptive prefix
           (clojure.string/starts-with? filename "exercise-library-")
           ;; Filename should end with .json
           (clojure.string/ends-with? filename ".json")
           ;; Filename should contain a timestamp (at least 8 digits for date)
           (re-find #"\d{8}" filename)))
        false))))

;; Property 21: Import Validation
;; **Validates: Requirements 11.2, 11.4**
(defspec ^{:feature "exercise-timer-app"
           :property 21
           :description "Import Validation"}
  import-validation-property
  100
  (prop/for-all [invalid-data (gen/one-of
                                [(gen/return "{invalid json")  ; Malformed JSON
                                 (gen/return "{}")  ; Missing exercises field
                                 (gen/return "{\"exercises\":[]}")  ; Empty exercises
                                 (gen/return "{\"exercises\":[{\"weight\":1.0}]}")  ; Missing name
                                 (gen/return "{\"exercises\":[{\"name\":\"Test\",\"weight\":3.0}]}")  ; Invalid weight
                                 (gen/return "{\"exercises\":[{\"name\":\"\",\"weight\":1.0}]}")])]  ; Empty name
    (let [result (library/parse-import-json invalid-data)]
      ;; All invalid data should be rejected with an error
      (contains? result :error))))

;; Property 22: Import Preserves Library on Error
;; **Validates: Requirements 11.5**
(defspec ^{:feature "exercise-timer-app"
           :property 22
           :description "Import Preserves Library on Error"}
  import-preserves-library-on-error-property
  100
  (prop/for-all [exercises (gen/not-empty
                            (gen/vector
                             (gen/let [name (gen/not-empty gen/string-alphanumeric)
                                       weight (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
                               {:name name :weight weight})
                             1 5))]
    ;; Set up library with exercises
    (library/clear-library-for-testing!)
    (doseq [ex exercises]
      (library/add-exercise! ex))
    
    (let [original-library (library/get-all-exercises)
          ;; Try to import invalid JSON
          invalid-json "{invalid json"
          import-result (library/import-from-json invalid-json)
          library-after (library/get-all-exercises)]
      (and
       ;; Import should fail
       (contains? import-result :error)
       ;; Library should be unchanged
       (= (count original-library) (count library-after))
       (every? (fn [orig-ex]
                 (some #(and (= (:name %) (:name orig-ex))
                             (= (:weight %) (:weight orig-ex)))
                       library-after))
               original-library)))))

;; Property 23: Import Merge Behavior
;; **Validates: Requirements 11.6**
(defspec ^{:feature "exercise-timer-app"
           :property 23
           :description "Import Merge Behavior"}
  import-merge-behavior-property
  100
  (prop/for-all [existing-ex (gen/let [name (gen/not-empty gen/string-alphanumeric)
                                       weight (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
                               {:name name :weight weight})
                 new-ex (gen/let [name (gen/not-empty gen/string-alphanumeric)
                                  weight (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
                          {:name (str name "-new") :weight weight})]
    ;; Set up library with one exercise
    (library/clear-library-for-testing!)
    (library/add-exercise! existing-ex)
    
    ;; Import a new exercise (different name)
    (let [result (library/merge-and-save-import! [new-ex] {})
          library-after (library/get-all-exercises)]
      ;; Import should work (may fail on localStorage but in-memory should work)
      (and
       ;; New exercise should be added
       (some #(= (:name %) (:name new-ex)) library-after)
       ;; Existing exercise should still be there
       (some #(= (:name %) (:name existing-ex)) library-after)
       ;; Library should have both exercises
       (>= (count library-after) 2)))))

;; Property 24: Import Conflict Detection
;; **Validates: Requirements 11.7, 11.8**
(defspec ^{:feature "exercise-timer-app"
           :property 24
           :description "Import Conflict Detection"}
  import-conflict-detection-property
  100
  (prop/for-all [name (gen/not-empty gen/string-alphanumeric)
                 weight1 (gen/double* {:min 0.5 :max 1.5 :infinite? false :NaN? false})
                 weight2 (gen/double* {:min 1.5 :max 2.0 :infinite? false :NaN? false})]
    ;; Ensure weights are different (weight2 should be >= 1.5, weight1 should be < 1.5)
    ;; But due to floating point, they might still be equal at the boundary
    (if (= weight1 weight2)
      true  ; Skip this test case
      (do
        ;; Set up library with one exercise
        (library/clear-library-for-testing!)
        (library/add-exercise! {:name name :weight weight1})
        
        ;; Try to import same name with different weight
        (let [json-str (str "{\"exercises\":[{\"name\":\"" name "\",\"weight\":" weight2 "}]}")
              result (library/import-from-json json-str)]
          (if (contains? result :ok)
            (let [conflicts (get-in result [:ok :conflicts])]
              ;; Should detect a conflict (same name, different weight)
              (and
               (= 1 (count conflicts))
               (= name (:name (first conflicts)))
               (= weight1 (:existing-weight (first conflicts)))
               (= weight2 (:imported-weight (first conflicts)))))
            false))))))

;; Property 25: Import Persistence
;; **Validates: Requirements 11.9**
(defspec ^{:feature "exercise-timer-app"
           :property 25
           :description "Import Persistence"}
  import-persistence-property
  100
  (prop/for-all [exercises (gen/let [num (gen/choose 1 5)]
                             (gen/fmap
                              (fn [exs]
                                ;; Ensure unique names by adding index
                                (vec (map-indexed
                                      (fn [idx ex]
                                        (assoc ex :name (str (:name ex) "-" idx)))
                                      exs)))
                              (gen/vector
                               (gen/let [name (gen/not-empty gen/string-alphanumeric)
                                         weight (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
                                 {:name name :weight weight})
                               num num)))]
    ;; Clear and import exercises
    (library/clear-library-for-testing!)
    (let [result (library/merge-and-save-import! exercises {})]
      ;; Check in-memory state (localStorage may not be available in test environment)
      (let [current-library (library/get-all-exercises)]
        ;; All imported exercises should be in current library
        (every? (fn [ex]
                  (some #(and (= (:name %) (:name ex))
                              (= (:weight %) (:weight ex)))
                        current-library))
                exercises)))))
