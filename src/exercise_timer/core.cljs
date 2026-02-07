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
;; Keyboard Navigation
;; ============================================================================

(defn handle-keyboard-shortcuts!
  "Handle global keyboard shortcuts for timer controls.
   
   Shortcuts:
   - Space: Play/Pause
   - R: Restart
   - Escape: Close modals
   
   Parameters:
   - event: keyboard event"
  [event]
  (let [key (.-key event)
        timer-state (:timer-state @app-state)
        state-keyword (:session-state timer-state)
        session (:current-session @app-state)
        target (.-target event)
        tag-name (.-tagName target)]
    ;; Don't intercept if user is typing in an input
    (when-not (or (= tag-name "INPUT") (= tag-name "TEXTAREA"))
      (case key
        " " (do
              (.preventDefault event)
              (when session
                (cond
                  (= state-keyword :not-started)
                  (do (timer/start!)
                      (update-timer-state! (timer/get-state)))
                  
                  (= state-keyword :running)
                  (do (timer/pause!)
                      (update-timer-state! (timer/get-state)))
                  
                  (= state-keyword :paused)
                  (do (timer/start!)
                      (update-timer-state! (timer/get-state))))))
        
        "r" (when (and session (not= state-keyword :not-started))
              (timer/restart!)
              (update-timer-state! (timer/get-state)))
        
        "Escape" (update-ui! {:show-add-exercise false
                              :show-import-dialog false})
        
        nil))))

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

(defn add-exercise!
  "Add a new exercise to the library.
   
   Parameters:
   - name: exercise name
   - weight: exercise weight (0.5 to 2.0)
   
   Side effects:
   - Adds exercise to library
   - Updates app state
   - Closes add exercise dialog"
  [name weight]
  (let [result (library/add-exercise! {:name name :weight weight})]
    (if (contains? result :ok)
      (do
        (update-exercises! (library/load-library))
        (update-ui! {:show-add-exercise false
                     :add-exercise-name ""
                     :add-exercise-weight 1.0
                     :add-exercise-error nil}))
      (update-ui! {:add-exercise-error (:error result)}))))

(defn handle-import-file!
  "Handle file selection for import.
   
   Parameters:
   - file: JavaScript File object from file input
   
   Side effects:
   - Reads file content
   - Parses JSON and detects conflicts
   - Updates UI state with import data or error"
  [file]
  (when file
    (let [reader (js/FileReader.)]
      (set! (.-onload reader)
            (fn [e]
              (let [json-str (-> e .-target .-result)
                    import-result (library/import-from-json json-str)]
                (if (contains? import-result :ok)
                  (let [{:keys [exercises conflicts]} (:ok import-result)]
                    (if (empty? conflicts)
                      ;; No conflicts, merge immediately
                      (let [merge-result (library/merge-and-save-import! exercises {})]
                        (if (contains? merge-result :ok)
                          (do
                            (update-exercises! (library/load-library))
                            (let [{:keys [added skipped updated]} (:ok merge-result)]
                              (js/alert (str "Import successful!\n"
                                           "Added: " (count added) "\n"
                                           "Skipped (duplicates): " (count skipped) "\n"
                                           "Updated: " (count updated)))))
                          (js/alert (str "Import failed: " (:error merge-result)))))
                      ;; Has conflicts, show dialog
                      (update-ui! {:show-import-dialog true
                                   :import-exercises exercises
                                   :import-conflicts conflicts
                                   :conflict-resolutions (into {} (map (fn [c] [(:name c) :keep-existing]) conflicts))})))
                  (js/alert (str "Import failed: " (:error import-result)))))))
      (.readAsText reader file))))

(defn complete-import!
  "Complete the import after user resolves conflicts.
   
   Side effects:
   - Merges imported exercises with conflict resolutions
   - Updates app state
   - Closes import dialog"
  []
  (let [ui (:ui @app-state)
        exercises (:import-exercises ui)
        resolutions (:conflict-resolutions ui)
        merge-result (library/merge-and-save-import! exercises resolutions)]
    (if (contains? merge-result :ok)
      (do
        (update-exercises! (library/load-library))
        (let [{:keys [added skipped updated]} (:ok merge-result)]
          (update-ui! {:show-import-dialog false
                       :import-exercises nil
                       :import-conflicts nil
                       :conflict-resolutions nil})
          (js/alert (str "Import successful!\n"
                       "Added: " (count added) "\n"
                       "Skipped (duplicates): " (count skipped) "\n"
                       "Updated: " (count updated)))))
      (do
        (update-ui! {:show-import-dialog false
                     :import-exercises nil
                     :import-conflicts nil
                     :conflict-resolutions nil})
        (js/alert (str "Import failed: " (:error merge-result)))))))


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
                           (let [all-exercises (:exercises @app-state)
                                 enabled-exercises (vec (filter #(:enabled % true) all-exercises))
                                 config (session/make-session-config duration)
                                 session-plan (session/generate-session config enabled-exercises)]
                             (update-current-session! session-plan)
                             (timer/initialize-session! session-plan)
                             (update-timer-state! (timer/get-state))
                             ;; Reset speech announcement tracking for new session
                             (speech/reset-announcement-tracking!)))
               :disabled (or session-active? 
                            (empty? (:exercises @app-state))
                            (empty? (filter #(:enabled % true) (:exercises @app-state))))}
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
        [:div.exercise-display {:role "status" :aria-live "polite"}
         [:h2 "Current Exercise"]
         [:div.exercise-name {:aria-label (str "Current exercise: " ex-name)} ex-name]
         [:div.exercise-progress {:aria-label (str "Exercise " (inc index) " of " total)}
          (str "Exercise " (inc index) " of " total)]]))))

;; Timer Display Component
(defn timer-display []
  (let [timer-state (:timer-state @app-state)
        remaining (:remaining-seconds timer-state)
        formatted (format/seconds-to-mm-ss remaining)]
    [:div.timer-display {:role "timer" :aria-live "polite" :aria-atomic "true"}
     [:div.timer-value {:aria-label (str "Time remaining: " formatted)} formatted]]))

;; Control Panel Component
(defn control-panel []
  (let [timer-state (:timer-state @app-state)
        state-keyword (:session-state timer-state)
        session (:current-session @app-state)]
    (when session
      [:div.control-panel {:role "group" :aria-label "Timer controls"}
       [:h3 "Controls"]
       (case state-keyword
         :not-started
         [:button {:on-click #(do (timer/start!)
                                  (update-timer-state! (timer/get-state)))
                   :aria-label "Start workout (Spacebar)"}
          "Start"]
         
         :running
         [:button {:on-click #(do (timer/pause!)
                                  (update-timer-state! (timer/get-state)))
                   :aria-label "Pause workout (Spacebar)"}
          "Pause"]
         
         :paused
         [:button {:on-click #(do (timer/start!)
                                  (update-timer-state! (timer/get-state)))
                   :aria-label "Resume workout (Spacebar)"}
          "Resume"]
         
         :completed
         [:div "Session Complete!"]
         
         nil)
       
       [:button {:on-click #(do (timer/restart!)
                                (update-timer-state! (timer/get-state)))
                 :disabled (= state-keyword :not-started)
                 :aria-label "Restart workout (R key)"}
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
    [:div.exercise-library-panel {:role "region" :aria-label "Exercise Library"}
     [:h2 "Exercise Library"]
     [:div.library-stats {:aria-live "polite"}
      (str (count exercises) " exercises"
           " (" (count (filter #(:enabled % true) exercises)) " enabled)")]
     [:div.exercise-list {:role "list"}
      (for [ex exercises]
        (let [enabled? (:enabled ex true)
              ex-name (:name ex)
              ex-weight (:weight ex)]
          ^{:key ex-name}
          [:div.exercise-item {:class (when-not enabled? "disabled")
                               :role "listitem"}
           [:div.exercise-info
            [:span.exercise-name ex-name]
            [:span.exercise-weight (str "Weight: " ex-weight)]]
           [:div.exercise-controls {:role "group" 
                                    :aria-label (str "Controls for " ex-name)}
            [:button.weight-btn {:on-click #(do
                                              (library/update-exercise-weight! ex-name (max 0.5 (- ex-weight 0.1)))
                                              (update-exercises! (library/load-library)))
                                 :aria-label (str "Decrease weight for " ex-name " (currently " ex-weight ")")
                                 :title "Decrease weight"}
             "−"]
            [:button.weight-btn {:on-click #(do
                                              (library/update-exercise-weight! ex-name (min 2.0 (+ ex-weight 0.1)))
                                              (update-exercises! (library/load-library)))
                                 :aria-label (str "Increase weight for " ex-name " (currently " ex-weight ")")
                                 :title "Increase weight"}
             "+"]
            [:button.toggle-btn {:on-click #(do
                                              (library/toggle-exercise-enabled! ex-name)
                                              (update-exercises! (library/load-library)))
                                 :class (if enabled? "enabled" "disabled")
                                 :aria-label (str (if enabled? "Disable" "Enable") " " ex-name " in sessions")
                                 :aria-pressed (if enabled? "true" "false")
                                 :title (if enabled? "Exclude from sessions" "Include in sessions")}
             (if enabled? "✓" "✗")]
            [:button.delete-btn {:on-click #(when (js/confirm (str "Delete '" ex-name "'?"))
                                              (library/delete-exercise! ex-name)
                                              (update-exercises! (library/load-library)))
                                 :aria-label (str "Delete " ex-name)
                                 :title "Delete exercise"}
             "🗑"]]]))]
     [:div.library-actions
      [:button {:on-click #(library/export-and-download!)
                :aria-label "Export exercise library to JSON file"}
       "Export Library"]
      [:label.import-button
       [:input {:type "file"
                :accept ".json"
                :style {:display "none"}
                :on-change #(when-let [file (-> % .-target .-files (aget 0))]
                             (handle-import-file! file)
                             (set! (-> % .-target .-value) ""))}]
       [:button {:on-click #(.click (-> % .-target .-previousSibling))
                 :aria-label "Import exercise library from JSON file"}
        "Import Library"]]
      [:button {:on-click #(update-ui! {:show-add-exercise true
                                        :add-exercise-name ""
                                        :add-exercise-weight 1.0
                                        :add-exercise-error nil})
                :aria-label "Add new exercise to library"}
       "Add Exercise"]]]))

;; Add Exercise Dialog Component
(defn add-exercise-dialog []
  (let [ui (:ui @app-state)
        show? (:show-add-exercise ui)
        name (:add-exercise-name ui "")
        weight (:add-exercise-weight ui 1.0)
        error (:add-exercise-error ui)
        input-ref (atom nil)]
    (when show?
      [:div.modal-overlay {:on-click #(update-ui! {:show-add-exercise false})
                           :role "dialog"
                           :aria-modal "true"
                           :aria-labelledby "add-exercise-title"}
       [:div.modal-content {:on-click #(.stopPropagation %)}
        [:h2#add-exercise-title "Add New Exercise"]
        
        (when error
          [:div.error-message {:role "alert" :aria-live "assertive"} error])
        
        [:div.form-group
         [:label {:for "exercise-name"} "Exercise Name:"]
         [:input {:type "text"
                  :id "exercise-name"
                  :value name
                  :placeholder "e.g., Push-ups"
                  :aria-required "true"
                  :ref (fn [el] 
                         (reset! input-ref el)
                         (when el (.focus el)))
                  :on-change #(update-ui! {:add-exercise-name (-> % .-target .-value)})
                  :on-key-press #(when (= (.-key %) "Enter")
                                   (add-exercise! name weight))}]]
        
        [:div.form-group
         [:label {:for "exercise-weight"} 
          (str "Weight: " weight " (0.5 = easier, 2.0 = harder)")]
         [:input {:type "range"
                  :id "exercise-weight"
                  :min 0.5
                  :max 2.0
                  :step 0.1
                  :value weight
                  :aria-valuemin 0.5
                  :aria-valuemax 2.0
                  :aria-valuenow weight
                  :aria-label "Exercise difficulty weight"
                  :on-change #(update-ui! {:add-exercise-weight (js/parseFloat (-> % .-target .-value))})}]]
        
        [:div.modal-actions
         [:button {:on-click #(add-exercise! name weight)
                   :aria-label "Add exercise to library"}
          "Add Exercise"]
         [:button {:on-click #(update-ui! {:show-add-exercise false})
                   :aria-label "Cancel and close dialog"}
          "Cancel"]]]])))

;; Import Conflict Dialog Component
(defn import-conflict-dialog []
  (let [ui (:ui @app-state)
        show? (:show-import-dialog ui)
        conflicts (:import-conflicts ui)
        resolutions (:conflict-resolutions ui)]
    (when show?
      [:div.modal-overlay {:on-click #(update-ui! {:show-import-dialog false
                                                    :import-exercises nil
                                                    :import-conflicts nil
                                                    :conflict-resolutions nil})
                           :role "dialog"
                           :aria-modal "true"
                           :aria-labelledby "import-dialog-title"}
       [:div.modal-content.import-dialog {:on-click #(.stopPropagation %)}
        [:h2#import-dialog-title "Import Conflicts Detected"]
        
        [:p "The following exercises exist in both your library and the import file with different weights. Choose which version to keep:"]
        
        [:div.conflict-list
         (for [conflict conflicts]
           (let [ex-name (:name conflict)
                 existing-weight (:existing-weight conflict)
                 imported-weight (:imported-weight conflict)
                 current-choice (get resolutions ex-name :keep-existing)]
             ^{:key ex-name}
             [:div.conflict-item
              [:div.conflict-header
               [:strong ex-name]]
              [:div.conflict-options
               [:label.conflict-option
                [:input {:type "radio"
                         :name (str "conflict-" ex-name)
                         :checked (= current-choice :keep-existing)
                         :on-change #(update-ui! {:conflict-resolutions (assoc resolutions ex-name :keep-existing)})}]
                [:span (str "Keep existing (weight: " existing-weight ")")]]
               [:label.conflict-option
                [:input {:type "radio"
                         :name (str "conflict-" ex-name)
                         :checked (= current-choice :use-imported)
                         :on-change #(update-ui! {:conflict-resolutions (assoc resolutions ex-name :use-imported)})}]
                [:span (str "Use imported (weight: " imported-weight ")")]]]]))]
        
        [:div.modal-actions
         [:button {:on-click #(complete-import!)
                   :aria-label "Complete import with selected resolutions"}
          "Import"]
         [:button {:on-click #(update-ui! {:show-import-dialog false
                                           :import-exercises nil
                                           :import-conflicts nil
                                           :conflict-resolutions nil})
                   :aria-label "Cancel import"}
          "Cancel"]]]])))

;; ============================================================================
;; Root Component
;; ============================================================================

(defn app []
  [:div.app-container
   [:header
    [:h1 "Sweat Roulette"]]
   
   [:main#main-content
    [:div.session-area
     [configuration-panel]
     
     (when (:current-session @app-state)
       [:div.active-session
        [exercise-display]
        [timer-display]
        [control-panel]
        [completion-screen]])]
    
    [:div.library-area
     [exercise-library-panel]]]
   
   ;; Modals
   [add-exercise-dialog]
   [import-conflict-dialog]])

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
  ;; Add keyboard event listener
  (.addEventListener js/document "keydown" handle-keyboard-shortcuts!)
  (rdom/render [app]
               (.getElementById js/document "app")))

;; Hot reload support
(defn ^:dev/after-load reload! []
  (init!))
