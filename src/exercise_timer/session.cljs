(ns exercise-timer.session
  "Session Generator - generates exercise sequences with difficulty-based time distribution")

;; ============================================================================
;; Session Data Structures
;; ============================================================================

(defn make-session-config
  "Create a session configuration structure.
   
   Parameters:
   - duration-minutes: positive integer representing total session duration in minutes
   - equipment (optional): set of equipment type strings that are available
   - excluded-tags (optional): set of tag strings to exclude from session
   
   Returns:
   - {:duration-minutes int :equipment #{string} :excluded-tags #{string}}
   
   Validates: Requirements 1.1, 1.2, 1.3, 13.2, 13.4"
  ([duration-minutes]
   (make-session-config duration-minutes #{} #{}))
  ([duration-minutes equipment]
   (make-session-config duration-minutes equipment #{}))
  ([duration-minutes equipment excluded-tags]
   {:pre [(pos-int? duration-minutes)
          (set? equipment)
          (set? excluded-tags)]}
   {:duration-minutes duration-minutes
    :equipment equipment
    :excluded-tags excluded-tags}))

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
   - exercise: exercise map with :name and :difficulty
   - duration-seconds: positive integer representing time allocated for this exercise
   
   Returns:
   - {:exercise {:name string :difficulty number}
      :duration-seconds int}
   
   Validates: Requirements 2.4, 2.5"
  [exercise duration-seconds]
  {:pre [(map? exercise)
         (contains? exercise :name)
         (contains? exercise :difficulty)
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
;; Difficulty-Based Time Distribution Algorithm
;; ============================================================================

(defn calculate-difficulty-sum
  "Calculate the sum of inverse exercise difficulties (1/difficulty).
   
   Parameters:
   - exercises: vector of exercise maps with :difficulty
   
   Returns:
   - number representing sum of all inverse difficulties
   
   Validates: Requirements 2.4"
  [exercises]
  {:pre [(vector? exercises)
         (seq exercises)
         (every? #(contains? % :difficulty) exercises)]}
  (reduce + (map #(/ 1.0 (:difficulty %)) exercises)))

(defn calculate-base-time
  "Calculate base time per unit inverse difficulty.
   
   Base time is the total duration divided by the sum of inverse difficulties.
   With inverted difficulties: higher difficulty = harder = less time.
   
   Parameters:
   - total-duration-seconds: total session duration in seconds
   - inverse-difficulty-sum: sum of all inverse exercise difficulties (1/difficulty)
   
   Returns:
   - number representing base time per unit difficulty (may be fractional)
   
   Validates: Requirements 2.4"
  [total-duration-seconds inverse-difficulty-sum]
  {:pre [(pos-int? total-duration-seconds)
         (pos? inverse-difficulty-sum)]}
  (/ total-duration-seconds inverse-difficulty-sum))

(defn calculate-exercise-duration
  "Calculate duration for a single exercise based on its difficulty.
   
   Duration = base-time / exercise-difficulty (rounded down to integer seconds)
   Higher difficulty = harder exercise = less time
   Lower difficulty = easier exercise = more time
   
   Parameters:
   - base-time: base time per unit difficulty
   - exercise-difficulty: difficulty multiplier for this exercise
   
   Returns:
   - integer representing duration in seconds (floored)
   
   Validates: Requirements 2.4"
  [base-time exercise-difficulty]
  {:pre [(number? base-time)
         (pos? base-time)
         (number? exercise-difficulty)
         (pos? exercise-difficulty)]}
  (int (Math/floor (/ base-time exercise-difficulty))))

(defn distribute-remaining-seconds
  "Distribute remaining seconds due to rounding across exercises.
   
   After calculating integer durations, there may be remaining seconds
   due to rounding. This function distributes them proportionally based
   on the fractional parts that were lost during rounding.
   
   Strategy: Give one extra second to exercises with the largest fractional
   remainders until all remaining seconds are distributed.
   
   Parameters:
   - exercises: vector of exercise maps with :difficulty
   - base-time: base time per unit difficulty
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
                                   (let [exact-duration (/ base-time (:difficulty exercise))
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

(defn distribute-time-by-difficulty
  "Distribute total session time across exercises based on inverse of their difficulties.
   
   Higher difficulty = harder exercise = less time
   Lower difficulty = easier exercise = more time
   
   Algorithm:
   1. Calculate sum of inverse difficulties (1/difficulty for each exercise)
   2. Calculate base time = total-duration / inverse-difficulty-sum
   3. For each exercise, calculate duration = floor(base-time / difficulty)
   4. Calculate remaining seconds = total-duration - sum(calculated-durations)
   5. Distribute remaining seconds to exercises with largest fractional remainders
   
   Parameters:
   - exercises: vector of exercise maps with :name and :difficulty
   - total-duration-seconds: total session duration in seconds
   
   Returns:
   - vector of session exercise entries with calculated durations
   
   Invariant: sum of all durations equals total-duration-seconds
   
   Validates: Requirements 2.4, 2.5"
  [exercises total-duration-seconds]
  {:pre [(vector? exercises)
         (seq exercises)
         (every? #(and (contains? % :name) (contains? % :difficulty)) exercises)
         (pos-int? total-duration-seconds)]}
  (let [;; Step 1: Calculate sum of inverse difficulties
        inverse-difficulty-sum (calculate-difficulty-sum exercises)
        ;; Step 2: Calculate base time per unit inverse difficulty
        base-time (calculate-base-time total-duration-seconds inverse-difficulty-sum)
        ;; Step 3: Calculate initial durations (floored to integers)
        initial-durations (mapv #(calculate-exercise-duration base-time (:difficulty %))
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
;; Time Constraints
;; ============================================================================

(def min-exercise-duration-seconds
  "Minimum allowed duration for a single exercise in seconds.
   Validates: Requirements 2.6"
  20)

(def max-exercise-duration-seconds
  "Maximum allowed duration for a single exercise in seconds (2 minutes).
   Validates: Requirements 2.7"
  120)

(defn apply-time-constraints
  "Ensure exercise duration is between minimum and maximum bounds.
   
   Enforces:
   - Minimum: 20 seconds per exercise
   - Maximum: 120 seconds (2 minutes) per exercise
   
   Parameters:
   - exercise-duration: duration in seconds to constrain
   
   Returns:
   - integer representing constrained duration in seconds
   
   Validates: Requirements 2.6, 2.7"
  [exercise-duration]
  {:pre [(number? exercise-duration)
         (pos? exercise-duration)]}
  (-> exercise-duration
      (max min-exercise-duration-seconds)
      (min max-exercise-duration-seconds)
      int))

;; ============================================================================
;; Exercise Splitting for Long Durations
;; ============================================================================

(defn split-long-exercises
  "Split exercises exceeding maximum duration into multiple occurrences.
   
   When an exercise would naturally receive more than 120 seconds (2 minutes),
   it is split into multiple occurrences, each with a duration at or below the
   maximum. The split occurrences are distributed throughout the session.
   
   Algorithm:
   1. Identify exercises exceeding max duration
   2. For each long exercise, calculate how many splits are needed
   3. Split the exercise into multiple occurrences with durations <= max
   4. Distribute split occurrences evenly throughout the session
   5. Maintain time conservation (total duration unchanged)
   
   Parameters:
   - session-exercises: vector of session exercise entries with durations
   
   Returns:
   - vector of session exercise entries with long exercises split and distributed
   
   Invariants:
   - All exercise durations <= max-exercise-duration-seconds
   - Total duration is conserved (sum of durations unchanged)
   - Split exercises are distributed throughout the session
   
   Validates: Requirements 2.8"
  [session-exercises]
  {:pre [(vector? session-exercises)
         (seq session-exercises)
         (every? #(and (contains? % :exercise) (contains? % :duration-seconds)) session-exercises)]}
  (let [;; Separate exercises into those that need splitting and those that don't
        {needs-split true, no-split false}
        (group-by #(> (:duration-seconds %) max-exercise-duration-seconds) session-exercises)]
    
    (if (empty? needs-split)
      ;; No exercises need splitting, return as-is
      session-exercises
      
      ;; Split long exercises and distribute them
      (let [;; Process each long exercise into multiple occurrences
            split-exercises
            (mapcat (fn [{:keys [exercise duration-seconds]}]
                      (let [;; Calculate number of splits needed
                            num-splits (int (Math/ceil (/ duration-seconds max-exercise-duration-seconds)))
                            ;; Calculate base duration for each split
                            base-duration (int (Math/floor (/ duration-seconds num-splits)))
                            ;; Calculate remaining seconds to distribute
                            remaining (- duration-seconds (* base-duration num-splits))]
                        ;; Create split occurrences
                        (mapv (fn [idx]
                                ;; Give extra second to first 'remaining' splits
                                (let [split-duration (if (< idx remaining)
                                                       (inc base-duration)
                                                       base-duration)]
                                  (make-session-exercise exercise split-duration)))
                              (range num-splits))))
                    needs-split)
            
            ;; Combine non-split and split exercises
            all-exercises (vec (concat (or no-split []) split-exercises))
            
            ;; Distribute exercises evenly throughout session
            ;; Strategy: interleave split occurrences with other exercises
            ;; to avoid clustering split exercises together
            total-count (count all-exercises)
            non-split-count (count (or no-split []))
            split-count (count split-exercises)
            
            ;; If we have both split and non-split exercises, interleave them
            ;; Otherwise, just return all exercises
            distributed
            (if (and (pos? non-split-count) (pos? split-count))
              ;; Calculate step size for distributing split exercises
              (let [step (if (> split-count 1)
                           (/ (double total-count) split-count)
                           (double total-count))
                    ;; Create target positions for split exercises
                    target-positions (mapv #(int (Math/floor (* % step))) (range split-count))
                    ;; Create result vector with nil placeholders
                    result (vec (repeat total-count nil))
                    ;; Place split exercises at target positions
                    result-with-splits (reduce (fn [acc [idx ex]]
                                                 (assoc acc (nth target-positions idx) ex))
                                               result
                                               (map-indexed vector split-exercises))
                    ;; Fill remaining positions with non-split exercises
                    final-result (reduce (fn [acc ex]
                                          (let [first-nil-idx (first (keep-indexed
                                                                      (fn [idx val]
                                                                        (when (nil? val) idx))
                                                                      acc))]
                                            (if first-nil-idx
                                              (assoc acc first-nil-idx ex)
                                              acc)))
                                        result-with-splits
                                        (or no-split []))]
                final-result)
              ;; Only one type of exercise, return as-is
              all-exercises)]
        
        distributed))))

;; ============================================================================
;; Progressive Difficulty Arrangement
;; ============================================================================

(defn sort-by-difficulty
  "Sort exercises by difficulty value in ascending order.
   
   Lower difficulty exercises (easier) come first, higher difficulty
   exercises (harder) come last. This is used to arrange exercises
   for progressive difficulty in sessions.
   
   Parameters:
   - exercises: vector of exercise maps with :difficulty
   
   Returns:
   - vector of exercises sorted by difficulty (ascending)
   
   Validates: Requirements 2.9"
  [exercises]
  {:pre [(vector? exercises)
         (every? #(contains? % :difficulty) exercises)]}
  (vec (sort-by :difficulty exercises)))

(defn arrange-progressive-difficulty
  "Rearrange exercises so easier ones are at start and end, harder ones in middle.
   
   This creates a progressive difficulty curve where the session starts easy,
   builds up to harder exercises in the middle, and ends with easier exercises
   for a cool-down effect.
   
   Algorithm:
   1. Sort exercises by difficulty (ascending)
   2. If 3 or more exercises:
      - Place exercises alternating between start and end positions
      - Lower difficulty exercises go to start and end
      - Higher difficulty exercises end up in the middle
   3. If fewer than 3 exercises, return as-is (no meaningful arrangement)
   
   Parameters:
   - session-plan: session plan map with :exercises vector
   
   Returns:
   - session plan map with exercises rearranged for progressive difficulty
   
   Invariants:
   - Total duration is conserved (same exercises, just reordered)
   - All exercise data is preserved
   - First and last exercises have lower difficulty than middle exercises (when 3+ exercises)
   
   Validates: Requirements 2.9"
  [session-plan]
  {:pre [(map? session-plan)
         (contains? session-plan :exercises)
         (vector? (:exercises session-plan))]}
  (let [exercises (:exercises session-plan)
        num-exercises (count exercises)]
    (if (< num-exercises 3)
      ;; With fewer than 3 exercises, no meaningful progressive arrangement
      session-plan
      ;; Sort exercises by difficulty (ascending: easier first)
      (let [sorted-exercises (vec (sort-by #(get-in % [:exercise :difficulty]) exercises))
            ;; Build result by alternating placement: start, end, start, end, ...
            ;; This puts easier exercises at start/end and harder ones in middle
            result (loop [remaining sorted-exercises
                          front []
                          back []
                          place-at-front true]
                     (if (empty? remaining)
                       ;; Combine front and reversed back
                       (vec (concat front (reverse back)))
                       ;; Take next exercise and place it
                       (let [next-ex (first remaining)
                             rest-ex (vec (rest remaining))]
                         (if place-at-front
                           ;; Place at front
                           (recur rest-ex (conj front next-ex) back false)
                           ;; Place at back
                           (recur rest-ex front (conj back next-ex) true)))))]
        ;; Return session plan with rearranged exercises
        (assoc session-plan :exercises result)))))

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
;; Tag Variety Distribution
;; ============================================================================

(defn- muscle-tags
  "Get the set of muscle-related tags from an exercise, excluding type/impact tags.
   
   Muscle tags are tags that represent body parts or muscle groups.
   Type tags (cardio, strength, etc.) and impact tags are excluded."
  [exercise]
  (let [all-tags (set (:tags (:exercise exercise) []))
        non-muscle-tags #{"cardio" "strength" "flexibility" "balance" "plyometric" 
                          "low-impact" "high-impact" "full-body"}]
    (clojure.set/difference all-tags non-muscle-tags)))

(defn- tag-overlap-score
  "Calculate how many muscle tags two exercises share.
   Higher score = more overlap = less variety."
  [ex1 ex2]
  (let [tags1 (muscle-tags ex1)
        tags2 (muscle-tags ex2)]
    (count (clojure.set/intersection tags1 tags2))))

(defn distribute-for-tag-variety
  "Rearrange exercises to maximize tag variety (avoid consecutive exercises with same muscle groups).
   
   Uses a greedy algorithm to select the next exercise that has minimal tag overlap
   with the previous exercise. This ensures variety in muscle groups throughout the session.
   
   Parameters:
   - exercises: vector of exercise maps with :exercise {:tags [...]} structure
   
   Returns:
   - vector of exercises rearranged for tag variety
   
   Note: This is applied BEFORE progressive difficulty arrangement, so difficulty
   distribution is preserved."
  [exercises]
  (if (< (count exercises) 2)
    exercises
    (loop [remaining (vec (rest exercises))
           result [(first exercises)]]
      (if (empty? remaining)
        result
        (let [last-ex (last result)
              ;; Find exercise with minimal tag overlap with last exercise
              next-ex (apply min-key
                            #(tag-overlap-score last-ex %)
                            remaining)
              ;; Remove selected exercise from remaining
              new-remaining (filterv #(not= % next-ex) remaining)]
          (recur new-remaining (conj result next-ex)))))))

;; ============================================================================
;; Sided Exercise Time Adjustment
;; ============================================================================

(defn apply-sided-multiplier
  "Apply 1.5x time multiplier to sided exercises.
   
   Sided exercises (e.g., single-leg exercises, side planks) need to be performed
   on both sides, so they get 50% more time with a 'Switch sides' announcement
   at the halfway point.
   
   Parameters:
   - exercises-with-durations: vector of session exercise entries
   
   Returns:
   - vector of session exercise entries with adjusted durations for sided exercises
   
   Note: This increases total session duration. The caller should handle any
   necessary time redistribution if maintaining exact duration is required."
  [exercises-with-durations]
  (mapv (fn [session-ex]
          (let [exercise (:exercise session-ex)
                sided? (:sided exercise false)
                current-duration (:duration-seconds session-ex)]
            (if sided?
              (assoc session-ex :duration-seconds (int (* current-duration 1.5)))
              session-ex)))
        exercises-with-durations))

;; ============================================================================
;; Session Generation
;; ============================================================================

(defn generate-session
  "Generate a complete session plan from configuration and exercise library.
   
   This is the main entry point for session generation. It integrates:
   1. Session configuration (duration in minutes, equipment selection)
   2. Equipment filtering (only include exercises with selected equipment)
   3. Exercise selection (round-robin without repetition)
   4. Difficulty-based time distribution (proportional to exercise difficulties)
   5. Time constraints (20s min, 120s max per exercise)
   6. Exercise splitting (split exercises exceeding 2 minutes)
   7. Progressive difficulty arrangement (easier exercises at start and end)
   
   Algorithm:
   1. Convert duration from minutes to seconds
   2. Filter exercises by selected equipment
   3. Determine number of exercises (currently uses all filtered exercises once)
   4. Select exercises using round-robin without repetition
   5. Distribute time across exercises based on difficulties
   6. Split exercises exceeding 2 minutes into multiple occurrences
   7. Arrange exercises for progressive difficulty (easier at start/end)
   8. Return complete session plan
   
   Note: Time constraints are enforced through the splitting process. Exercises
   exceeding 120 seconds are split into multiple occurrences. The minimum constraint
   of 20 seconds is a natural result of the time distribution algorithm for reasonable
   session durations and library sizes.
   
   Parameters:
   - config: session configuration map {:duration-minutes int :equipment #{string}}
   - exercises: vector of exercise maps from library [{:name string :difficulty number :equipment [string]}]
   
   Returns:
   - session plan map:
     {:exercises [{:exercise {:name string :difficulty number :equipment [string]}
                   :duration-seconds int}]
      :total-duration-seconds int}
   
   Invariants:
   - exercises.length >= 1
   - sum(exercises.map(e => e.duration-seconds)) === total-duration-seconds
   - All exercises come from the provided library
   - All exercises match selected equipment requirements
   - All exercise durations are at most 120 seconds (enforced by splitting)
   - First and last exercises have lower difficulty than middle exercises (when 3+ exercises)
   - No exercise repeats until all library exercises have been used
   
   Validates: Requirements 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 13.3"
  [config exercises]
  {:pre [(map? config)
         (contains? config :duration-minutes)
         (pos-int? (:duration-minutes config))
         (vector? exercises)
         (seq exercises)
         (every? #(and (contains? % :name) (contains? % :difficulty)) exercises)]}
  (let [;; Step 1: Convert minutes to seconds
        total-duration-seconds (config->seconds config)
        
        ;; Step 2: Filter exercises by selected equipment
        equipment-set (get config :equipment #{})
        filtered-exercises (if (empty? equipment-set)
                             ;; If no equipment selected, use all exercises
                             exercises
                             ;; Otherwise, filter by equipment
                             (filterv
                              (fn [exercise]
                                (let [required-equipment (:equipment exercise [])]
                                  ;; Exercise is included if all required equipment is available
                                  ;; Empty equipment means no equipment needed
                                  (every? (fn [eq]
                                            (contains? equipment-set eq))
                                          required-equipment)))
                              exercises))
        
        ;; Step 2.5: Filter exercises by excluded tags
        excluded-tags (get config :excluded-tags #{})
        tag-filtered-exercises (if (empty? excluded-tags)
                                 filtered-exercises
                                 (filterv
                                  (fn [exercise]
                                    (let [exercise-tags (set (:tags exercise []))]
                                      ;; Exercise is included if it has NO tags in the excluded set
                                      (empty? (clojure.set/intersection exercise-tags excluded-tags))))
                                  filtered-exercises))
        
        ;; Step 3: Determine number of exercises to select
        ;; Aim for approximately 40 seconds per exercise for better workout pacing
        ;; Use at least 3 exercises for variety, but cap at available exercises
        target-avg-duration 40
        ideal-num-exercises (max 3 (int (/ total-duration-seconds target-avg-duration)))
        num-exercises (min ideal-num-exercises (count tag-filtered-exercises))
        
        ;; Step 4: Select exercises using round-robin without repetition
        selected-exercises (select-exercises-round-robin tag-filtered-exercises num-exercises)
        
        ;; Step 5: Distribute time across exercises based on difficulties
        exercises-with-durations (distribute-time-by-difficulty selected-exercises total-duration-seconds)
        
        ;; Step 5.25: Apply 1.5x multiplier to sided exercises
        sided-adjusted-exercises (apply-sided-multiplier exercises-with-durations)
        
        ;; Step 5.5: Apply minimum constraint and redistribute excess time
        ;; If any exercise is below 20s, bring it up to 20s and redistribute the added time
        ;; by reducing other exercises proportionally
        constrained-exercises (let [below-min (filter #(< (:duration-seconds %) min-exercise-duration-seconds) sided-adjusted-exercises)]
                                (if (empty? below-min)
                                  sided-adjusted-exercises
                                  (let [;; Calculate how much time we need to add
                                        time-deficit (reduce + (map #(- min-exercise-duration-seconds (:duration-seconds %)) below-min))
                                        ;; Bring all below-min exercises up to minimum
                                        with-mins (mapv (fn [ex]
                                                          (if (< (:duration-seconds ex) min-exercise-duration-seconds)
                                                            (assoc ex :duration-seconds min-exercise-duration-seconds)
                                                            ex))
                                                        sided-adjusted-exercises)
                                        ;; Find exercises that can be reduced (above minimum)
                                        reducible (filter #(> (:duration-seconds %) min-exercise-duration-seconds) with-mins)
                                        total-reducible-time (reduce + (map #(- (:duration-seconds %) min-exercise-duration-seconds) reducible))]
                                    (if (or (empty? reducible) (zero? total-reducible-time))
                                      ;; Can't redistribute, just return with minimums applied
                                      with-mins
                                      ;; Redistribute the deficit proportionally from reducible exercises
                                      (mapv (fn [ex]
                                              (if (> (:duration-seconds ex) min-exercise-duration-seconds)
                                                (let [reducible-amount (- (:duration-seconds ex) min-exercise-duration-seconds)
                                                      proportion (/ reducible-amount total-reducible-time)
                                                      reduction (* time-deficit proportion)
                                                      new-duration (max min-exercise-duration-seconds
                                                                       (int (- (:duration-seconds ex) reduction)))]
                                                  (assoc ex :duration-seconds new-duration))
                                                ex))
                                            with-mins)))))
        
        ;; Step 6: Split exercises exceeding 2 minutes into multiple occurrences
        ;; This enforces the maximum duration constraint of 120 seconds
        split-exercises (split-long-exercises constrained-exercises)
        
        ;; Step 7: Distribute exercises for tag variety (avoid consecutive same muscle groups)
        variety-distributed (distribute-for-tag-variety split-exercises)
        
        ;; Step 8: Calculate final total duration (should equal original due to time conservation in splitting)
        final-total (reduce + (map :duration-seconds variety-distributed))
        
        ;; Step 9: Create session plan (before progressive difficulty arrangement)
        pre-arranged-plan (make-session-plan variety-distributed final-total)
        
        ;; Step 10: Arrange exercises for progressive difficulty (easier at start/end)
        session-plan (arrange-progressive-difficulty pre-arranged-plan)]
    session-plan))