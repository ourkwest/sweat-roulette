(ns exercise-timer.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [exercise-timer.library :as library]
            [exercise-timer.session :as session]
            [exercise-timer.timer :as timer]
            [exercise-timer.format :as format]
            [exercise-timer.speech :as speech]))

;; ============================================================================
;; Global App State
;; ============================================================================

(defonce app-state
  (r/atom
    {:exercises []                    ; Exercise library
     :current-session nil             ; Current session plan or nil
     :timer-state {:current-exercise-index 0
                   :remaining-seconds 0
                   :session-state :not-started}
     :ui {:show-add-exercise false
          :show-import-dialog false
          :import-conflicts nil
          :session-duration-minutes 5
          :speech-enabled true}}))     ; Speech announcements enabled by default

;; ============================================================================
;; State Update Functions
;; ============================================================================

(defn update-exercises!
  "Update the exercises in app state.
   
   Parameters:
   - exercises: vector of exercise maps
   
   Side effects:
   - Updates :exercises in app-state atom
   
   Validates: Requirements 6.1"
  [exercises]
  (swap! app-state assoc :exercises exercises))

(defn update-current-session!
  "Update the current session plan.
   
   Parameters:
   - session-plan: session plan map or nil
   
   Side effects:
   - Updates :current-session in app-state atom
   
   Validates: Requirements 1.3, 2.1"
  [session-plan]
  (swap! app-state assoc :current-session session-plan))

(defn update-timer-state!
  "Update the timer state.
   
   Parameters:
   - timer-state: timer state map
   
   Side effects:
   - Updates :timer-state in app-state atom
   
   Validates: Requirements 8.1, 8.2, 8.3"
  [timer-state]
  (swap! app-state assoc :timer-state timer-state))

(defn update-ui!
  "Update UI state.
   
   Parameters:
   - ui-updates: map of UI state updates
   
   Side effects:
   - Merges ui-updates into :ui in app-state atom"
  [ui-updates]
  (swap! app-state update :ui merge ui-updates))

(defn get-current-exercise
  "Get the current exercise from the session.
   
   Returns:
   - Current exercise map or nil if no session active"
  []
  (when-let [session (:current-session @app-state)]
    (let [timer-state (:timer-state @app-state)
          index (:current-exercise-index timer-state)
          exercises (:exercises session)]
      (when (< index (count exercises))
        (nth exercises index)))))

;; ============================================================================
;; Initialization
;; ============================================================================

(defn initialize-app-state!
  "Initialize the application state on startup.
   
   Side effects:
   - Loads exercise library from localStorage
   - Updates app-state with loaded exercises
   
   Validates: Requirements 6.5, 6.6"
  []
  (let [exercises (library/load-library)]
    (update-exercises! exercises)))

;; ============================================================================
;; UI Components
;; ============================================================================

;; Configuration Panel Component
(defn configuration-panel []
  (let [ui (:ui @app-state)
        duration (:session-duration-minutes ui)
        speech-enabled (:speech-enabled ui)
        session-active? (not= :not-started (get-in @app-state [:timer-state :session-state]))]
    [:div.configuration-panel
     [:h2 "Session Configuration"]
     [:div.form-group
      [:label {:for "duration"} "Session Duration (minutes):"]
      [:input {:type "number"
               :id "duration"
               :min 1
               :max 120
               :value duration
               :disabled session-active?
               :on-change #(update-ui! {:session-duration-minutes (js/parseInt (-> % .-target .-value))})}]]
     
     ;; Speech toggle
     (when (speech/speech-available?)
       [:div.form-group
        [:label
         [:input {:type "checkbox"
                  :checked speech-enabled
                  :on-change #(update-ui! {:speech-enabled (-> % .-target .-checked)})}]
         " 🔊 Voice announcements (exercise names + time every 10s)"]])
     
     [:button {:on-click (fn []
                           (let [exercises (:exercises @app-state)
                                 config (session/make-session-config duration)
                                 session-plan (session/generate-session config exercises)]
                             (update-current-session! session-plan)
                             (timer/initialize-session! session-plan)
                             (update-timer-state! (timer/get-state))
                             ;; Reset speech announcement tracking for new session
                             (speech/reset-announcement-tracking!)))
               :disabled (or session-active? (empty? (:exercises @app-state)))}
      "Start Session"]]))

;; Exercise Display Component
(defn exercise-display []
  (let [session (:current-session @app-state)
        timer-state (:timer-state @app-state)
        current-ex (get-current-exercise)]
    (when (and session current-ex)
      (let [index (:current-exercise-index timer-state)
            total (count (:exercises session))
            ex-name (get-in current-ex [:exercise :name])]
        [:div.exercise-display
         [:h2 "Current Exercise"]
         [:div.exercise-name ex-name]
         [:div.exercise-progress
          (str "Exercise " (inc index) " of " total)]]))))

;; Timer Display Component
(defn timer-display []
  (let [timer-state (:timer-state @app-state)
        remaining (:remaining-seconds timer-state)
        formatted (format/seconds-to-mm-ss remaining)]
    [:div.timer-display
     [:div.timer-value formatted]]))

;; Control Panel Component
(defn control-panel []
  (let [timer-state (:timer-state @app-state)
        state-keyword (:session-state timer-state)
        session (:current-session @app-state)]
    (when session
      [:div.control-panel
       [:h3 "Controls"]
       (case state-keyword
         :not-started
         [:button {:on-click #(do (timer/start!)
                                  (update-timer-state! (timer/get-state)))}
          "Start"]
         
         :running
         [:button {:on-click #(do (timer/pause!)
                                  (update-timer-state! (timer/get-state)))}
          "Pause"]
         
         :paused
         [:button {:on-click #(do (timer/start!)
                                  (update-timer-state! (timer/get-state)))}
          "Resume"]
         
         :completed
         [:div "Session Complete!"]
         
         nil)
       
       [:button {:on-click #(do (timer/restart!)
                                (update-timer-state! (timer/get-state)))
                 :disabled (= state-keyword :not-started)}
        "Restart"]])))

;; Completion Screen Component
(defn completion-screen []
  (let [timer-state (:timer-state @app-state)
        state-keyword (:session-state timer-state)]
    (when (= state-keyword :completed)
      [:div.completion-screen
       [:h2 "🎉 Session Complete!"]
       [:p "Great job! You've completed your workout."]
       [:button {:on-click #(do (update-current-session! nil)
                                (update-timer-state! (timer/make-timer-state)))}
        "Start New Session"]])))

;; Exercise Library Panel Component
(defn exercise-library-panel []
  (let [exercises (:exercises @app-state)]
    [:div.exercise-library-panel
     [:h2 "Exercise Library"]
     [:div.library-stats
      (str (count exercises) " exercises")]
     [:div.exercise-list
      (for [ex exercises]
        ^{:key (:name ex)}
        [:div.exercise-item
         [:span.exercise-name (:name ex)]
         [:span.exercise-weight (str "Weight: " (:weight ex))]])]
     [:div.library-actions
      [:button {:on-click #(library/export-and-download!)}
       "Export Library"]
      [:button {:on-click #(js/alert "Import functionality - file picker would go here")}
       "Import Library"]
      [:button {:on-click #(js/alert "Add exercise dialog would go here")}
       "Add Exercise"]]]))

;; ============================================================================
;; Root Component
;; ============================================================================

(defn app []
  [:div.app-container
   [:header
    [:h1 "Sweat Roulette"]]
   
   [:main
    [:div.session-area
     [configuration-panel]
     
     (when (:current-session @app-state)
       [:div.active-session
        [exercise-display]
        [timer-display]
        [control-panel]
        [completion-screen]])]
    
    [:div.library-area
     [exercise-library-panel]]]])

;; ============================================================================
;; Timer Callbacks
;; ============================================================================

(defn setup-timer-callbacks!
  "Set up timer callbacks to update UI state."
  []
  (timer/clear-callbacks!)
  
  ;; Update UI on every tick
  (timer/on-tick
   (fn [remaining]
     (update-timer-state! (timer/get-state))
     ;; Announce time every 10 seconds if speech enabled
     (when (and (get-in @app-state [:ui :speech-enabled])
                (speech/should-announce-time? remaining))
       (speech/speak-time-remaining! remaining))))
  
  ;; Update UI when exercise changes
  (timer/on-exercise-change
   (fn [_new-index]
     (update-timer-state! (timer/get-state))
     ;; Announce new exercise name and duration if speech enabled
     (when (get-in @app-state [:ui :speech-enabled])
       (when-let [current-ex (get-current-exercise)]
         (let [ex-name (get-in current-ex [:exercise :name])
               duration-seconds (:duration-seconds current-ex)]
           (speech/speak-exercise-start! ex-name duration-seconds))))))
  
  ;; Update UI when session completes
  (timer/on-complete
   (fn []
     (update-timer-state! (timer/get-state))
     ;; Announce completion if speech enabled
     (when (get-in @app-state [:ui :speech-enabled])
       (speech/speak-completion!)))))

;; ============================================================================
;; App Initialization
;; ============================================================================

(defn init! []
  "Initialize the application."
  (initialize-app-state!)
  (setup-timer-callbacks!)
  (rdom/render [app]
               (.getElementById js/document "app")))

;; Hot reload support
(defn ^:dev/after-load reload! []
  (init!))
