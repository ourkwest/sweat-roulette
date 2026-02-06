(ns exercise-timer.session
  "Session Generator - generates exercise sequences with weighted time distribution")

;; ============================================================================
;; Session Data Structures
;; ============================================================================

(defn make-session-config
  "Create a session configuration structure.
   
   Parameters:
   - duration-minutes: positive integer representing total session duration in minutes
   
   Returns:
   - {:duration-minutes int}
   
   Validates: Requirements 1.1, 1.2, 1.3"
  [duration-minutes]
  {:pre [(pos-int? duration-minutes)]}
  {:duration-minutes duration-minutes})

(defn config->seconds
  "Convert session configuration duration from minutes to seconds.
   
   Parameters:
   - config: session configuration map with :duration-minutes
   
   Returns:
   - integer representing duration in seconds
   
   Validates: Requirements 1.2"
  [config]
  {:pre [(map? config)
         (contains? config :duration-minutes)
         (pos-int? (:duration-minutes config))]}
  (* (:duration-minutes config) 60))

(defn make-session-exercise
  "Create a session exercise entry with exercise data and calculated duration.
   
   Parameters:
   - exercise: exercise map with :name and :weight
   - duration-seconds: positive integer representing time allocated for this exercise
   
   Returns:
   - {:exercise {:name string :weight number}
      :duration-seconds int}
   
   Validates: Requirements 2.4, 2.5"
  [exercise duration-seconds]
  {:pre [(map? exercise)
         (contains? exercise :name)
         (contains? exercise :weight)
         (pos-int? duration-seconds)]}
  {:exercise exercise
   :duration-seconds duration-seconds})

(defn make-session-plan
  "Create a complete session plan structure.
   
   Parameters:
   - exercises: vector of session exercise entries (from make-session-exercise)
   - total-duration-seconds: total session duration in seconds
   
   Returns:
   - {:exercises [{:exercise {...} :duration-seconds int}]
      :total-duration-seconds int}
   
   Invariants:
   - exercises.length >= 1
   - sum(exercises.map(e => e.duration-seconds)) === total-duration-seconds
   
   Validates: Requirements 1.2, 1.3, 2.1, 2.5"
  [exercises total-duration-seconds]
  {:pre [(vector? exercises)
         (seq exercises)  ; at least one exercise
         (pos-int? total-duration-seconds)]}
  {:exercises exercises
   :total-duration-seconds total-duration-seconds})

;; ============================================================================
;; Default Configuration
;; ============================================================================

(def default-session-duration-minutes
  "Default session duration in minutes.
   Validates: Requirements 1.4"
  5)

;; ============================================================================
;; Weighted Time Distribution Algorithm
;; ============================================================================

(defn calculate-weight-sum
  "Calculate the sum of all exercise weights.
   
   Parameters:
   - exercises: vector of exercise maps with :weight
   
   Returns:
   - number representing sum of all weights
   
   Validates: Requirements 2.4"
  [exercises]
  {:pre [(vector? exercises)
         (seq exercises)
         (every? #(contains? % :weight) exercises)]}
  (reduce + (map :weight exercises)))

(defn calculate-base-time
  "Calculate base time per unit weight.
   
   Base time is the total duration divided by the sum of all weights.
   This gives us the time allocation for a weight of 1.0.
   
   Parameters:
   - total-duration-seconds: total session duration in seconds
   - weight-sum: sum of all exercise weights
   
   Returns:
   - number representing base time per unit weight (may be fractional)
   
   Validates: Requirements 2.4"
  [total-duration-seconds weight-sum]
  {:pre [(pos-int? total-duration-seconds)
         (pos? weight-sum)]}
  (/ total-duration-seconds weight-sum))

(defn calculate-exercise-duration
  "Calculate duration for a single exercise based on its weight.
   
   Duration = base-time * exercise-weight (rounded down to integer seconds)
   
   Parameters:
   - base-time: base time per unit weight
   - exercise-weight: weight multiplier for this exercise
   
   Returns:
   - integer representing duration in seconds (floored)
   
   Validates: Requirements 2.4"
  [base-time exercise-weight]
  {:pre [(number? base-time)
         (pos? base-time)
         (number? exercise-weight)
         (pos? exercise-weight)]}
  (int (Math/floor (* base-time exercise-weight))))

(defn distribute-remaining-seconds
  "Distribute remaining seconds due to rounding across exercises.
   
   After calculating integer durations, there may be remaining seconds
   due to rounding. This function distributes them proportionally based
   on the fractional parts that were lost during rounding.
   
   Strategy: Give one extra second to exercises with the largest fractional
   remainders until all remaining seconds are distributed.
   
   Parameters:
   - exercises: vector of exercise maps with :weight
   - base-time: base time per unit weight
   - initial-durations: vector of initially calculated integer durations
   - remaining-seconds: number of seconds to distribute
   
   Returns:
   - vector of final integer durations with remaining seconds distributed
   
   Validates: Requirements 2.5"
  [exercises base-time initial-durations remaining-seconds]
  {:pre [(vector? exercises)
         (vector? initial-durations)
         (= (count exercises) (count initial-durations))
         (>= remaining-seconds 0)]}
  (if (zero? remaining-seconds)
    initial-durations
    (let [;; Calculate fractional remainders for each exercise
          fractional-parts (mapv (fn [exercise duration]
                                   (let [exact-duration (* base-time (:weight exercise))
                                         fractional (- exact-duration duration)]
                                     fractional))
                                 exercises
                                 initial-durations)
          ;; Create indexed pairs of [index fractional-part]
          indexed-fractions (map-indexed vector fractional-parts)
          ;; Sort by fractional part (descending) to prioritize largest remainders
          sorted-indices (map first (sort-by second > indexed-fractions))
          ;; Take the top N indices where N = remaining-seconds
          indices-to-increment (take remaining-seconds sorted-indices)
          ;; Create a set for O(1) lookup
          increment-set (set indices-to-increment)]
      ;; Add 1 second to exercises at the selected indices
      (mapv (fn [idx duration]
              (if (contains? increment-set idx)
                (inc duration)
                duration))
            (range (count initial-durations))
            initial-durations))))

(defn distribute-time-weighted
  "Distribute total session time across exercises based on their weights.
   
   Algorithm:
   1. Calculate sum of all weights
   2. Calculate base time = total-duration / weight-sum
   3. For each exercise, calculate duration = floor(base-time * weight)
   4. Calculate remaining seconds = total-duration - sum(calculated-durations)
   5. Distribute remaining seconds to exercises with largest fractional remainders
   
   Parameters:
   - exercises: vector of exercise maps with :name and :weight
   - total-duration-seconds: total session duration in seconds
   
   Returns:
   - vector of session exercise entries with calculated durations
   
   Invariant: sum of all durations equals total-duration-seconds
   
   Validates: Requirements 2.4, 2.5"
  [exercises total-duration-seconds]
  {:pre [(vector? exercises)
         (seq exercises)
         (every? #(and (contains? % :name) (contains? % :weight)) exercises)
         (pos-int? total-duration-seconds)]}
  (let [;; Step 1: Calculate sum of weights
        weight-sum (calculate-weight-sum exercises)
        ;; Step 2: Calculate base time per unit weight
        base-time (calculate-base-time total-duration-seconds weight-sum)
        ;; Step 3: Calculate initial durations (floored to integers)
        initial-durations (mapv #(calculate-exercise-duration base-time (:weight %))
                                exercises)
        ;; Step 4: Calculate remaining seconds due to rounding
        allocated-seconds (reduce + initial-durations)
        remaining-seconds (- total-duration-seconds allocated-seconds)
        ;; Step 5: Distribute remaining seconds
        final-durations (distribute-remaining-seconds exercises
                                                      base-time
                                                      initial-durations
                                                      remaining-seconds)]
    ;; Create session exercise entries
    (mapv make-session-exercise exercises final-durations)))

;; ============================================================================
;; Exercise Selection with No-Repeat Logic
;; ============================================================================

(defn select-exercises-round-robin
  "Select exercises from library using round-robin without repetition.
   
   This function ensures that no exercise is repeated until all exercises
   from the library have been used. It cycles through the entire library
   before repeating any exercise.
   
   Algorithm:
   1. If num-exercises <= library size, return first N exercises from shuffled library
   2. If num-exercises > library size, cycle through library multiple times
   3. Shuffle library at the start of each cycle to add variety
   4. Ensure no consecutive duplicates when cycling
   
   Parameters:
   - library: vector of all available exercises
   - num-exercises: number of exercises to select
   
   Returns:
   - vector of selected exercises (may contain duplicates if num-exercises > library size,
     but only after all library exercises have been used)
   
   Invariants:
   - All selected exercises exist in the library
   - No exercise repeats until all library exercises have been used
   - No consecutive duplicate exercises
   
   Validates: Requirements 2.2, 2.3"
  [library num-exercises]
  {:pre [(vector? library)
         (seq library)
         (pos-int? num-exercises)]}
  (let [library-size (count library)]
    (if (<= num-exercises library-size)
      ;; Simple case: select first N exercises from shuffled library
      (vec (take num-exercises (shuffle library)))
      ;; Complex case: need to cycle through library multiple times
      (loop [result []
             remaining num-exercises
             last-name nil]
        (if (zero? remaining)
          result
          ;; Shuffle library for this cycle
          (let [shuffled (shuffle library)
                ;; If last exercise in result matches first in shuffled, rotate shuffled
                adjusted (if (and last-name (= last-name (:name (first shuffled))))
                           ;; Find first exercise with different name
                           (if-let [diff-idx (first (keep-indexed
                                                     (fn [idx ex]
                                                       (when (not= (:name ex) last-name)
                                                         idx))
                                                     shuffled))]
                             ;; Rotate to put different exercise first
                             (vec (concat (drop diff-idx shuffled)
                                          (take diff-idx shuffled)))
                             ;; All exercises have same name (shouldn't happen), use as-is
                             shuffled)
                           shuffled)
                ;; Take as many as we need from this cycle
                to-take (min remaining library-size)
                selected (take to-take adjusted)]
            (recur (vec (concat result selected))
                   (- remaining to-take)
                   (:name (last selected)))))))))

;; ============================================================================
;; Session Generation
;; ============================================================================

(defn generate-session
  "Generate a complete session plan from configuration and exercise library.
   
   This is the main entry point for session generation. It integrates:
   1. Session configuration (duration in minutes)
   2. Exercise selection (round-robin without repetition)
   3. Weighted time distribution (proportional to exercise weights)
   
   Algorithm:
   1. Convert duration from minutes to seconds
   2. Determine number of exercises (currently uses all library exercises once)
   3. Select exercises using round-robin without repetition
   4. Distribute time across exercises based on weights
   5. Return complete session plan
   
   Parameters:
   - config: session configuration map {:duration-minutes int}
   - exercises: vector of exercise maps from library [{:name string :weight number}]
   
   Returns:
   - session plan map:
     {:exercises [{:exercise {:name string :weight number}
                   :duration-seconds int}]
      :total-duration-seconds int}
   
   Invariants:
   - exercises.length >= 1
   - sum(exercises.map(e => e.duration-seconds)) === total-duration-seconds
   - All exercises come from the provided library
   - No exercise repeats until all library exercises have been used
   
   Validates: Requirements 1.2, 2.1, 2.2, 2.3, 2.4, 2.5"
  [config exercises]
  {:pre [(map? config)
         (contains? config :duration-minutes)
         (pos-int? (:duration-minutes config))
         (vector? exercises)
         (seq exercises)
         (every? #(and (contains? % :name) (contains? % :weight)) exercises)]}
  (let [;; Step 1: Convert minutes to seconds
        total-duration-seconds (config->seconds config)
        
        ;; Step 2: Determine number of exercises to select
        ;; For now, we'll use all exercises from the library once
        ;; This ensures variety and uses the entire library
        num-exercises (count exercises)
        
        ;; Step 3: Select exercises using round-robin without repetition
        selected-exercises (select-exercises-round-robin exercises num-exercises)
        
        ;; Step 4: Distribute time across exercises based on weights
        exercises-with-durations (distribute-time-weighted selected-exercises total-duration-seconds)
        
        ;; Step 5: Create and return complete session plan
        session-plan (make-session-plan exercises-with-durations total-duration-seconds)]
    session-plan))