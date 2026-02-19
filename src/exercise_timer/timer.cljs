(ns exercise-timer.timer
  "Timer Manager - handles countdown timer and exercise progression")

;; ============================================================================
;; Timer State Structure
;; ============================================================================

(def valid-session-states
  "Valid session states for the timer.
   Validates: Requirements 8.3"
  #{:not-started :running :paused :completed})

(defn valid-session-state?
  "Check if a session state is valid.
   
   Parameters:
   - state: keyword representing session state
   
   Returns:
   - true if state is valid, false otherwise
   
   Validates: Requirements 8.3"
  [state]
  (contains? valid-session-states state))

(defn make-timer-state
  "Create a timer state structure.
   
   Parameters:
   - current-exercise-index: zero-based index of current exercise (default 0)
   - remaining-seconds: seconds remaining for current exercise (default 0)
   - session-state: one of :not-started, :running, :paused, :completed (default :not-started)
   - total-elapsed-seconds: total elapsed time across all exercises (default 0)
   
   Returns:
   - {:current-exercise-index int
      :remaining-seconds int
      :session-state keyword
      :total-elapsed-seconds int}
   
   Validates: Requirements 8.1, 8.2, 8.3, 9.3"
  ([]
   (make-timer-state 0 0 :not-started 0))
  ([current-exercise-index remaining-seconds session-state]
   (make-timer-state current-exercise-index remaining-seconds session-state 0))
  ([current-exercise-index remaining-seconds session-state total-elapsed-seconds]
   {:pre [(>= current-exercise-index 0)
          (>= remaining-seconds 0)
          (valid-session-state? session-state)
          (>= total-elapsed-seconds 0)]}
   {:current-exercise-index current-exercise-index
    :remaining-seconds remaining-seconds
    :session-state session-state
    :total-elapsed-seconds total-elapsed-seconds}))

;; ============================================================================
;; Timer State Management
;; ============================================================================

;; Global timer state atom
(defonce ^:private timer-state
  (atom (make-timer-state)))

;; Current session plan atom
(defonce ^:private session-plan
  (atom nil))

;; JavaScript interval ID for the timer
(defonce ^:private interval-id
  (atom nil))

;; Track whether "Switch sides" has been announced for current exercise
(defonce ^:private switch-sides-announced
  (atom false))

;; Registered callbacks for timer events
(defonce ^:private callbacks
  (atom {:on-tick []
         :on-exercise-change []
         :on-complete []
         :on-switch-sides []}))

;; ============================================================================
;; Timer Control Functions
;; ============================================================================

;; Forward declarations
(declare start-interval!)

(defn get-state
  "Get current timer state.
   
   Returns:
   - {:current-exercise-index int
      :remaining-seconds int
      :session-state keyword
      :total-elapsed-seconds int}
   
   Validates: Requirements 8.1, 8.2, 8.3, 9.3"
  []
  @timer-state)

(defn- clear-interval!
  "Clear the JavaScript interval if it exists."
  []
  (when-let [id @interval-id]
    (js/clearInterval id)
    (reset! interval-id nil)))

(defn- set-state!
  "Set the timer state atomically.
   
   Parameters:
   - new-state: new timer state map
   
   Validates: Requirements 8.4"
  [new-state]
  {:pre [(map? new-state)
         (contains? new-state :session-state)
         (valid-session-state? (:session-state new-state))]}
  (reset! timer-state new-state))

(defn- update-state!
  "Update the timer state atomically using a function.
   
   Parameters:
   - f: function to apply to current state
   - args: additional arguments to pass to f
   
   Validates: Requirements 8.4"
  [f & args]
  (apply swap! timer-state f args))

(defn- trigger-callbacks!
  "Trigger all registered callbacks for a specific event type.
   
   Parameters:
   - event-type: keyword (:on-tick, :on-exercise-change, :on-complete)
   - args: arguments to pass to callbacks
   
   Side effects:
   - Invokes all registered callbacks for the event type"
  [event-type & args]
  (doseq [callback (get @callbacks event-type)]
    (apply callback args)))

(defn initialize-session!
  "Initialize a new session with a session plan.
   
   Parameters:
   - plan: session plan map with :exercises and :total-duration-seconds
   
   Side effects:
   - Sets session-plan atom
   - Resets timer state to first exercise
   - Clears any existing interval
   - Resets total-elapsed-seconds to 0
   
   Validates: Requirements 1.3, 2.1, 9.3"
  [plan]
  {:pre [(map? plan)
         (contains? plan :exercises)
         (seq (:exercises plan))]}
  (clear-interval!)
  (reset! session-plan plan)
  (let [first-exercise (first (:exercises plan))
        initial-duration (:duration-seconds first-exercise)]
    (set-state! (make-timer-state 0 initial-duration :not-started 0))))

(defn start!
  "Start or resume the timer.
   
   If timer is :not-started or :paused, transitions to :running and starts the interval.
   If already :running, does nothing.
   If :completed, does nothing.
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if no session is initialized
   
   Side effects:
   - Updates timer state to :running
   - Starts JavaScript interval
   
   Validates: Requirements 5.1, 5.4"
  []
  (let [current-state (get-state)
        state-keyword (:session-state current-state)]
    (cond
      ;; No session initialized
      (nil? @session-plan)
      {:error "No session initialized"}
      
      ;; Already running
      (= state-keyword :running)
      {:ok true}
      
      ;; Already completed
      (= state-keyword :completed)
      {:error "Session already completed"}
      
      ;; Start or resume
      (or (= state-keyword :not-started) (= state-keyword :paused))
      (do
        (update-state! assoc :session-state :running)
        ;; Reset switch-sides flag when starting from :not-started
        (when (= state-keyword :not-started)
          (reset! switch-sides-announced false))
        (start-interval!)
        ;; Trigger exercise-change callback when starting from :not-started
        ;; This ensures the first exercise is announced just like subsequent ones
        (when (= state-keyword :not-started)
          (trigger-callbacks! :on-exercise-change 0))
        {:ok true}))))

(defn pause!
  "Pause the timer.
   
   If timer is :running, transitions to :paused and preserves current state.
   If not :running, does nothing.
   
   Returns:
   - {:ok true} on success
   
   Side effects:
   - Updates timer state to :paused
   - Stops JavaScript interval
   - Preserves current exercise index and remaining seconds
   
   Validates: Requirements 5.2"
  []
  (let [current-state (get-state)
        state-keyword (:session-state current-state)]
    (if (= state-keyword :running)
      (do
        (clear-interval!)
        (update-state! assoc :session-state :paused)
        {:ok true})
      {:ok true})))

(defn restart!
  "Reset the session to the beginning.
   
   Resets to first exercise with its full duration.
   Clears any running interval.
   Resets total elapsed time to 0.
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if no session is initialized
   
   Side effects:
   - Resets timer state to first exercise
   - Clears JavaScript interval
   - Sets state to :not-started
   - Resets total-elapsed-seconds to 0
   
   Validates: Requirements 5.5, 9.3"
  []
  (if-let [plan @session-plan]
    (do
      (clear-interval!)
      (reset! switch-sides-announced false)
      (let [first-exercise (first (:exercises plan))
            initial-duration (:duration-seconds first-exercise)]
        (set-state! (make-timer-state 0 initial-duration :not-started 0)))
      {:ok true})
    {:error "No session initialized"}))

(defn skip-exercise!
  "Skip the current exercise and reallocate remaining time.
   
   Cancels the current exercise and reallocates its remaining time to future exercises.
   If there are future exercises, distributes the remaining time proportionally.
   If this is the last exercise, adds a new exercise from the library.
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if no session is initialized or not running
   
   Side effects:
   - Updates session plan with reallocated time
   - Advances to next exercise or adds new exercise
   - Maintains running state
   
   Validates: Requirements 5.6, 5.7"
  []
  (let [current-state (get-state)
        state-keyword (:session-state current-state)]
    (cond
      ;; No session initialized
      (nil? @session-plan)
      {:error "No session initialized"}
      
      ;; Not running
      (not= state-keyword :running)
      {:error "Session not running"}
      
      ;; Skip current exercise
      :else
      (let [current-index (:current-exercise-index current-state)
            remaining (:remaining-seconds current-state)
            elapsed (:total-elapsed-seconds current-state)
            plan @session-plan
            exercises (:exercises plan)
            future-exercises (drop (inc current-index) exercises)]
        (if (seq future-exercises)
          ;; Reallocate remaining time to future exercises
          (let [total-future-duration (reduce + (map :duration-seconds future-exercises))
                reallocation-ratio (if (> total-future-duration 0)
                                     (/ remaining total-future-duration)
                                     0)
                updated-exercises (vec
                                   (concat
                                    (take (inc current-index) exercises)
                                    (map (fn [ex]
                                           (update ex :duration-seconds
                                                   (fn [dur]
                                                     (+ dur (int (* dur reallocation-ratio))))))
                                         future-exercises)))
                updated-plan (assoc plan :exercises updated-exercises)
                next-exercise (nth updated-exercises (inc current-index))
                next-duration (:duration-seconds next-exercise)]
            (reset! session-plan updated-plan)
            (set-state! (make-timer-state (inc current-index) next-duration :running elapsed))
            (trigger-callbacks! :on-exercise-change (inc current-index))
            {:ok true})
          ;; Last exercise - complete the session
          (do
            (clear-interval!)
            (update-state! assoc :session-state :completed)
            (trigger-callbacks! :on-complete)
            {:ok true}))))))

(defn search-exercise
  "Open a web search for the current exercise name.
   
   Generates a search URL with the current exercise name and opens it in a new browser tab.
   
   Returns:
   - {:ok true :url \"search-url\"} on success
   - {:error \"message\"} if no session is initialized or no current exercise
   
   Side effects:
   - Opens a new browser tab with search results (if window is available)
   
   Validates: Requirements 5.9"
  []
  (if-let [plan @session-plan]
    (let [current-state (get-state)
          current-index (:current-exercise-index current-state)
          exercises (:exercises plan)]
      (if (< current-index (count exercises))
        (let [current-exercise (nth exercises current-index)
              exercise-name (get-in current-exercise [:exercise :name])
              search-query (str "how to do " exercise-name " exercises")
              search-url (str "https://www.google.com/search?q=" (js/encodeURIComponent search-query))]
          (when (exists? js/window)
            (js/window.open search-url "_blank"))
          {:ok true :url search-url})
        {:error "No current exercise"}))
    {:error "No session initialized"}))


;; ============================================================================
;; Timer Tick Logic and Exercise Progression
;; ============================================================================

(defn calculate-progress-percentage
  "Calculate session progress as percentage (0-100).
   
   Returns:
   - percentage value (0-100) representing (elapsed / total) × 100
   - 0 if no session is initialized
   
   Validates: Requirements 9.3"
  []
  (if-let [plan @session-plan]
    (let [state (get-state)
          elapsed (:total-elapsed-seconds state)
          total (:total-duration-seconds plan)]
      (if (> total 0)
        (* (/ elapsed total) 100.0)
        0.0))
    0.0))

(defn- advance-to-next-exercise!
  "Advance to the next exercise in the session.
   
   If there is a next exercise, updates state and triggers callbacks.
   If this was the final exercise, marks session as completed.
   Maintains total-elapsed-seconds across exercise transitions.
   
   Side effects:
   - Updates timer state
   - Triggers :on-exercise-change or :on-complete callbacks
   - Resets switch-sides-announced flag
   
   Validates: Requirements 4.3, 4.4, 9.1"
  []
  (let [current-state (get-state)
        current-index (:current-exercise-index current-state)
        elapsed (:total-elapsed-seconds current-state)
        plan @session-plan
        exercises (:exercises plan)
        next-index (inc current-index)]
    ;; Reset switch-sides flag for new exercise
    (reset! switch-sides-announced false)
    (if (< next-index (count exercises))
      ;; There is a next exercise
      (let [next-exercise (nth exercises next-index)
            next-duration (:duration-seconds next-exercise)]
        (set-state! (make-timer-state next-index next-duration :running elapsed))
        (trigger-callbacks! :on-exercise-change next-index))
      ;; No more exercises, session complete
      (do
        (clear-interval!)
        (update-state! assoc :session-state :completed)
        (trigger-callbacks! :on-complete)))))

(defn- timer-tick!
  "Handle a single timer tick (called every second).
   
   Decrements remaining seconds and increments total elapsed seconds.
   Handles exercise transitions and 'Switch sides' announcements for sided exercises.
   
   Side effects:
   - Updates timer state
   - May advance to next exercise or complete session
   - Triggers :on-tick callback
   - Triggers :on-switch-sides callback at halfway point for sided exercises
   
   Validates: Requirements 4.1, 4.2, 4.3, 4.4, 9.1, 9.3"
  []
  (let [current-state (get-state)
        state-keyword (:session-state current-state)
        remaining (:remaining-seconds current-state)]
    (when (= state-keyword :running)
      (if (> remaining 0)
        ;; Decrement remaining seconds and increment elapsed seconds
        (do
          (update-state! (fn [state]
                           (-> state
                               (update :remaining-seconds dec)
                               (update :total-elapsed-seconds inc))))
          (trigger-callbacks! :on-tick (dec remaining))
          
          ;; Check if we should announce "Switch sides" for sided exercises
          (when-not @switch-sides-announced
            (let [plan @session-plan
                  current-index (:current-exercise-index current-state)
                  exercises (:exercises plan)
                  current-exercise (nth exercises current-index)
                  exercise-data (:exercise current-exercise)
                  sided? (:sided exercise-data false)
                  total-duration (:duration-seconds current-exercise)
                  halfway-point (int (/ total-duration 2))
                  time-elapsed (- total-duration remaining)]
              ;; Announce at halfway point for sided exercises
              (when (and sided? (>= time-elapsed halfway-point))
                (reset! switch-sides-announced true)
                (trigger-callbacks! :on-switch-sides)))))
        ;; Timer reached zero, advance to next exercise
        (advance-to-next-exercise!)))))

(defn- start-interval!
  "Start the JavaScript interval for timer ticks.
   
   Side effects:
   - Creates JavaScript interval that calls timer-tick! every second
   - Stores interval ID in interval-id atom
   
   Validates: Requirements 4.2"
  []
  (clear-interval!)
  (let [id (js/setInterval timer-tick! 1000)]
    (reset! interval-id id)))


;; ============================================================================
;; Callback Registration
;; ============================================================================

(defn on-tick
  "Register a callback to be called on every timer tick (every second).
   
   Parameters:
   - callback: function that takes remaining-seconds as argument
   
   Returns:
   - nil
   
   Side effects:
   - Adds callback to :on-tick callbacks list
   
   Validates: Requirements 4.2"
  [callback]
  (swap! callbacks update :on-tick conj callback))

(defn on-exercise-change
  "Register a callback to be called when advancing to next exercise.
   
   Parameters:
   - callback: function that takes new-exercise-index as argument
   
   Returns:
   - nil
   
   Side effects:
   - Adds callback to :on-exercise-change callbacks list
   
   Validates: Requirements 3.4, 4.3"
  [callback]
  (swap! callbacks update :on-exercise-change conj callback))

(defn on-complete
  "Register a callback to be called when session completes.
   
   Parameters:
   - callback: function with no arguments
   
   Returns:
   - nil
   
   Side effects:
   - Adds callback to :on-complete callbacks list
   
   Validates: Requirements 4.4"
  [callback]
  (swap! callbacks update :on-complete conj callback))

(defn on-switch-sides
  "Register a callback to be called at halfway point for sided exercises.
   
   Parameters:
   - callback: function with no arguments
   
   Returns:
   - nil
   
   Side effects:
   - Adds callback to :on-switch-sides callbacks list"
  [callback]
  (swap! callbacks update :on-switch-sides conj callback))

(defn clear-callbacks!
  "Clear all registered callbacks.
   Useful for testing and cleanup.
   
   Returns:
   - nil
   
   Side effects:
   - Resets all callback lists to empty"
  []
  (reset! callbacks {:on-tick []
                     :on-exercise-change []
                     :on-complete []
                     :on-switch-sides []}))
