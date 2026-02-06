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
   
   Returns:
   - {:current-exercise-index int
      :remaining-seconds int
      :session-state keyword}
   
   Validates: Requirements 8.1, 8.2, 8.3"
  ([]
   (make-timer-state 0 0 :not-started))
  ([current-exercise-index remaining-seconds session-state]
   {:pre [(>= current-exercise-index 0)
          (>= remaining-seconds 0)
          (valid-session-state? session-state)]}
   {:current-exercise-index current-exercise-index
    :remaining-seconds remaining-seconds
    :session-state session-state}))

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

;; Registered callbacks for timer events
(defonce ^:private callbacks
  (atom {:on-tick []
         :on-exercise-change []
         :on-complete []}))

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
      :session-state keyword}
   
   Validates: Requirements 8.1, 8.2, 8.3"
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
   
   Validates: Requirements 1.3, 2.1"
  [plan]
  {:pre [(map? plan)
         (contains? plan :exercises)
         (seq (:exercises plan))]}
  (clear-interval!)
  (reset! session-plan plan)
  (let [first-exercise (first (:exercises plan))
        initial-duration (:duration-seconds first-exercise)]
    (set-state! (make-timer-state 0 initial-duration :not-started))))

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
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} if no session is initialized
   
   Side effects:
   - Resets timer state to first exercise
   - Clears JavaScript interval
   - Sets state to :not-started
   
   Validates: Requirements 5.5"
  []
  (if-let [plan @session-plan]
    (do
      (clear-interval!)
      (let [first-exercise (first (:exercises plan))
            initial-duration (:duration-seconds first-exercise)]
        (set-state! (make-timer-state 0 initial-duration :not-started)))
      {:ok true})
    {:error "No session initialized"}))


;; ============================================================================
;; Timer Tick Logic and Exercise Progression
;; ============================================================================

(defn- advance-to-next-exercise!
  "Advance to the next exercise in the session.
   
   If there is a next exercise, updates state and triggers callbacks.
   If this was the final exercise, marks session as completed.
   
   Side effects:
   - Updates timer state
   - Triggers :on-exercise-change or :on-complete callbacks
   
   Validates: Requirements 4.3, 4.4"
  []
  (let [current-state (get-state)
        current-index (:current-exercise-index current-state)
        plan @session-plan
        exercises (:exercises plan)
        next-index (inc current-index)]
    (if (< next-index (count exercises))
      ;; There is a next exercise
      (let [next-exercise (nth exercises next-index)
            next-duration (:duration-seconds next-exercise)]
        (set-state! (make-timer-state next-index next-duration :running))
        (trigger-callbacks! :on-exercise-change next-index))
      ;; No more exercises, session complete
      (do
        (clear-interval!)
        (update-state! assoc :session-state :completed)
        (trigger-callbacks! :on-complete)))))

(defn- timer-tick!
  "Handle a single timer tick (called every second).
   
   Decrements remaining seconds and handles exercise transitions.
   
   Side effects:
   - Updates timer state
   - May advance to next exercise or complete session
   - Triggers :on-tick callback
   
   Validates: Requirements 4.1, 4.2, 4.3, 4.4"
  []
  (let [current-state (get-state)
        state-keyword (:session-state current-state)
        remaining (:remaining-seconds current-state)]
    (when (= state-keyword :running)
      (if (> remaining 0)
        ;; Decrement remaining seconds
        (do
          (update-state! update :remaining-seconds dec)
          (trigger-callbacks! :on-tick (dec remaining)))
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
                     :on-complete []}))
