(ns exercise-timer.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom-client]
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
     :session-config {:equipment #{}}  ; Selected equipment types
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
   - difficulty: exercise difficulty (0.5 to 2.0)
   - equipment: vector of equipment type strings
   
   Side effects:
   - Adds exercise to library
   - Updates app state
   - Closes add exercise dialog"
  [name difficulty equipment]
  (let [result (library/add-exercise! {:name name :difficulty difficulty :equipment equipment})]
    (if (contains? result :ok)
      (do
        (update-exercises! (library/load-library))
        (update-ui! {:show-add-exercise false
                     :add-exercise-name ""
                     :add-exercise-difficulty 1.0
                     :add-exercise-equipment ""
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
   - Initializes equipment selection with all equipment types
   
   Validates: Requirements 6.5, 6.6, 13.4"
  []
  (let [exercises (library/load-library)
        equipment-types (library/get-equipment-types)]
    (update-exercises! exercises)
    ;; Initialize equipment selection with all equipment types (default)
    (swap! app-state assoc-in [:session-config :equipment] equipment-types)))

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
     
     ;; Equipment selection checkboxes
     (let [all-equipment-types (library/get-equipment-types)
           selected-equipment (get-in @app-state [:session-config :equipment])]
       (when (seq all-equipment-types)
         [:div.form-group
          [:label "Available Equipment:"]
          [:div.equipment-checkboxes {:role "group" :aria-label "Equipment selection"}
           (for [equipment (sort all-equipment-types)]
             ^{:key equipment}
             [:label.equipment-checkbox
              [:input {:type "checkbox"
                       :checked (contains? selected-equipment equipment)
                       :disabled session-active?
                       :on-change #(let [checked (-> % .-target .-checked)]
                                     (swap! app-state update-in [:session-config :equipment]
                                            (fn [current-equipment]
                                              (if checked
                                                (conj current-equipment equipment)
                                                (disj current-equipment equipment)))))}]
              [:span equipment]])]]))
     
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
                                 session-config (:session-config @app-state)
                                 duration (:session-duration-minutes ui)
                                 equipment (:equipment session-config)
                                 config (session/make-session-config duration equipment)
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
            ex-name (get-in current-ex [:exercise :name])
            ex-difficulty (get-in current-ex [:exercise :difficulty])
            state-keyword (:session-state timer-state)
            is-active? (or (= state-keyword :running) (= state-keyword :paused))]
        [:div.exercise-display {:role "status" :aria-live "polite"}
         [:h2 "Current Exercise"]
         [:div.exercise-name {:aria-label (str "Current exercise: " ex-name)} ex-name]
         
         ;; Difficulty adjustment controls (only show during active exercise)
         (when is-active?
           [:div.difficulty-controls {:role "group" :aria-label "Difficulty adjustment"}
            [:button.difficulty-btn {:on-click #(do
                                                   (library/update-exercise-difficulty! ex-name (max 0.5 (- ex-difficulty 0.1)))
                                                   (update-exercises! (library/load-library)))
                                     :aria-label (str "Decrease difficulty for " ex-name " (currently " ex-difficulty ")")
                                     :title "Decrease difficulty"}
             "−"]
            [:span.difficulty-value {:aria-label (str "Current difficulty: " ex-difficulty)}
             (str "Difficulty: " (.toFixed ex-difficulty 1))]
            [:button.difficulty-btn {:on-click #(do
                                                   (library/update-exercise-difficulty! ex-name (min 2.0 (+ ex-difficulty 0.1)))
                                                   (update-exercises! (library/load-library)))
                                     :aria-label (str "Increase difficulty for " ex-name " (currently " ex-difficulty ")")
                                     :title "Increase difficulty"}
             "+"]])
         
         [:div.exercise-progress {:aria-label (str "Exercise " (inc index) " of " total)}
          (str "Exercise " (inc index) " of " total)]]))))

(defn timer-display []
  (let [timer-state (:timer-state @app-state)
        remaining (:remaining-seconds timer-state)
        formatted (format/seconds-to-mm-ss remaining)]
    [:div.timer-display {:role "timer" :aria-live "polite" :aria-atomic "true"}
     [:div.timer-value {:aria-label (str "Time remaining: " formatted)} formatted]]))

;; Progress Bar Component
(defn progress-bar []
  (let [session (:current-session @app-state)
        timer-state (:timer-state @app-state)
        state-keyword (:session-state timer-state)]
    (when (and session (not= state-keyword :not-started))
      (let [progress-pct (timer/calculate-progress-percentage)]
        [:div.progress-bar-container {:role "progressbar"
                                      :aria-valuenow progress-pct
                                      :aria-valuemin 0
                                      :aria-valuemax 100
                                      :aria-label (str "Session progress: " (.toFixed progress-pct 0) "%")}
         [:div.progress-bar-fill {:style {:width (str progress-pct "%")}}]
         [:div.progress-bar-text (str (.toFixed progress-pct 0) "%")]]))))

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
         [:div
          [:button {:on-click #(do (timer/pause!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Pause workout (Spacebar)"}
           "Pause"]
          [:button {:on-click #(do (timer/skip-exercise!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Skip current exercise"}
           "Skip"]]
         
         :paused
         [:div
          [:button {:on-click #(do (timer/start!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Resume workout (Spacebar)"}
           "Resume"]
          [:button {:on-click #(timer/search-exercise)
                    :aria-label "Search for current exercise instructions"}
           "Search Exercise"]]
         
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
              ex-difficulty (:difficulty ex)]
          ^{:key ex-name}
          [:div.exercise-item {:class (when-not enabled? "disabled")
                               :role "listitem"}
           [:div.exercise-info
            [:span.exercise-name ex-name]
            [:span.exercise-difficulty (str "Difficulty: " ex-difficulty)]]
           [:div.exercise-controls {:role "group" 
                                    :aria-label (str "Controls for " ex-name)}
            [:button.difficulty-btn {:on-click #(do
                                              (library/update-exercise-difficulty! ex-name (max 0.5 (- ex-difficulty 0.1)))
                                              (update-exercises! (library/load-library)))
                                 :aria-label (str "Decrease difficulty for " ex-name " (currently " ex-difficulty ")")
                                 :title "Decrease difficulty"}
             "−"]
            [:button.difficulty-btn {:on-click #(do
                                              (library/update-exercise-difficulty! ex-name (min 2.0 (+ ex-difficulty 0.1)))
                                              (update-exercises! (library/load-library)))
                                 :aria-label (str "Increase difficulty for " ex-name " (currently " ex-difficulty ")")
                                 :title "Increase difficulty"}
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
                                        :add-exercise-difficulty 1.0
                                        :add-exercise-error nil})
                :aria-label "Add new exercise to library"}
       "Add Exercise"]
      [:button {:on-click #(when (js/confirm "Reset all data and restore default exercises? This cannot be undone.")
                             (library/clear-library-for-testing!)
                             (update-exercises! (library/load-library)))
                :aria-label "Reset all data to defaults"
                :style {:background "#e74c3c"}}
       "Reset Data"]]]))

;; Add Exercise Dialog Component
(defn add-exercise-dialog []
  (let [ui (:ui @app-state)
        show? (:show-add-exercise ui)
        name (:add-exercise-name ui "")
        difficulty (:add-exercise-difficulty ui 1.0)
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
                                   (let [equipment-str (or (:add-exercise-equipment ui) "")
                                         equipment-vec (if (empty? equipment-str)
                                                         ["None"]
                                                         (vec (map clojure.string/trim (clojure.string/split equipment-str #","))))]
                                     (add-exercise! name difficulty equipment-vec)))}]]
        
        [:div.form-group
         [:label {:for "exercise-difficulty"} 
          (str "Difficulty: " difficulty " (0.5 = easier, 2.0 = harder)")]
         [:input {:type "range"
                  :id "exercise-difficulty"
                  :min 0.5
                  :max 2.0
                  :step 0.1
                  :value difficulty
                  :aria-valuemin 0.5
                  :aria-valuemax 2.0
                  :aria-valuenow difficulty
                  :aria-label "Exercise difficulty level"
                  :on-change #(update-ui! {:add-exercise-difficulty (js/parseFloat (-> % .-target .-value))})}]]
        
        [:div.form-group
         [:label {:for "exercise-equipment"} "Equipment (comma-separated, or leave empty for 'None'):"]
         [:input {:type "text"
                  :id "exercise-equipment"
                  :value (or (:add-exercise-equipment ui) "")
                  :placeholder "e.g., Dumbbells, A wall"
                  :on-change #(update-ui! {:add-exercise-equipment (-> % .-target .-value)})}]]
        
        [:div.modal-actions
         [:button {:on-click #(let [equipment-str (or (:add-exercise-equipment ui) "")
                                    equipment-vec (if (empty? equipment-str)
                                                    ["None"]
                                                    (vec (map clojure.string/trim (clojure.string/split equipment-str #","))))]
                                (add-exercise! name difficulty equipment-vec))
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
        
        [:p "The following exercises exist in both your library and the import file with different difficulties. Choose which version to keep:"]
        
        [:div.conflict-list
         (for [conflict conflicts]
           (let [ex-name (:name conflict)
                 existing-difficulty (:existing-difficulty conflict)
                 imported-difficulty (:imported-difficulty conflict)
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
                [:span (str "Keep existing (difficulty: " existing-difficulty ")")]]
               [:label.conflict-option
                [:input {:type "radio"
                         :name (str "conflict-" ex-name)
                         :checked (= current-choice :use-imported)
                         :on-change #(update-ui! {:conflict-resolutions (assoc resolutions ex-name :use-imported)})}]
                [:span (str "Use imported (difficulty: " imported-difficulty ")")]]]]))]
        
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
        [progress-bar]
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

;; Store the root for React 18 createRoot API
(defonce root (atom nil))

(defn init! []
  "Initialize the application."
  (initialize-app-state!)
  (setup-timer-callbacks!)
  ;; Add keyboard event listener
  (.addEventListener js/document "keydown" handle-keyboard-shortcuts!)
  ;; Use React 18's createRoot API
  (when-not @root
    (reset! root (rdom-client/create-root (.getElementById js/document "app"))))
  (rdom-client/render @root [app]))

;; Hot reload support
(defn ^:dev/after-load reload! []
  (init!))
