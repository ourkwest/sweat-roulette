(ns exercise-timer.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [exercise-timer.library :as library]
            [exercise-timer.session :as session]
            [exercise-timer.timer :as timer]))

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
          :session-duration-minutes 5}}))  ; Default session duration

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
;; Root Component
;; ============================================================================

;; Root component - will be fully implemented in task 10
(defn app []
  [:div
   [:h1 "Exercise Timer App"]
   [:p "Application structure initialized. UI components will be implemented in later tasks."]
   [:p (str "Loaded " (count (:exercises @app-state)) " exercises from library.")]])

;; ============================================================================
;; App Initialization
;; ============================================================================

(defn init! []
  "Initialize the application."
  (initialize-app-state!)
  (rdom/render [app]
               (.getElementById js/document "app")))

;; Hot reload support
(defn ^:dev/after-load reload! []
  (init!))
