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
    (let [exercise {:name "Push-ups" :weight 1.2}
          duration 45
          session-ex (session/make-session-exercise exercise duration)]
      (is (map? session-ex))
      (is (contains? session-ex :exercise))
      (is (contains? session-ex :duration-seconds))
      (is (= exercise (:exercise session-ex)))
      (is (= 45 (:duration-seconds session-ex))))))

(deftest test-make-session-exercise-preserves-exercise-data
  (testing "make-session-exercise preserves all exercise data"
    (let [exercise {:name "Squats" :weight 1.0}
          session-ex (session/make-session-exercise exercise 60)]
      (is (= "Squats" (get-in session-ex [:exercise :name])))
      (is (= 1.0 (get-in session-ex [:exercise :weight]))))))

(deftest test-make-session-plan-valid
  (testing "make-session-plan creates valid session plan"
    (let [exercises [(session/make-session-exercise {:name "Push-ups" :weight 1.2} 150)
                     (session/make-session-exercise {:name "Squats" :weight 1.0} 150)]
          plan (session/make-session-plan exercises 300)]
      (is (map? plan))
      (is (contains? plan :exercises))
      (is (contains? plan :total-duration-seconds))
      (is (= exercises (:exercises plan)))
      (is (= 300 (:total-duration-seconds plan))))))

(deftest test-make-session-plan-single-exercise
  (testing "make-session-plan works with single exercise"
    (let [exercises [(session/make-session-exercise {:name "Plank" :weight 1.5} 300)]
          plan (session/make-session-plan exercises 300)]
      (is (= 1 (count (:exercises plan))))
      (is (= 300 (:total-duration-seconds plan))))))

(deftest test-make-session-plan-multiple-exercises
  (testing "make-session-plan works with multiple exercises"
    (let [exercises [(session/make-session-exercise {:name "Push-ups" :weight 1.2} 100)
                     (session/make-session-exercise {:name "Squats" :weight 1.0} 100)
                     (session/make-session-exercise {:name "Plank" :weight 1.5} 100)]
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
            weight (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false})]
    {:name name :weight weight}))

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

(deftest test-calculate-weight-sum-single-exercise
  (testing "calculate-weight-sum with single exercise"
    (let [exercises [{:name "Push-ups" :weight 1.5}]]
      (is (= 1.5 (session/calculate-weight-sum exercises))))))

(deftest test-calculate-weight-sum-multiple-exercises
  (testing "calculate-weight-sum with multiple exercises"
    (let [exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}
                     {:name "Plank" :weight 1.5}]]
      (is (= 3.7 (session/calculate-weight-sum exercises))))))

(deftest test-calculate-base-time
  (testing "calculate-base-time divides duration by weight sum"
    (is (= 100 (session/calculate-base-time 300 3.0)))
    (is (= 50 (session/calculate-base-time 300 6.0)))))

(deftest test-calculate-exercise-duration
  (testing "calculate-exercise-duration multiplies base time by weight"
    (is (= 120 (session/calculate-exercise-duration 100 1.2)))
    (is (= 100 (session/calculate-exercise-duration 100 1.0)))
    (is (= 150 (session/calculate-exercise-duration 100 1.5)))))

(deftest test-calculate-exercise-duration-floors-result
  (testing "calculate-exercise-duration floors fractional results"
    (is (= 81 (session/calculate-exercise-duration 100 0.81)))
    (is (= 119 (session/calculate-exercise-duration 100 1.19)))))

(deftest test-distribute-remaining-seconds-no-remainder
  (testing "distribute-remaining-seconds with no remaining seconds"
    (let [exercises [{:name "Push-ups" :weight 1.0}
                     {:name "Squats" :weight 1.0}]
          base-time 100
          initial-durations [100 100]
          result (session/distribute-remaining-seconds exercises base-time initial-durations 0)]
      (is (= [100 100] result)))))

(deftest test-distribute-remaining-seconds-with-remainder
  (testing "distribute-remaining-seconds distributes extra seconds"
    (let [exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}
                     {:name "Plank" :weight 1.5}]
          base-time 81.08108108108108  ; 300 / 3.7
          initial-durations [97 81 121]  ; floored values
          ;; Total = 299, so 1 second remaining
          result (session/distribute-remaining-seconds exercises base-time initial-durations 1)]
      ;; One exercise should get +1 second
      (is (= 300 (reduce + result)))
      (is (= 3 (count result))))))

(deftest test-distribute-time-weighted-simple-case
  (testing "distribute-time-weighted with simple equal weights"
    (let [exercises [{:name "Push-ups" :weight 1.0}
                     {:name "Squats" :weight 1.0}]
          result (session/distribute-time-weighted exercises 300)]
      (is (= 2 (count result)))
      ;; Each should get 150 seconds (300 / 2)
      (is (= 150 (get-in result [0 :duration-seconds])))
      (is (= 150 (get-in result [1 :duration-seconds])))
      ;; Total should equal 300
      (is (= 300 (reduce + (map :duration-seconds result)))))))

(deftest test-distribute-time-weighted-different-weights
  (testing "distribute-time-weighted with different weights"
    (let [exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}
                     {:name "Plank" :weight 1.5}]
          result (session/distribute-time-weighted exercises 300)]
      (is (= 3 (count result)))
      ;; Total should equal 300 (time conservation)
      (is (= 300 (reduce + (map :duration-seconds result))))
      ;; Exercises with higher weights should get more time
      (let [durations (mapv :duration-seconds result)]
        ;; Plank (1.5) should have most time
        ;; Push-ups (1.2) should have more than Squats (1.0)
        (is (> (nth durations 2) (nth durations 0)))
        (is (> (nth durations 0) (nth durations 1)))))))

(deftest test-distribute-time-weighted-single-exercise
  (testing "distribute-time-weighted with single exercise gets all time"
    (let [exercises [{:name "Plank" :weight 1.5}]
          result (session/distribute-time-weighted exercises 300)]
      (is (= 1 (count result)))
      (is (= 300 (get-in result [0 :duration-seconds]))))))

(deftest test-distribute-time-weighted-preserves-exercise-data
  (testing "distribute-time-weighted preserves exercise name and weight"
    (let [exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}]
          result (session/distribute-time-weighted exercises 300)]
      (is (= "Push-ups" (get-in result [0 :exercise :name])))
      (is (= 1.2 (get-in result [0 :exercise :weight])))
      (is (= "Squats" (get-in result [1 :exercise :name])))
      (is (= 1.0 (get-in result [1 :exercise :weight]))))))

(deftest test-distribute-time-weighted-time-conservation-property
  (testing "distribute-time-weighted always conserves total time"
    (let [test-cases [;; Various durations and exercise combinations
                      {:exercises [{:name "A" :weight 1.0}] :duration 60}
                      {:exercises [{:name "A" :weight 1.0} {:name "B" :weight 1.0}] :duration 120}
                      {:exercises [{:name "A" :weight 0.5} {:name "B" :weight 2.0}] :duration 300}
                      {:exercises [{:name "A" :weight 1.2} {:name "B" :weight 1.0} {:name "C" :weight 1.5}] :duration 300}
                      {:exercises [{:name "A" :weight 0.8} {:name "B" :weight 1.3} {:name "C" :weight 1.1} {:name "D" :weight 1.7}] :duration 600}]]
      (doseq [{:keys [exercises duration]} test-cases]
        (let [result (session/distribute-time-weighted exercises duration)
              total (reduce + (map :duration-seconds result))]
          (is (= duration total)
              (str "Failed for exercises: " exercises " with duration: " duration)))))))

;; ============================================================================
;; Property-Based Tests for Weighted Time Distribution (Task 3.3)
;; ============================================================================

;; Generator for valid exercise weight (0.5 to 2.0)
(def gen-exercise-weight
  "Generator for valid exercise weights between 0.5 and 2.0"
  (gen/double* {:min 0.5 :max 2.0 :infinite? false :NaN? false}))

;; Generator for exercise with valid weight
(def gen-weighted-exercise
  "Generator for exercise maps with valid weights"
  (gen/let [name (gen/not-empty gen/string-alphanumeric)
            weight gen-exercise-weight]
    {:name name :weight weight}))

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
    (let [result (session/distribute-time-weighted exercises total-duration)
          sum-of-durations (reduce + (map :duration-seconds result))]
      ;; The sum of all individual exercise durations should equal the total session duration
      (= total-duration sum-of-durations))))

;; ============================================================================
;; Unit Tests for Exercise Selection with No-Repeat Logic (Task 3.4)
;; ============================================================================

(deftest test-select-exercises-round-robin-simple-case
  (testing "select-exercises-round-robin with count <= library size"
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}
                   {:name "Plank" :weight 1.5}
                   {:name "Jumping Jacks" :weight 0.8}
                   {:name "Lunges" :weight 1.0}]
          result (session/select-exercises-round-robin library 3)]
      ;; Should return exactly 3 exercises
      (is (= 3 (count result)))
      ;; All selected exercises should be from the library
      (is (every? (fn [ex] (some #(= (:name %) (:name ex)) library)) result))
      ;; All selected exercises should be unique (no repeats)
      (is (= (count result) (count (set (map :name result))))))))

(deftest test-select-exercises-round-robin-exact-library-size
  (testing "select-exercises-round-robin with count = library size"
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}
                   {:name "Plank" :weight 1.5}]
          result (session/select-exercises-round-robin library 3)]
      ;; Should return all 3 exercises
      (is (= 3 (count result)))
      ;; All exercises should be unique
      (is (= 3 (count (set (map :name result))))))))

(deftest test-select-exercises-round-robin-single-exercise
  (testing "select-exercises-round-robin with single exercise requested"
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}
                   {:name "Plank" :weight 1.5}]
          result (session/select-exercises-round-robin library 1)]
      ;; Should return exactly 1 exercise
      (is (= 1 (count result)))
      ;; Exercise should be from the library
      (is (some #(= (:name %) (:name (first result))) library)))))

(deftest test-select-exercises-round-robin-cycling
  (testing "select-exercises-round-robin cycles through library when count > library size"
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}
                   {:name "Plank" :weight 1.5}]
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
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}
                   {:name "Plank" :weight 1.5}]
          ;; Test a few times due to randomization
          results (repeatedly 3 #(session/select-exercises-round-robin library 10))]
      (doseq [result results]
        ;; Check no consecutive duplicates
        (is (every? (fn [[a b]] (not= (:name a) (:name b)))
                    (partition 2 1 result))
            "Found consecutive duplicate exercises")))))

(deftest test-select-exercises-round-robin-library-membership
  (testing "select-exercises-round-robin only selects from library"
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}
                   {:name "Plank" :weight 1.5}]
          library-names (set (map :name library))
          result (session/select-exercises-round-robin library 10)]
      ;; All selected exercise names should be in the library
      (is (every? #(contains? library-names (:name %)) result)))))

(deftest test-select-exercises-round-robin-exhausts-library-before-repeat
  (testing "select-exercises-round-robin uses all library exercises before repeating"
    (let [library [{:name "A" :weight 1.0}
                   {:name "B" :weight 1.0}
                   {:name "C" :weight 1.0}]
          result (session/select-exercises-round-robin library 5)]
      ;; First 3 should be all different (all library exercises used)
      (is (= 3 (count (set (map :name (take 3 result))))))
      ;; All 5 exercises should be from library
      (is (every? (fn [ex] (some #(= (:name %) (:name ex)) library)) result)))))

(deftest test-select-exercises-round-robin-preserves-exercise-data
  (testing "select-exercises-round-robin preserves exercise name and weight"
    (let [library [{:name "Push-ups" :weight 1.2}
                   {:name "Squats" :weight 1.0}]
          result (session/select-exercises-round-robin library 3)]
      ;; Each selected exercise should have matching weight from library
      (doseq [selected result]
        (let [original (first (filter #(= (:name %) (:name selected)) library))]
          (is (some? original))
          (is (= (:weight original) (:weight selected))))))))

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
          exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}
                     {:name "Plank" :weight 1.5}]
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
          exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}]
          result (session/generate-session config exercises)]
      ;; Should use default 5 minutes = 300 seconds
      (is (= 300 (:total-duration-seconds result)))
      ;; Sum of exercise durations should equal total
      (let [sum (reduce + (map :duration-seconds (:exercises result)))]
        (is (= 300 sum))))))

(deftest test-generate-session-single-exercise
  (testing "generate-session with single exercise"
    (let [config (session/make-session-config 5)
          exercises [{:name "Plank" :weight 1.5}]
          result (session/generate-session config exercises)]
      ;; Should have exactly 1 exercise
      (is (= 1 (count (:exercises result))))
      ;; Exercise should get all the time
      (is (= 300 (get-in result [:exercises 0 :duration-seconds])))
      ;; Total should be 300 seconds
      (is (= 300 (:total-duration-seconds result))))))

(deftest test-generate-session-time-conservation
  (testing "generate-session conserves total time"
    (let [config (session/make-session-config 10)
          exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}
                     {:name "Plank" :weight 1.5}
                     {:name "Jumping Jacks" :weight 0.8}]
          result (session/generate-session config exercises)]
      ;; Total should be 10 minutes = 600 seconds
      (is (= 600 (:total-duration-seconds result)))
      ;; Sum of exercise durations should equal total
      (let [sum (reduce + (map :duration-seconds (:exercises result)))]
        (is (= 600 sum))))))

(deftest test-generate-session-specific-weight-calculations
  (testing "generate-session with known weight calculations"
    (let [config (session/make-session-config 5)
          ;; Use weights that sum to 3.0 for easy calculation
          ;; 300 seconds / 3.0 = 100 seconds base time
          exercises [{:name "A" :weight 1.0}  ; should get ~100 seconds
                     {:name "B" :weight 1.0}  ; should get ~100 seconds
                     {:name "C" :weight 1.0}] ; should get ~100 seconds
          result (session/generate-session config exercises)]
      ;; Each exercise should get approximately 100 seconds
      (let [durations (map :duration-seconds (:exercises result))]
        ;; All should be close to 100 (within rounding)
        (is (every? #(and (>= % 99) (<= % 101)) durations))
        ;; Total should be exactly 300
        (is (= 300 (reduce + durations)))))))

(deftest test-generate-session-preserves-exercise-data
  (testing "generate-session preserves exercise names and weights"
    (let [config (session/make-session-config 5)
          exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}]
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
          exercises [{:name "Push-ups" :weight 1.2}
                     {:name "Squats" :weight 1.0}
                     {:name "Plank" :weight 1.5}]
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
          exercises [{:name "Push-ups" :weight 1.0}]]
      (doseq [{:keys [minutes expected-seconds]} test-cases]
        (let [config (session/make-session-config minutes)
              result (session/generate-session config exercises)]
          (is (= expected-seconds (:total-duration-seconds result))
              (str "Failed for " minutes " minutes")))))))
