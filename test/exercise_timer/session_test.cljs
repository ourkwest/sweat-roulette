(ns exercise-timer.session-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [clojure.set :as set]
            [exercise-timer.session :as session]))

;; ============================================================================
;; Unit Tests for Session Data Structures (Task 3.1)
;; ============================================================================

(deftest test-make-session-config-valid
  (testing "make-session-config creates valid configuration"
    (let [config (session/make-session-config 10)]
      (is (map? config))
      (is (= 10 (:duration-minutes config))))))

(deftest test-make-session-config-default-duration
  (testing "make-session-config works with default duration"
    (let [config (session/make-session-config session/default-session-duration-minutes)]
      (is (map? config))
      (is (= 5 (:duration-minutes config))))))

(deftest test-config-to-seconds-conversion
  (testing "config->seconds correctly converts minutes to seconds"
    (let [config (session/make-session-config 5)
          seconds (session/config->seconds config)]
      (is (= 300 seconds)))))

(deftest test-config-to-seconds-various-durations
  (testing "config->seconds handles various durations"
    (is (= 60 (session/config->seconds (session/make-session-config 1))))
    (is (= 600 (session/config->seconds (session/make-session-config 10))))
    (is (= 3600 (session/config->seconds (session/make-session-config 60))))))

(deftest test-make-session-exercise-valid
  (testing "make-session-exercise creates valid session exercise entry"
    (let [exercise {:name "Push-ups" :difficulty 1.2}
          duration 45
          session-ex (session/make-session-exercise exercise duration)]
      (is (map? session-ex))
      (is (contains? session-ex :exercise))
      (is (contains? session-ex :duration-seconds))
      (is (= exercise (:exercise session-ex)))
      (is (= 45 (:duration-seconds session-ex))))))

(deftest test-make-session-exercise-preserves-exercise-data
  (testing "make-session-exercise preserves all exercise data"
    (let [exercise {:name "Squats" :difficulty 1.0}
          session-ex (session/make-session-exercise exercise 60)]
      (is (= "Squats" (get-in session-ex [:exercise :name])))
      (is (= 1.0 (get-in session-ex [:exercise :difficulty]))))))

(deftest test-make-session-plan-valid
  (testing "make-session-plan creates valid session plan"
    (let [exercises [(session/make-session-exercise {:name "Push-ups" :difficulty 1.2} 150)
                     (session/make-session-exercise {:name "Squats" :difficulty 1.0} 150)]
          plan (session/make-session-plan exercises 300)]
      (is (map? plan))
      (is (contains? plan :exercises))
      (is (contains? plan :total-duration-seconds))
      (is (= exercises (:exercises plan)))
      (is (= 300 (:total-duration-seconds plan))))))

(deftest test-make-session-plan-single-exercise
  (testing "make-session-plan works with single exercise"
    (let [exercises [(session/make-session-exercise {:name "Plank" :difficulty 1.5} 300)]
          plan (session/make-session-plan exercises 300)]
      (is (= 1 (count (:exercises plan))))
      (is (= 300 (:total-duration-seconds plan))))))

(deftest test-make-session-plan-multiple-exercises
  (testing "make-session-plan works with multiple exercises"
    (let [exercises [(session/make-session-exercise {:name "Push-ups" :difficulty 1.2} 100)
                     (session/make-session-exercise {:name "Squats" :difficulty 1.0} 100)
                     (session/make-session-exercise {:name "Plank" :difficulty 1.5} 100)]
          plan (session/make-session-plan exercises 300)]
      (is (= 3 (count (:exercises plan))))
      (is (= 300 (:total-duration-seconds plan))))))

(deftest test-default-session-duration-is-5-minutes
  (testing "Default session duration is 5 minutes"
    (is (= 5 session/default-session-duration-minutes))))

;; ============================================================================
;; Property-Based Tests for Session Data Structures (Task 3.1)
;; ============================================================================

;; Generators for property-based testing

(def gen-positive-minutes
  "Generator for positive integer minutes (1 to 120)"
  (gen/choose 1 120))

(def gen-positive-seconds
  "Generator for positive integer seconds (1 to 7200)"
  (gen/choose 1 7200))

(def gen-exercise
  "Generator for valid exercise maps"
  (gen/let [name (gen/not-empty gen/string-alphanumeric)
            difficulty (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
    {:name name :difficulty difficulty}))

;; Property: Session configuration round-trip (minutes to seconds)
(defspec session-config-minutes-to-seconds-property
  100
  (prop/for-all [minutes gen-positive-minutes]
    (let [config (session/make-session-config minutes)
          seconds (session/config->seconds config)]
      (and
       ;; Config should store minutes
       (= minutes (:duration-minutes config))
       ;; Conversion should be correct (minutes * 60)
       (= (* minutes 60) seconds)))))

;; Property: Session exercise structure integrity
(defspec session-exercise-structure-property
  100
  (prop/for-all [exercise gen-exercise
                 duration gen-positive-seconds]
    (let [session-ex (session/make-session-exercise exercise duration)]
      (and
       ;; Should have required keys
       (contains? session-ex :exercise)
       (contains? session-ex :duration-seconds)
       ;; Exercise data should be preserved
       (= exercise (:exercise session-ex))
       ;; Duration should be preserved
       (= duration (:duration-seconds session-ex))))))

;; Property: Session plan structure integrity
(defspec session-plan-structure-property
  100
  (prop/for-all [exercises (gen/not-empty
                            (gen/vector
                             (gen/let [ex gen-exercise
                                       dur gen-positive-seconds]
                               (session/make-session-exercise ex dur))
                             1 10))
                 total-duration gen-positive-seconds]
    (let [plan (session/make-session-plan exercises total-duration)]
      (and
       ;; Should have required keys
       (contains? plan :exercises)
       (contains? plan :total-duration-seconds)
       ;; Exercises should be preserved
       (= exercises (:exercises plan))
       ;; Total duration should be preserved
       (= total-duration (:total-duration-seconds plan))
       ;; Exercises should be a vector
       (vector? (:exercises plan))
       ;; Should have at least one exercise
       (>= (count (:exercises plan)) 1)))))

;; Property: Default duration is always 5 minutes
(deftest default-duration-constant-property
  (testing "Default session duration is always 5 minutes"
    (is (= 5 session/default-session-duration-minutes))
    (is (= 300 (session/config->seconds 
                (session/make-session-config session/default-session-duration-minutes))))))

;; ============================================================================
;; Unit Tests for Weighted Time Distribution (Task 3.2)
;; ============================================================================

(deftest test-calculate-difficulty-sum-single-exercise
  (testing "calculate-difficulty-sum with single exercise"
    (let [exercises [{:name "Push-ups" :difficulty 1.5}]]
      ;; Sum of inverse difficulties: 1/1.5 = 0.6666...
      (is (= (/ 1.0 1.5) (session/calculate-difficulty-sum exercises))))))

(deftest test-calculate-difficulty-sum-multiple-exercises
  (testing "calculate-difficulty-sum with multiple exercises"
    (let [exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}
                     {:name "Plank" :difficulty 1.5}]]
      ;; Sum of inverse difficulties: 1/1.2 + 1/1.0 + 1/1.5 = 0.833... + 1.0 + 0.666... = 2.5
      (is (< (Math/abs (- 2.5 (session/calculate-difficulty-sum exercises))) 0.01)))))

(deftest test-calculate-base-time
  (testing "calculate-base-time divides duration by difficulty sum"
    (is (= 100 (session/calculate-base-time 300 3.0)))
    (is (= 50 (session/calculate-base-time 300 6.0)))))

(deftest test-calculate-exercise-duration
  (testing "calculate-exercise-duration divides base time by difficulty"
    ;; duration = base-time * (1/difficulty) for non-sided exercises
    ;; Higher difficulty = less time
    (is (= 83 (session/calculate-exercise-duration 100 {:difficulty 1.2})))
    (is (= 100 (session/calculate-exercise-duration 100 {:difficulty 1.0})))
    (is (= 66 (session/calculate-exercise-duration 100 {:difficulty 1.5})))))

(deftest test-calculate-exercise-duration-floors-result
  (testing "calculate-exercise-duration floors fractional results"
    ;; duration = base-time * (1/difficulty) (floored)
    (is (= 123 (session/calculate-exercise-duration 100 {:difficulty 0.81})))
    (is (= 84 (session/calculate-exercise-duration 100 {:difficulty 1.19})))))

(deftest test-calculate-exercise-duration-sided
  (testing "calculate-exercise-duration gives 1.5x time to sided exercises"
    ;; duration = base-time * (1.5/difficulty) for sided exercises
    (is (= 150 (session/calculate-exercise-duration 100 {:difficulty 1.0 :sided true})))
    (is (= 125 (session/calculate-exercise-duration 100 {:difficulty 1.2 :sided true})))
    (is (= 100 (session/calculate-exercise-duration 100 {:difficulty 1.5 :sided true})))))

(deftest test-distribute-remaining-seconds-no-remainder
  (testing "distribute-remaining-seconds with no remaining seconds"
    (let [exercises [{:name "Push-ups" :difficulty 1.0}
                     {:name "Squats" :difficulty 1.0}]
          base-time 100
          initial-durations [100 100]
          result (session/distribute-remaining-seconds exercises base-time initial-durations 0)]
      (is (= [100 100] result)))))

(deftest test-distribute-remaining-seconds-with-remainder
  (testing "distribute-remaining-seconds distributes extra seconds"
    (let [exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}
                     {:name "Plank" :difficulty 1.5}]
          base-time 81.08108108108108  ; 300 / 3.7
          initial-durations [97 81 121]  ; floored values
          ;; Total = 299, so 1 second remaining
          result (session/distribute-remaining-seconds exercises base-time initial-durations 1)]
      ;; One exercise should get +1 second
      (is (= 300 (reduce + result)))
      (is (= 3 (count result))))))

(deftest test-distribute-time-by-difficulty-simple-case
  (testing "distribute-time-by-difficulty with simple equal difficultys"
    (let [exercises [{:name "Push-ups" :difficulty 1.0}
                     {:name "Squats" :difficulty 1.0}]
          result (session/distribute-time-by-difficulty exercises 300)]
      (is (= 2 (count result)))
      ;; Each should get 150 seconds (300 / 2)
      (is (= 150 (get-in result [0 :duration-seconds])))
      (is (= 150 (get-in result [1 :duration-seconds])))
      ;; Total should equal 300
      (is (= 300 (reduce + (map :duration-seconds result)))))))

(deftest test-distribute-time-by-difficulty-different-difficultys
  (testing "distribute-time-by-difficulty with different difficultys"
    (let [exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}
                     {:name "Plank" :difficulty 1.5}]
          result (session/distribute-time-by-difficulty exercises 300)]
      (is (= 3 (count result)))
      ;; Total should equal 300 (time conservation)
      (is (= 300 (reduce + (map :duration-seconds result))))
      ;; With inverse difficulty: higher difficulty = LESS time
      ;; Exercises with lower difficulties should get more time
      (let [durations (mapv :duration-seconds result)]
        ;; Squats (1.0) should have most time
        ;; Push-ups (1.2) should have less than Squats
        ;; Plank (1.5) should have least time
        (is (> (nth durations 1) (nth durations 0)))  ; Squats > Push-ups
        (is (> (nth durations 0) (nth durations 2)))))))

(deftest test-distribute-time-by-difficulty-single-exercise
  (testing "distribute-time-by-difficulty with single exercise gets all time"
    (let [exercises [{:name "Plank" :difficulty 1.5}]
          result (session/distribute-time-by-difficulty exercises 300)]
      (is (= 1 (count result)))
      (is (= 300 (get-in result [0 :duration-seconds]))))))

(deftest test-distribute-time-by-difficulty-preserves-exercise-data
  (testing "distribute-time-by-difficulty preserves exercise name and difficulty"
    (let [exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}]
          result (session/distribute-time-by-difficulty exercises 300)]
      (is (= "Push-ups" (get-in result [0 :exercise :name])))
      (is (= 1.2 (get-in result [0 :exercise :difficulty])))
      (is (= "Squats" (get-in result [1 :exercise :name])))
      (is (= 1.0 (get-in result [1 :exercise :difficulty]))))))

(deftest test-distribute-time-by-difficulty-time-conservation-property
  (testing "distribute-time-by-difficulty always conserves total time"
    (let [test-cases [;; Various durations and exercise combinations
                      {:exercises [{:name "A" :difficulty 1.0}] :duration 60}
                      {:exercises [{:name "A" :difficulty 1.0} {:name "B" :difficulty 1.0}] :duration 120}
                      {:exercises [{:name "A" :difficulty 0.5} {:name "B" :difficulty 2.0}] :duration 300}
                      {:exercises [{:name "A" :difficulty 1.2} {:name "B" :difficulty 1.0} {:name "C" :difficulty 1.5}] :duration 300}
                      {:exercises [{:name "A" :difficulty 0.8} {:name "B" :difficulty 1.3} {:name "C" :difficulty 1.1} {:name "D" :difficulty 1.7}] :duration 600}]]
      (doseq [{:keys [exercises duration]} test-cases]
        (let [result (session/distribute-time-by-difficulty exercises duration)
              total (reduce + (map :duration-seconds result))]
          (is (= duration total)
              (str "Failed for exercises: " exercises " with duration: " duration)))))))

;; ============================================================================
;; Property-Based Tests for Weighted Time Distribution (Task 3.3)
;; ============================================================================

;; Generator for valid exercise difficulty (0.5 to 2.0)
(def gen-exercise-difficulty
  "Generator for valid exercise difficultys between 0.5 and 2.0"
  (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false}))

;; Generator for exercise with valid difficulty
(def gen-weighted-exercise
  "Generator for exercise maps with valid difficultys"
  (gen/let [name (gen/not-empty gen/string-alphanumeric)
            difficulty gen-exercise-difficulty]
    {:name name :difficulty difficulty}))

;; Generator for non-empty vector of exercises
(def gen-exercise-list
  "Generator for non-empty vector of exercises (1 to 10 exercises)"
  (gen/not-empty (gen/vector gen-weighted-exercise 1 10)))

;; Generator for exercise library with unique names
(def gen-unique-exercise-library
  "Generator for exercise library with unique exercise names (1 to 10 exercises)"
  (gen/let [num-exercises (gen/choose 1 10)]
    (gen/fmap
     (fn [exercises]
       ;; Ensure unique names by adding index suffix if needed
       (let [name-counts (atom {})
             unique-exercises (mapv (fn [ex]
                                      (let [base-name (:name ex)
                                            count (get @name-counts base-name 0)]
                                        (swap! name-counts update base-name (fnil inc 0))
                                        (if (zero? count)
                                          ex
                                          (assoc ex :name (str base-name "-" count)))))
                                    exercises)]
         (vec unique-exercises)))
     (gen/vector gen-weighted-exercise num-exercises num-exercises))))

;; Generator for positive session duration in seconds (1 minute to 2 hours)
(def gen-session-duration-seconds
  "Generator for session duration in seconds (60 to 7200)"
  (gen/choose 60 7200))

;; Property 5: Time Conservation
;; **Validates: Requirements 2.5**
(defspec ^{:feature "exercise-timer-app"
           :property 5
           :description "Time Conservation"}
  time-conservation-property
  100
  (prop/for-all [exercises gen-exercise-list
                 total-duration gen-session-duration-seconds]
    (let [result (session/distribute-time-by-difficulty exercises total-duration)
          sum-of-durations (reduce + (map :duration-seconds result))]
      ;; The sum of all individual exercise durations should equal the total session duration
      (= total-duration sum-of-durations))))

;; ============================================================================
;; Unit Tests for Time Constraints (Task 5.1)
;; ============================================================================

(deftest test-apply-time-constraints-within-bounds
  (testing "apply-time-constraints preserves duration within valid range"
    ;; Test values within the 20-120 second range
    (is (= 20 (session/apply-time-constraints 20)))
    (is (= 60 (session/apply-time-constraints 60)))
    (is (= 90 (session/apply-time-constraints 90)))
    (is (= 120 (session/apply-time-constraints 120)))))

(deftest test-apply-time-constraints-minimum-enforcement
  (testing "apply-time-constraints enforces minimum 20 seconds"
    ;; Values below 20 should be raised to 20
    (is (= 20 (session/apply-time-constraints 1)))
    (is (= 20 (session/apply-time-constraints 10)))
    (is (= 20 (session/apply-time-constraints 15)))
    (is (= 20 (session/apply-time-constraints 19)))
    (is (= 20 (session/apply-time-constraints 19.9)))))

(deftest test-apply-time-constraints-maximum-enforcement
  (testing "apply-time-constraints enforces maximum 120 seconds"
    ;; Values above 120 should be capped at 120
    (is (= 120 (session/apply-time-constraints 121)))
    (is (= 120 (session/apply-time-constraints 150)))
    (is (= 120 (session/apply-time-constraints 200)))
    (is (= 120 (session/apply-time-constraints 300)))
    (is (= 120 (session/apply-time-constraints 1000)))))

(deftest test-apply-time-constraints-returns-integer
  (testing "apply-time-constraints returns integer values"
    ;; Fractional values should be converted to integers
    (is (integer? (session/apply-time-constraints 45.7)))
    (is (= 45 (session/apply-time-constraints 45.7)))
    (is (= 75 (session/apply-time-constraints 75.3)))
    (is (= 20 (session/apply-time-constraints 15.9)))
    (is (= 120 (session/apply-time-constraints 125.5)))))

(deftest test-apply-time-constraints-boundary-values
  (testing "apply-time-constraints handles exact boundary values"
    ;; Test exact min and max boundaries
    (is (= 20 (session/apply-time-constraints 20)))
    (is (= 120 (session/apply-time-constraints 120)))
    ;; Test just inside boundaries
    (is (= 21 (session/apply-time-constraints 21)))
    (is (= 119 (session/apply-time-constraints 119)))
    ;; Test just outside boundaries
    (is (= 20 (session/apply-time-constraints 19)))
    (is (= 120 (session/apply-time-constraints 121)))))

(deftest test-min-max-constants
  (testing "Minimum and maximum duration constants are correct"
    (is (= 20 session/min-exercise-duration-seconds))
    (is (= 120 session/max-exercise-duration-seconds))))

;; ============================================================================
;; Unit Tests for Exercise Selection with No-Repeat Logic (Task 3.4)
;; ============================================================================

(deftest test-select-exercises-round-robin-simple-case
  (testing "select-exercises-round-robin with count <= library size"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}
                   {:name "Plank" :difficulty 1.5}
                   {:name "Jumping Jacks" :difficulty 0.8}
                   {:name "Lunges" :difficulty 1.0}]
          result (session/select-exercises-round-robin library 3)]
      ;; Should return exactly 3 exercises
      (is (= 3 (count result)))
      ;; All selected exercises should be from the library
      (is (every? (fn [ex] (some #(= (:name %) (:name ex)) library)) result))
      ;; All selected exercises should be unique (no repeats)
      (is (= (count result) (count (set (map :name result))))))))

(deftest test-select-exercises-round-robin-exact-library-size
  (testing "select-exercises-round-robin with count = library size"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}
                   {:name "Plank" :difficulty 1.5}]
          result (session/select-exercises-round-robin library 3)]
      ;; Should return all 3 exercises
      (is (= 3 (count result)))
      ;; All exercises should be unique
      (is (= 3 (count (set (map :name result))))))))

(deftest test-select-exercises-round-robin-single-exercise
  (testing "select-exercises-round-robin with single exercise requested"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}
                   {:name "Plank" :difficulty 1.5}]
          result (session/select-exercises-round-robin library 1)]
      ;; Should return exactly 1 exercise
      (is (= 1 (count result)))
      ;; Exercise should be from the library
      (is (some #(= (:name %) (:name (first result))) library)))))

(deftest test-select-exercises-round-robin-cycling
  (testing "select-exercises-round-robin cycles through library when count > library size"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}
                   {:name "Plank" :difficulty 1.5}]
          result (session/select-exercises-round-robin library 7)]
      ;; Should return exactly 7 exercises
      (is (= 7 (count result)))
      ;; All exercises should be from the library
      (is (every? (fn [ex] (some #(= (:name %) (:name ex)) library)) result))
      ;; Each exercise should appear at least twice (7 exercises, 3 in library)
      (let [name-counts (frequencies (map :name result))]
        (is (every? #(>= % 2) (vals name-counts)))))))

(deftest test-select-exercises-round-robin-no-consecutive-duplicates
  (testing "select-exercises-round-robin avoids consecutive duplicates"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}
                   {:name "Plank" :difficulty 1.5}]
          ;; Test a few times due to randomization
          results (repeatedly 3 #(session/select-exercises-round-robin library 10))]
      (doseq [result results]
        ;; Check no consecutive duplicates
        (is (every? (fn [[a b]] (not= (:name a) (:name b)))
                    (partition 2 1 result))
            "Found consecutive duplicate exercises")))))

(deftest test-select-exercises-round-robin-library-membership
  (testing "select-exercises-round-robin only selects from library"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}
                   {:name "Plank" :difficulty 1.5}]
          library-names (set (map :name library))
          result (session/select-exercises-round-robin library 10)]
      ;; All selected exercise names should be in the library
      (is (every? #(contains? library-names (:name %)) result)))))

(deftest test-select-exercises-round-robin-exhausts-library-before-repeat
  (testing "select-exercises-round-robin uses all library exercises before repeating"
    (let [library [{:name "A" :difficulty 1.0}
                   {:name "B" :difficulty 1.0}
                   {:name "C" :difficulty 1.0}]
          result (session/select-exercises-round-robin library 5)]
      ;; First 3 should be all different (all library exercises used)
      (is (= 3 (count (set (map :name (take 3 result))))))
      ;; All 5 exercises should be from library
      (is (every? (fn [ex] (some #(= (:name %) (:name ex)) library)) result)))))

(deftest test-select-exercises-round-robin-preserves-exercise-data
  (testing "select-exercises-round-robin preserves exercise name and difficulty"
    (let [library [{:name "Push-ups" :difficulty 1.2}
                   {:name "Squats" :difficulty 1.0}]
          result (session/select-exercises-round-robin library 3)]
      ;; Each selected exercise should have matching difficulty from library
      (doseq [selected result]
        (let [original (first (filter #(= (:name %) (:name selected)) library))]
          (is (some? original))
          (is (= (:difficulty original) (:difficulty selected))))))))

;; ============================================================================
;; Property-Based Tests for Exercise Selection (Task 3.5)
;; ============================================================================

;; Property 2: Minimum Exercise Count
;; **Validates: Requirements 2.1**
(defspec ^{:feature "exercise-timer-app"
           :property 2
           :description "Minimum Exercise Count"}
  minimum-exercise-count-property
  100
  (prop/for-all [library gen-unique-exercise-library
                 num-exercises (gen/choose 1 20)]
    (let [result (session/select-exercises-round-robin library num-exercises)]
      ;; The generated exercise sequence should contain at least one exercise
      (>= (count result) 1))))

;; Property 3: Exercise Library Membership
;; **Validates: Requirements 2.2**
(defspec ^{:feature "exercise-timer-app"
           :property 3
           :description "Exercise Library Membership"}
  exercise-library-membership-property
  100
  (prop/for-all [library gen-unique-exercise-library
                 num-exercises (gen/choose 1 20)]
    (let [result (session/select-exercises-round-robin library num-exercises)
          library-names (set (map :name library))]
      ;; All exercises in the sequence should exist in the exercise library
      (every? (fn [exercise]
                (contains? library-names (:name exercise)))
              result))))

;; Property 4: Exercise Selection Constraints (Relaxed)
;; **Validates: Requirements 2.3**
(defspec ^{:feature "exercise-timer-app"
           :property 4
           :description "Exercise Selection Constraints"}
  exercise-selection-constraints-property
  100
  (prop/for-all [library gen-unique-exercise-library
                 num-exercises (gen/choose 1 30)]
    (let [result (session/select-exercises-round-robin library num-exercises)
          exercise-names (mapv :name result)
          library-names (set (map :name library))
          library-size (count library)]
      (and
       ;; 1. All exercises come from the library
       (every? (fn [ex-name]
                 (contains? library-names ex-name))
               exercise-names)
       
       ;; 2. No consecutive duplicates (except when library has only 1 exercise)
       (if (= library-size 1)
         ;; With single-exercise library, consecutive duplicates are unavoidable
         true
         ;; With multiple exercises, no consecutive duplicates should occur
         (every? (fn [[a b]]
                   (not= a b))
                 (partition 2 1 exercise-names)))
       
       ;; 3. Exercises cycle through the library
       ;; (but not necessarily completing full cycles before repeating)
       ;; This means: if we have more exercises than library size,
       ;; we should see exercises from throughout the library, not just a subset
       (if (> (count result) library-size)
         ;; If result is larger than library, all library exercises should appear
         (= library-names (set exercise-names))
         ;; If result is smaller or equal to library, this property holds trivially
         true)))))



;; ============================================================================
;; Unit Tests for generate-session Function (Task 3.6)
;; ============================================================================

(deftest test-generate-session-basic
  (testing "generate-session creates valid session plan"
    (let [config (session/make-session-config 5)
          exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}
                     {:name "Plank" :difficulty 1.5}]
          result (session/generate-session config exercises)]
      ;; Should return a valid session plan
      (is (map? result))
      (is (contains? result :exercises))
      (is (contains? result :total-duration-seconds))
      ;; Total duration should be 5 minutes = 300 seconds
      (is (= 300 (:total-duration-seconds result)))
      ;; Should have exercises
      (is (>= (count (:exercises result)) 1)))))

(deftest test-generate-session-default-duration
  (testing "generate-session with default 5-minute duration"
    (let [config (session/make-session-config session/default-session-duration-minutes)
          ;; Provide enough exercises for a 5-minute session (aim for ~40s each = 7-8 exercises)
          exercises [{:name "Push-ups" :difficulty 1.2 :tags [] :equipment []}
                     {:name "Squats" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Plank" :difficulty 1.5 :tags [] :equipment []}
                     {:name "Lunges" :difficulty 1.1 :tags [] :equipment []}
                     {:name "Sit-ups" :difficulty 0.9 :tags [] :equipment []}
                     {:name "Jumping Jacks" :difficulty 0.7 :tags [] :equipment []}
                     {:name "Mountain Climbers" :difficulty 1.4 :tags [] :equipment []}
                     {:name "Burpees" :difficulty 2.0 :tags [] :equipment []}]
          result (session/generate-session config exercises)]
      ;; Should use default 5 minutes = 300 seconds
      (is (= 300 (:total-duration-seconds result)))
      ;; Sum of exercise durations should equal total
      (let [sum (reduce + (map :duration-seconds (:exercises result)))]
        (is (= 300 sum))))))

(deftest test-generate-session-single-exercise
  (testing "generate-session with single exercise"
    (let [config (session/make-session-config 5)
          exercises [{:name "Plank" :difficulty 1.5 :tags [] :equipment []}]
          result (session/generate-session config exercises)]
      ;; With a single exercise, it will be repeated multiple times
      ;; The session aims for ~40s per exercise, so 300s / 40s = 7.5, rounds to 7
      ;; But with only 1 exercise available, it uses min(7, 1) = 1 exercise
      ;; Then repeats it to fill time, aiming for ~40s each = 5 repetitions (200s total)
      (is (>= (count (:exercises result)) 1))
      ;; All should be the same exercise
      (is (every? #(= "Plank" (get-in % [:exercise :name])) (:exercises result)))
      ;; Total duration should be consistent
      (let [total (:total-duration-seconds result)
            sum (reduce + (map :duration-seconds (:exercises result)))]
        ;; Sum should equal total (time conservation)
        (is (= total sum))
        ;; Total should be reasonable for the session (at least 100s for a 5-min target)
        (is (>= total 100)))
      ;; All durations should be <= 120 seconds
      (is (every? #(<= (:duration-seconds %) 120) (:exercises result))))))

(deftest test-generate-session-time-conservation
  (testing "generate-session conserves total time"
    (let [config (session/make-session-config 10)
          ;; Provide enough exercises for a 10-minute session
          exercises [{:name "Push-ups" :difficulty 1.2 :tags [] :equipment []}
                     {:name "Squats" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Plank" :difficulty 1.5 :tags [] :equipment []}
                     {:name "Jumping Jacks" :difficulty 0.8 :tags [] :equipment []}
                     {:name "Lunges" :difficulty 1.1 :tags [] :equipment []}
                     {:name "Sit-ups" :difficulty 0.9 :tags [] :equipment []}
                     {:name "Mountain Climbers" :difficulty 1.4 :tags [] :equipment []}
                     {:name "Burpees" :difficulty 2.0 :tags [] :equipment []}
                     {:name "High Knees" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Wall Sit" :difficulty 1.5 :tags [] :equipment []}
                     {:name "Calf Raises" :difficulty 0.6 :tags [] :equipment []}
                     {:name "Glute Bridges" :difficulty 0.8 :tags [] :equipment []}
                     {:name "Russian Twists" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Leg Raises" :difficulty 1.4 :tags [] :equipment []}
                     {:name "Tricep Dips" :difficulty 1.5 :tags [] :equipment []}]
          result (session/generate-session config exercises)]
      ;; Total should be 10 minutes = 600 seconds
      (is (= 600 (:total-duration-seconds result)))
      ;; Sum of exercise durations should equal total
      (let [sum (reduce + (map :duration-seconds (:exercises result)))]
        (is (= 600 sum))))))

(deftest test-generate-session-specific-difficulty-calculations
  (testing "generate-session with known difficulty calculations"
    (let [config (session/make-session-config 5)
          ;; Use difficultys that sum to 3.0 for easy calculation
          ;; 300 seconds / 3.0 = 100 seconds base time
          exercises [{:name "A" :difficulty 1.0}  ; should get ~100 seconds
                     {:name "B" :difficulty 1.0}  ; should get ~100 seconds
                     {:name "C" :difficulty 1.0}] ; should get ~100 seconds
          result (session/generate-session config exercises)]
      ;; Each exercise should get approximately 100 seconds
      (let [durations (map :duration-seconds (:exercises result))]
        ;; All should be close to 100 (within rounding)
        (is (every? #(and (>= % 99) (<= % 101)) durations))
        ;; Total should be exactly 300
        (is (= 300 (reduce + durations)))))))

(deftest test-generate-session-preserves-exercise-data
  (testing "generate-session preserves exercise names and difficultys"
    (let [config (session/make-session-config 5)
          exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}]
          result (session/generate-session config exercises)]
      ;; Exercise data should be preserved in the session plan
      (let [session-exercises (:exercises result)
            names (set (map #(get-in % [:exercise :name]) session-exercises))
            original-names (set (map :name exercises))]
        ;; All names should be from the original library
        (is (every? #(contains? original-names %) names))))))

(deftest test-generate-session-library-membership
  (testing "generate-session only uses exercises from library"
    (let [config (session/make-session-config 5)
          exercises [{:name "Push-ups" :difficulty 1.2}
                     {:name "Squats" :difficulty 1.0}
                     {:name "Plank" :difficulty 1.5}]
          library-names (set (map :name exercises))
          result (session/generate-session config exercises)]
      ;; All exercises in session should be from the library
      (is (every? (fn [session-ex]
                    (contains? library-names (get-in session-ex [:exercise :name])))
                  (:exercises result))))))

(deftest test-generate-session-minutes-to-seconds-conversion
  (testing "generate-session correctly converts minutes to seconds"
    (let [test-cases [{:minutes 1 :expected-seconds 60}
                      {:minutes 5 :expected-seconds 300}
                      {:minutes 10 :expected-seconds 600}
                      {:minutes 30 :expected-seconds 1800}]
          ;; Provide enough exercises for any duration
          exercises [{:name "Ex1" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex2" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex3" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex4" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex5" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex6" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex7" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex8" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex9" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex10" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex11" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex12" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex13" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex14" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex15" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex16" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex17" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex18" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex19" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex20" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex21" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex22" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex23" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex24" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex25" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex26" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex27" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex28" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex29" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex30" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex31" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex32" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex33" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex34" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex35" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex36" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex37" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex38" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex39" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex40" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex41" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex42" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex43" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex44" :difficulty 1.0 :tags [] :equipment []}
                     {:name "Ex45" :difficulty 1.0 :tags [] :equipment []}]]
      (doseq [{:keys [minutes expected-seconds]} test-cases]
        (let [config (session/make-session-config minutes)
              result (session/generate-session config exercises)]
          (is (= expected-seconds (:total-duration-seconds result))
              (str "Failed for " minutes " minutes")))))))

;; ============================================================================
;; Unit Tests for split-long-exercises Function (Task 5.4)
;; ============================================================================

(deftest test-split-long-exercises-no-splitting-needed
  (testing "split-long-exercises returns unchanged when all exercises within max duration"
    (let [session-exercises [(session/make-session-exercise {:name "Push-ups" :difficulty 1.2} 60)
                             (session/make-session-exercise {:name "Squats" :difficulty 1.0} 90)
                             (session/make-session-exercise {:name "Plank" :difficulty 1.5} 120)]
          result (session/split-long-exercises session-exercises)]
      ;; Should return same exercises (no splitting needed)
      (is (= 3 (count result)))
      ;; Durations should be unchanged
      (is (= [60 90 120] (mapv :duration-seconds result))))))

(deftest test-split-long-exercises-single-exercise-needs-split
  (testing "split-long-exercises splits single exercise exceeding max duration"
    (let [;; Exercise with 240 seconds (should split into 2 x 120)
          session-exercises [(session/make-session-exercise {:name "Long-Exercise" :difficulty 0.5} 240)]
          result (session/split-long-exercises session-exercises)]
      ;; Should have 2 exercises now
      (is (= 2 (count result)))
      ;; Both should be the same exercise
      (is (= "Long-Exercise" (get-in result [0 :exercise :name])))
      (is (= "Long-Exercise" (get-in result [1 :exercise :name])))
      ;; Each should have 120 seconds
      (is (= 120 (:duration-seconds (nth result 0))))
      (is (= 120 (:duration-seconds (nth result 1))))
      ;; Total duration should be conserved
      (is (= 240 (reduce + (map :duration-seconds result)))))))

(deftest test-split-long-exercises-multiple-splits
  (testing "split-long-exercises handles exercise needing multiple splits"
    (let [;; Exercise with 360 seconds (should split into 3 x 120)
          session-exercises [(session/make-session-exercise {:name "Very-Long" :difficulty 0.5} 360)]
          result (session/split-long-exercises session-exercises)]
      ;; Should have 3 exercises now
      (is (= 3 (count result)))
      ;; All should be the same exercise
      (is (every? #(= "Very-Long" (get-in % [:exercise :name])) result))
      ;; Each should have 120 seconds
      (is (every? #(= 120 (:duration-seconds %)) result))
      ;; Total duration should be conserved
      (is (= 360 (reduce + (map :duration-seconds result)))))))

(deftest test-split-long-exercises-uneven-split
  (testing "split-long-exercises handles uneven splits correctly"
    (let [;; Exercise with 250 seconds (should split into 2: 125 + 125, but max is 120, so 2 x 125)
          ;; Actually: 250 / 120 = 2.08, so ceil = 3 splits
          ;; 250 / 3 = 83.33, floor = 83 base
          ;; 83 * 3 = 249, remaining = 1
          ;; So: 84, 83, 83
          session-exercises [(session/make-session-exercise {:name "Uneven" :difficulty 0.8} 250)]
          result (session/split-long-exercises session-exercises)]
      ;; Should have 3 exercises (250 / 120 = 2.08, ceil = 3)
      (is (= 3 (count result)))
      ;; All should be the same exercise
      (is (every? #(= "Uneven" (get-in % [:exercise :name])) result))
      ;; Total duration should be conserved
      (is (= 250 (reduce + (map :duration-seconds result))))
      ;; All durations should be <= 120
      (is (every? #(<= (:duration-seconds %) 120) result)))))

(deftest test-split-long-exercises-mixed-exercises
  (testing "split-long-exercises handles mix of long and normal exercises"
    (let [session-exercises [(session/make-session-exercise {:name "Normal-1" :difficulty 1.0} 60)
                             (session/make-session-exercise {:name "Long" :difficulty 0.5} 240)
                             (session/make-session-exercise {:name "Normal-2" :difficulty 1.2} 90)]
          result (session/split-long-exercises session-exercises)]
      ;; Should have 4 exercises (2 normal + 2 from split)
      (is (= 4 (count result)))
      ;; Total duration should be conserved
      (is (= 390 (reduce + (map :duration-seconds result))))
      ;; All durations should be <= 120
      (is (every? #(<= (:duration-seconds %) 120) result))
      ;; Should have 2 occurrences of "Long"
      (is (= 2 (count (filter #(= "Long" (get-in % [:exercise :name])) result))))
      ;; Should have 1 occurrence each of Normal-1 and Normal-2
      (is (= 1 (count (filter #(= "Normal-1" (get-in % [:exercise :name])) result))))
      (is (= 1 (count (filter #(= "Normal-2" (get-in % [:exercise :name])) result)))))))

(deftest test-split-long-exercises-preserves-exercise-data
  (testing "split-long-exercises preserves exercise name and difficulty"
    (let [exercise {:name "Test-Exercise" :difficulty 0.7}
          session-exercises [(session/make-session-exercise exercise 240)]
          result (session/split-long-exercises session-exercises)]
      ;; All split exercises should have same name and difficulty
      (is (every? #(= "Test-Exercise" (get-in % [:exercise :name])) result))
      (is (every? #(= 0.7 (get-in % [:exercise :difficulty])) result)))))

(deftest test-split-long-exercises-time-conservation
  (testing "split-long-exercises always conserves total time"
    (let [test-cases [;; Various scenarios
                      [(session/make-session-exercise {:name "A" :difficulty 1.0} 60)]
                      [(session/make-session-exercise {:name "A" :difficulty 0.5} 240)]
                      [(session/make-session-exercise {:name "A" :difficulty 0.5} 360)]
                      [(session/make-session-exercise {:name "A" :difficulty 1.0} 60)
                       (session/make-session-exercise {:name "B" :difficulty 0.5} 250)
                       (session/make-session-exercise {:name "C" :difficulty 1.2} 90)]]]
      (doseq [session-exercises test-cases]
        (let [original-total (reduce + (map :duration-seconds session-exercises))
              result (session/split-long-exercises session-exercises)
              result-total (reduce + (map :duration-seconds result))]
          (is (= original-total result-total)
              (str "Failed time conservation for: " session-exercises)))))))

(deftest test-split-long-exercises-max-duration-enforcement
  (testing "split-long-exercises ensures all durations <= max"
    (let [;; Create exercises with various long durations
          session-exercises [(session/make-session-exercise {:name "A" :difficulty 0.5} 240)
                             (session/make-session-exercise {:name "B" :difficulty 0.6} 300)
                             (session/make-session-exercise {:name "C" :difficulty 0.7} 180)]
          result (session/split-long-exercises session-exercises)]
      ;; All resulting exercises should have duration <= 120
      (is (every? #(<= (:duration-seconds %) 120) result)))))

(deftest test-split-long-exercises-distribution
  (testing "split-long-exercises distributes split exercises throughout session"
    (let [;; Mix of normal and long exercises
          session-exercises [(session/make-session-exercise {:name "Normal-1" :difficulty 1.0} 60)
                             (session/make-session-exercise {:name "Long-1" :difficulty 0.5} 240)
                             (session/make-session-exercise {:name "Normal-2" :difficulty 1.2} 90)
                             (session/make-session-exercise {:name "Long-2" :difficulty 0.6} 240)]
          result (session/split-long-exercises session-exercises)]
      ;; Should have 6 exercises total (2 normal + 2*2 from splits)
      (is (= 6 (count result)))
      ;; Split exercises should be distributed (not all at the end)
      ;; Check that we don't have all "Long-1" or "Long-2" consecutive
      (let [names (mapv #(get-in % [:exercise :name]) result)
            ;; Count max consecutive occurrences of any name
            max-consecutive (apply max
                                   (map count
                                        (partition-by identity names)))]
        ;; No exercise should appear more than 2 times consecutively
        ;; (since we have 2 splits of each long exercise)
        (is (<= max-consecutive 2)
            (str "Found too many consecutive exercises: " names))))))

;; Note: test-split-long-exercises-empty-input removed because the function
;; has a precondition (seq session-exercises) that prevents empty input

(deftest test-split-long-exercises-exactly-at-max
  (testing "split-long-exercises does not split exercises exactly at max duration"
    (let [session-exercises [(session/make-session-exercise {:name "At-Max" :difficulty 1.0} 120)]
          result (session/split-long-exercises session-exercises)]
      ;; Should not split (120 is at the max, not exceeding)
      (is (= 1 (count result)))
      (is (= 120 (:duration-seconds (first result)))))))

(deftest test-split-long-exercises-just-over-max
  (testing "split-long-exercises splits exercises just over max duration"
    (let [session-exercises [(session/make-session-exercise {:name "Just-Over" :difficulty 1.0} 121)]
          result (session/split-long-exercises session-exercises)]
      ;; Should split into 2 exercises
      (is (= 2 (count result)))
      ;; Total should be conserved
      (is (= 121 (reduce + (map :duration-seconds result))))
      ;; Both should be <= 120
      (is (every? #(<= (:duration-seconds %) 120) result)))))

;; ============================================================================
;; Unit Tests for sort-by-difficulty Function (Task 6.1)
;; ============================================================================

(deftest test-sort-by-difficulty-ascending-order
  (testing "sort-by-difficulty sorts exercises by difficulty in ascending order"
    (let [exercises [{:name "Hard" :difficulty 1.8}
                     {:name "Easy" :difficulty 0.5}
                     {:name "Medium" :difficulty 1.0}]
          result (session/sort-by-difficulty exercises)]
      ;; Should be sorted: Easy (0.5), Medium (1.0), Hard (1.8)
      (is (= ["Easy" "Medium" "Hard"] (mapv :name result)))
      (is (= [0.5 1.0 1.8] (mapv :difficulty result))))))

(deftest test-sort-by-difficulty-already-sorted
  (testing "sort-by-difficulty handles already sorted exercises"
    (let [exercises [{:name "Easy" :difficulty 0.5}
                     {:name "Medium" :difficulty 1.0}
                     {:name "Hard" :difficulty 1.8}]
          result (session/sort-by-difficulty exercises)]
      ;; Should remain in same order
      (is (= ["Easy" "Medium" "Hard"] (mapv :name result)))
      (is (= [0.5 1.0 1.8] (mapv :difficulty result))))))

(deftest test-sort-by-difficulty-reverse-sorted
  (testing "sort-by-difficulty handles reverse sorted exercises"
    (let [exercises [{:name "Hard" :difficulty 1.8}
                     {:name "Medium" :difficulty 1.0}
                     {:name "Easy" :difficulty 0.5}]
          result (session/sort-by-difficulty exercises)]
      ;; Should reverse the order
      (is (= ["Easy" "Medium" "Hard"] (mapv :name result)))
      (is (= [0.5 1.0 1.8] (mapv :difficulty result))))))

(deftest test-sort-by-difficulty-equal-difficulties
  (testing "sort-by-difficulty handles exercises with equal difficulties"
    (let [exercises [{:name "A" :difficulty 1.0}
                     {:name "B" :difficulty 1.0}
                     {:name "C" :difficulty 1.0}]
          result (session/sort-by-difficulty exercises)]
      ;; All have same difficulty, order may vary but all should be present
      (is (= 3 (count result)))
      (is (every? #(= 1.0 (:difficulty %)) result))
      (is (= #{"A" "B" "C"} (set (map :name result)))))))

(deftest test-sort-by-difficulty-single-exercise
  (testing "sort-by-difficulty handles single exercise"
    (let [exercises [{:name "Only" :difficulty 1.2}]
          result (session/sort-by-difficulty exercises)]
      ;; Should return same exercise
      (is (= 1 (count result)))
      (is (= "Only" (:name (first result))))
      (is (= 1.2 (:difficulty (first result)))))))

(deftest test-sort-by-difficulty-preserves-exercise-data
  (testing "sort-by-difficulty preserves all exercise data"
    (let [exercises [{:name "Push-ups" :difficulty 1.2 :equipment ["None"]}
                     {:name "Wall Sit" :difficulty 1.4 :equipment ["A wall"]}
                     {:name "Jumping Jacks" :difficulty 0.8 :equipment ["None"]}]
          result (session/sort-by-difficulty exercises)]
      ;; Should preserve all fields
      (is (= "Jumping Jacks" (:name (nth result 0))))
      (is (= ["None"] (:equipment (nth result 0))))
      (is (= "Push-ups" (:name (nth result 1))))
      (is (= ["None"] (:equipment (nth result 1))))
      (is (= "Wall Sit" (:name (nth result 2))))
      (is (= ["A wall"] (:equipment (nth result 2)))))))

(deftest test-sort-by-difficulty-returns-vector
  (testing "sort-by-difficulty returns a vector"
    (let [exercises [{:name "A" :difficulty 1.0}
                     {:name "B" :difficulty 0.5}]
          result (session/sort-by-difficulty exercises)]
      ;; Should return a vector, not a lazy sequence
      (is (vector? result)))))

(deftest test-sort-by-difficulty-stable-sort
  (testing "sort-by-difficulty maintains relative order for equal difficulties"
    (let [exercises [{:name "A" :difficulty 1.0}
                     {:name "B" :difficulty 0.5}
                     {:name "C" :difficulty 1.0}
                     {:name "D" :difficulty 0.5}]
          result (session/sort-by-difficulty exercises)]
      ;; Should be sorted by difficulty
      (is (= [0.5 0.5 1.0 1.0] (mapv :difficulty result)))
      ;; Names should be present
      (is (= #{"A" "B" "C" "D"} (set (map :name result)))))))

(deftest test-sort-by-difficulty-wide-range
  (testing "sort-by-difficulty handles full difficulty range (0.5 to 2.0)"
    (let [exercises [{:name "Hardest" :difficulty 2.0}
                     {:name "Easiest" :difficulty 0.5}
                     {:name "Medium-Low" :difficulty 0.8}
                     {:name "Medium-High" :difficulty 1.5}]
          result (session/sort-by-difficulty exercises)]
      ;; Should be sorted from easiest to hardest
      (is (= ["Easiest" "Medium-Low" "Medium-High" "Hardest"] (mapv :name result)))
      (is (= [0.5 0.8 1.5 2.0] (mapv :difficulty result))))))

(deftest test-sort-by-difficulty-fractional-difficulties
  (testing "sort-by-difficulty handles fractional difficulty values"
    (let [exercises [{:name "A" :difficulty 1.23}
                     {:name "B" :difficulty 1.21}
                     {:name "C" :difficulty 1.22}]
          result (session/sort-by-difficulty exercises)]
      ;; Should be sorted by precise fractional values
      (is (= ["B" "C" "A"] (mapv :name result)))
      (is (= [1.21 1.22 1.23] (mapv :difficulty result))))))

;; ============================================================================
;; Unit Tests for arrange-progressive-difficulty Function (Task 6.2)
;; ============================================================================

(deftest test-arrange-progressive-difficulty-basic
  (testing "arrange-progressive-difficulty places easier exercises at start and end"
    (let [exercises [(session/make-session-exercise {:name "Easy-1" :difficulty 0.5} 60)
                     (session/make-session-exercise {:name "Medium" :difficulty 1.0} 60)
                     (session/make-session-exercise {:name "Hard" :difficulty 1.8} 60)
                     (session/make-session-exercise {:name "Easy-2" :difficulty 0.6} 60)]
          session-plan (session/make-session-plan exercises 240)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should have same number of exercises
      (is (= 4 (count (:exercises result))))
      ;; First exercise should be easier (lower difficulty)
      (let [difficulties (mapv #(get-in % [:exercise :difficulty]) (:exercises result))
            first-diff (first difficulties)
            last-diff (last difficulties)
            middle-diffs (subvec difficulties 1 (dec (count difficulties)))]
        ;; First and last should be lower than at least one middle exercise
        (is (< first-diff (apply max middle-diffs)))
        (is (< last-diff (apply max middle-diffs)))))))

(deftest test-arrange-progressive-difficulty-three-exercises
  (testing "arrange-progressive-difficulty with exactly 3 exercises"
    (let [exercises [(session/make-session-exercise {:name "Easy" :difficulty 0.5} 60)
                     (session/make-session-exercise {:name "Medium" :difficulty 1.0} 60)
                     (session/make-session-exercise {:name "Hard" :difficulty 1.8} 60)]
          session-plan (session/make-session-plan exercises 180)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should have 3 exercises
      (is (= 3 (count (:exercises result))))
      ;; First and last should be easier than middle
      (let [difficulties (mapv #(get-in % [:exercise :difficulty]) (:exercises result))]
        (is (< (first difficulties) (nth difficulties 1)))
        (is (< (last difficulties) (nth difficulties 1)))))))

(deftest test-arrange-progressive-difficulty-two-exercises
  (testing "arrange-progressive-difficulty with 2 exercises returns unchanged"
    (let [exercises [(session/make-session-exercise {:name "Easy" :difficulty 0.5} 60)
                     (session/make-session-exercise {:name "Hard" :difficulty 1.8} 60)]
          session-plan (session/make-session-plan exercises 120)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should return same exercises (no meaningful arrangement with < 3)
      (is (= 2 (count (:exercises result))))
      ;; Exercises should be unchanged
      (is (= exercises (:exercises result))))))

(deftest test-arrange-progressive-difficulty-single-exercise
  (testing "arrange-progressive-difficulty with 1 exercise returns unchanged"
    (let [exercises [(session/make-session-exercise {:name "Only" :difficulty 1.0} 60)]
          session-plan (session/make-session-plan exercises 60)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should return same exercise
      (is (= 1 (count (:exercises result))))
      (is (= exercises (:exercises result))))))

(deftest test-arrange-progressive-difficulty-time-conservation
  (testing "arrange-progressive-difficulty conserves total time"
    (let [exercises [(session/make-session-exercise {:name "A" :difficulty 0.5} 50)
                     (session/make-session-exercise {:name "B" :difficulty 1.0} 70)
                     (session/make-session-exercise {:name "C" :difficulty 1.5} 80)
                     (session/make-session-exercise {:name "D" :difficulty 0.8} 60)]
          session-plan (session/make-session-plan exercises 260)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Total duration should be conserved
      (is (= 260 (:total-duration-seconds result)))
      (is (= 260 (reduce + (map :duration-seconds (:exercises result)))))
      ;; Should have same exercises, just reordered
      (is (= 4 (count (:exercises result)))))))

(deftest test-arrange-progressive-difficulty-preserves-exercise-data
  (testing "arrange-progressive-difficulty preserves all exercise data"
    (let [exercises [(session/make-session-exercise {:name "Push-ups" :difficulty 1.2 :equipment ["None"]} 60)
                     (session/make-session-exercise {:name "Wall Sit" :difficulty 1.4 :equipment ["A wall"]} 70)
                     (session/make-session-exercise {:name "Jumping Jacks" :difficulty 0.8 :equipment ["None"]} 50)]
          session-plan (session/make-session-plan exercises 180)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; All exercise data should be preserved
      (let [result-exercises (:exercises result)
            result-names (set (map #(get-in % [:exercise :name]) result-exercises))
            original-names (set (map #(get-in % [:exercise :name]) exercises))]
        (is (= original-names result-names))
        ;; Check that equipment is preserved
        (doseq [ex result-exercises]
          (let [name (get-in ex [:exercise :name])
                equipment (get-in ex [:exercise :equipment])]
            (is (some? equipment))))))))

(deftest test-arrange-progressive-difficulty-five-exercises
  (testing "arrange-progressive-difficulty with 5 exercises"
    (let [exercises [(session/make-session-exercise {:name "Easy-1" :difficulty 0.5} 60)
                     (session/make-session-exercise {:name "Easy-2" :difficulty 0.7} 60)
                     (session/make-session-exercise {:name "Medium" :difficulty 1.0} 60)
                     (session/make-session-exercise {:name "Hard-1" :difficulty 1.5} 60)
                     (session/make-session-exercise {:name "Hard-2" :difficulty 1.8} 60)]
          session-plan (session/make-session-plan exercises 300)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should have 5 exercises
      (is (= 5 (count (:exercises result))))
      ;; First and last should be easier than middle exercises
      (let [difficulties (mapv #(get-in % [:exercise :difficulty]) (:exercises result))
            first-diff (first difficulties)
            last-diff (last difficulties)
            middle-diffs (subvec difficulties 1 (dec (count difficulties)))]
        ;; First and last should be lower than the hardest middle exercise
        (is (< first-diff (apply max middle-diffs)))
        (is (< last-diff (apply max middle-diffs)))))))

(deftest test-arrange-progressive-difficulty-equal-difficulties
  (testing "arrange-progressive-difficulty handles exercises with equal difficulties"
    (let [exercises [(session/make-session-exercise {:name "A" :difficulty 1.0} 60)
                     (session/make-session-exercise {:name "B" :difficulty 1.0} 60)
                     (session/make-session-exercise {:name "C" :difficulty 1.0} 60)]
          session-plan (session/make-session-plan exercises 180)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should have 3 exercises
      (is (= 3 (count (:exercises result))))
      ;; All difficulties should still be 1.0
      (is (every? #(= 1.0 (get-in % [:exercise :difficulty])) (:exercises result)))
      ;; Total time should be conserved
      (is (= 180 (reduce + (map :duration-seconds (:exercises result))))))))

(deftest test-arrange-progressive-difficulty-preserves-durations
  (testing "arrange-progressive-difficulty preserves individual exercise durations"
    (let [exercises [(session/make-session-exercise {:name "A" :difficulty 0.5} 50)
                     (session/make-session-exercise {:name "B" :difficulty 1.0} 70)
                     (session/make-session-exercise {:name "C" :difficulty 1.5} 80)]
          session-plan (session/make-session-plan exercises 200)
          result (session/arrange-progressive-difficulty session-plan)
          original-durations (set (map :duration-seconds exercises))
          result-durations (set (map :duration-seconds (:exercises result)))]
      ;; All original durations should be present in result
      (is (= original-durations result-durations)))))

(deftest test-arrange-progressive-difficulty-returns-session-plan
  (testing "arrange-progressive-difficulty returns a valid session plan"
    (let [exercises [(session/make-session-exercise {:name "A" :difficulty 0.5} 60)
                     (session/make-session-exercise {:name "B" :difficulty 1.0} 60)
                     (session/make-session-exercise {:name "C" :difficulty 1.5} 60)]
          session-plan (session/make-session-plan exercises 180)
          result (session/arrange-progressive-difficulty session-plan)]
      ;; Should be a map with required keys
      (is (map? result))
      (is (contains? result :exercises))
      (is (contains? result :total-duration-seconds))
      ;; Exercises should be a vector
      (is (vector? (:exercises result))))))
