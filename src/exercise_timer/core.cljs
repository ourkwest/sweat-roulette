(ns exercise-timer.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom-client]
            [exercise-timer.library :as library]
            [exercise-timer.session :as session]
            [exercise-timer.timer :as timer]
            [exercise-timer.format :as format]
            [exercise-timer.speech :as speech]
            [exercise-timer.wakelock :as wakelock]
            [exercise-timer.version :as version]))

;; ============================================================================
;; Forward Declarations
;; ============================================================================

(declare update-timer-state!)
(declare update-ui!)

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
     :session-config {:equipment #{}
                      :excluded-tags #{}}  ; Selected equipment types and excluded tags
     :ui {:show-edit-exercise false
          :show-import-dialog false
          :show-disclaimer false
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
        
        "Escape" (update-ui! {:show-edit-exercise false
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

(defn save-exercise!
  "Save an exercise to the library (handles both new and existing exercises).
   
   Parameters:
   - original-name: original exercise name (empty string for new exercises)
   - new-name: new exercise name
   - difficulty: exercise difficulty (0.5 to 2.0)
   - equipment: vector of equipment type strings
   - tags: vector of tag strings
   - enabled: whether exercise is enabled
   - sided: whether exercise requires switching sides
   
   Side effects:
   - Adds or updates exercise in library
   - Updates app state
   - Closes edit exercise dialog"
  [original-name new-name difficulty equipment tags enabled sided]
  (let [is-new? (empty? original-name)
        name-changed? (and (not is-new?) (not= original-name new-name))]
    ;; Delete old exercise if editing (either name changed or just updating properties)
    (when (not is-new?)
      (library/delete-exercise! original-name))
    
    (let [result (library/add-exercise! {:name new-name :difficulty difficulty :equipment equipment :tags tags :enabled enabled :sided sided})]
      (if (contains? result :ok)
        (do
          (update-exercises! (library/load-library))
          (update-ui! {:show-edit-exercise false
                       :edit-exercise-name ""
                       :edit-exercise-original-name ""
                       :edit-exercise-difficulty 1.0
                       :edit-exercise-equipment ""
                       :edit-exercise-tags ""
                       :edit-exercise-enabled true
                       :edit-exercise-sided false
                       :edit-exercise-error nil}))
        (do
          ;; If update failed, restore the original exercise
          (when (not is-new?)
            (library/add-exercise! {:name original-name :difficulty difficulty :equipment equipment :tags tags :enabled enabled :sided sided}))
          (update-exercises! (library/load-library))
          (update-ui! {:edit-exercise-error (:error result)}))))))

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
   - Current exercise map or nil if no session active
   - Merges current difficulty from library to reflect real-time updates"
  []
  (when-let [session (:current-session @app-state)]
    (let [timer-state (:timer-state @app-state)
          index (:current-exercise-index timer-state)
          exercises (:exercises session)]
      (when (< index (count exercises))
        (let [session-ex (nth exercises index)
              ex-name (get-in session-ex [:exercise :name])
              ;; Get current difficulty from library
              library-exercises (:exercises @app-state)
              library-ex (first (filter #(= (:name %) ex-name) library-exercises))
              current-difficulty (if library-ex (:difficulty library-ex) (get-in session-ex [:exercise :difficulty]))]
          ;; Merge current difficulty into session exercise
          (assoc-in session-ex [:exercise :difficulty] current-difficulty))))))

;; ============================================================================
;; Session Config Persistence
;; ============================================================================

(def ^:private session-config-storage-key "exercise-timer-session-config")

(defn- save-session-config!
  "Save session configuration to localStorage.
   
   Parameters:
   - config: map with :equipment, :excluded-tags, and :duration-minutes
   
   Side effects:
   - Writes to localStorage"
  [config]
  (try
    (let [config-data {:equipment (vec (:equipment config))
                       :excluded-tags (vec (:excluded-tags config))
                       :duration-minutes (:duration-minutes config)}
          json-str (js/JSON.stringify (clj->js config-data))]
      (.setItem js/localStorage session-config-storage-key json-str))
    (catch js/Error e
      (js/console.error "Failed to save session config:" (.-message e)))))

(defn- load-session-config
  "Load session configuration from localStorage.
   
   Returns:
   - Map with :equipment, :excluded-tags, and :duration-minutes if found
   - nil if not found or error"
  []
  (try
    (when-let [json-str (.getItem js/localStorage session-config-storage-key)]
      (let [parsed (js->clj (js/JSON.parse json-str) :keywordize-keys true)]
        {:equipment (set (:equipment parsed))
         :excluded-tags (set (:excluded-tags parsed))
         :duration-minutes (:duration-minutes parsed)}))
    (catch js/Error e
      (js/console.error "Failed to load session config:" (.-message e))
      nil)))

;; ============================================================================
;; Initialization
;; ============================================================================

(defn initialize-app-state!
  "Initialize the application state on startup.
   
   Side effects:
   - Loads exercise library from localStorage
   - Loads session config preferences from localStorage
   - Updates app-state with loaded exercises and config
   - Initializes equipment selection with all equipment types if no saved config
   
   Validates: Requirements 6.5, 6.6, 13.4"
  []
  (let [exercises (library/load-library)
        equipment-types (library/get-equipment-types)
        saved-config (load-session-config)]
    (update-exercises! exercises)
    ;; Load saved config or initialize with defaults
    (if saved-config
      (do
        ;; Restore saved preferences
        (swap! app-state assoc-in [:session-config :equipment] (:equipment saved-config))
        (swap! app-state assoc-in [:session-config :excluded-tags] (:excluded-tags saved-config))
        (when (:duration-minutes saved-config)
          (swap! app-state assoc-in [:ui :session-duration-minutes] (:duration-minutes saved-config))))
      ;; Initialize with defaults (all equipment, no excluded tags)
      (swap! app-state assoc-in [:session-config :equipment] equipment-types))))

;; Add watcher to save session config when it changes
(add-watch app-state :session-config-watcher
  (fn [_ _ old-state new-state]
    (let [old-config {:equipment (get-in old-state [:session-config :equipment])
                      :excluded-tags (get-in old-state [:session-config :excluded-tags])
                      :duration-minutes (get-in old-state [:ui :session-duration-minutes])}
          new-config {:equipment (get-in new-state [:session-config :equipment])
                      :excluded-tags (get-in new-state [:session-config :excluded-tags])
                      :duration-minutes (get-in new-state [:ui :session-duration-minutes])}]
      ;; Only save if config actually changed
      (when (not= old-config new-config)
        (save-session-config! new-config)))))

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
     [:div.form-group.inline-input
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
     
     ;; Tag exclusion checkboxes - split into Type and Muscle Groups
     (let [all-tags (library/get-all-tags)
           excluded-tags (get-in @app-state [:session-config :excluded-tags])
           type-tags #{"cardio" "strength" "flexibility" "balance" "plyometric" 
                       "low-impact" "high-impact"}
           muscle-tags (clojure.set/difference all-tags type-tags)]
       (when (seq all-tags)
         [:div.form-group
          ;; Type tags section
          [:label "Type:"]
          [:div.equipment-checkboxes {:role "group" :aria-label "Exercise type exclusion"}
           (for [tag (sort (filter type-tags all-tags))]
             ^{:key tag}
             [:label.equipment-checkbox
              [:input {:type "checkbox"
                       :checked (not (contains? excluded-tags tag))
                       :disabled session-active?
                       :on-change #(let [checked (-> % .-target .-checked)]
                                     (swap! app-state update-in [:session-config :excluded-tags]
                                            (fn [current-excluded]
                                              (if checked
                                                (disj current-excluded tag)
                                                (conj current-excluded tag)))))}]
              [:span tag]])]
          
          ;; Muscle groups section
          (when (seq muscle-tags)
            [:div {:style {:margin-top "15px"}}
             [:label "Muscle Groups:"]
             [:div.equipment-checkboxes {:role "group" :aria-label "Muscle group exclusion"}
              (for [tag (sort muscle-tags)]
                ^{:key tag}
                [:label.equipment-checkbox
                 [:input {:type "checkbox"
                          :checked (not (contains? excluded-tags tag))
                          :disabled session-active?
                          :on-change #(let [checked (-> % .-target .-checked)]
                                        (swap! app-state update-in [:session-config :excluded-tags]
                                               (fn [current-excluded]
                                                 (if checked
                                                   (disj current-excluded tag)
                                                   (conj current-excluded tag)))))}]
                 [:span tag]])]])]))
     
     [:div.button-row
      [:button {:on-click (fn []
                            ;; Check if disclaimer has been accepted
                            (let [disclaimer-accepted? (= "true" (.getItem js/localStorage "disclaimer-accepted"))]
                              (if disclaimer-accepted?
                                ;; Start session immediately
                                (let [all-exercises (:exercises @app-state)
                                      enabled-exercises (vec (filter #(:enabled % true) all-exercises))
                                      session-config (:session-config @app-state)
                                      duration (:session-duration-minutes ui)
                                      equipment (:equipment session-config)
                                      excluded-tags (:excluded-tags session-config)
                                      config (session/make-session-config duration equipment excluded-tags)
                                      session-plan (session/generate-session config enabled-exercises)]
                                  (update-current-session! session-plan)
                                  (timer/initialize-session! session-plan)
                                  (update-timer-state! (timer/get-state))
                                  ;; Reset speech announcement tracking for new session
                                  (speech/reset-announcement-tracking!)
                                  ;; Request wake lock to keep screen on during workout
                                  (wakelock/request-wake-lock!))
                                ;; Show disclaimer first
                                (update-ui! {:show-disclaimer true}))))
                :disabled (or session-active? 
                             (empty? (:exercises @app-state))
                             (empty? (filter #(:enabled % true) (:exercises @app-state))))}
       "Start"]
      
      ;; Speech toggle
      (when (speech/speech-available?)
        [:label.voice-checkbox
         [:input {:type "checkbox"
                  :checked speech-enabled
                  :on-change #(update-ui! {:speech-enabled (-> % .-target .-checked)})}]
         [:span "🔊 Voice announcements"]])]]))

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
         [:<>
          [:button {:on-click #(do (timer/pause!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Pause workout (Spacebar)"}
           "Pause"]
          [:button {:on-click #(do (timer/skip-exercise!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Skip current exercise"}
           "Skip"]
          [:button {:on-click #(do (timer/restart!)
                                   (update-timer-state! (timer/get-state)))
                   :aria-label "Restart workout (R key)"}
           "Restart"]
          [:button {:on-click #(timer/search-exercise)
                    :aria-label "Search for current exercise instructions"}
           "Search Exercise"]]
         
         :paused
         [:<>
          [:button {:on-click #(do (timer/start!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Resume workout (Spacebar)"}
           "Resume"]
          [:button {:on-click #(do (timer/skip-exercise!)
                                   (update-timer-state! (timer/get-state)))
                    :aria-label "Skip current exercise"}
           "Skip"]
          [:button {:on-click #(do (timer/restart!)
                                   (update-timer-state! (timer/get-state)))
                   :aria-label "Restart workout (R key)"}
           "Restart"]
          [:button {:on-click #(timer/search-exercise)
                    :aria-label "Search for current exercise instructions"}
           "Search Exercise"]]
         
         :completed
         [:div "Session Complete!"]
         
         nil)
       
       ;; Back/Cancel button to return to setup view
       [:button {:on-click #(do (timer/pause!)
                                (update-current-session! nil)
                                (update-timer-state! (timer/make-timer-state))
                                (wakelock/release-wake-lock!))
                 :aria-label "Cancel session and return to setup"}
        "Cancel Session"]])))

;; Completion Screen Component
(defn completion-screen []
  (let [timer-state (:timer-state @app-state)
        state-keyword (:session-state timer-state)]
    (when (= state-keyword :completed)
      (do
        ;; Release wake lock when session completes
        (wakelock/release-wake-lock!)
        [:div.completion-screen
         [:h2 "🎉 Session Complete!"]
         [:p "Great job! You've completed your workout."]
         [:button {:on-click #(do (update-current-session! nil)
                                  (update-timer-state! (timer/make-timer-state)))}
          "Start New Session"]]))))

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
              ex-difficulty (:difficulty ex)
              ex-equipment (:equipment ex [])
              ex-tags (:tags ex [])
              ex-sided (:sided ex false)
              open-edit-fn #(update-ui! {:show-edit-exercise true
                                         :edit-exercise-name ex-name
                                         :edit-exercise-original-name ex-name
                                         :edit-exercise-difficulty ex-difficulty
                                         :edit-exercise-equipment (clojure.string/join ", " ex-equipment)
                                         :edit-exercise-tags (clojure.string/join ", " ex-tags)
                                         :edit-exercise-enabled enabled?
                                         :edit-exercise-sided ex-sided
                                         :edit-exercise-error nil})]
          ^{:key ex-name}
          [:div.exercise-item {:class (when-not enabled? "disabled")
                               :role "listitem"
                               :on-click open-edit-fn
                               :style {:cursor "pointer"}
                               :aria-label (str "Edit " ex-name)}
           [:div.exercise-info
            [:div.exercise-header
             [:span.exercise-name ex-name]]
            [:div.exercise-details
             [:span.exercise-difficulty 
              (str "Difficulty: " (.toFixed ex-difficulty 1))]
             (when-not (empty? ex-equipment)
               [:span.exercise-equipment 
                (str "Equipment: " (clojure.string/join ", " ex-equipment))])]
            [:div.exercise-tags
             (let [type-tags #{"cardio" "strength" "flexibility" "balance" "plyometric" 
                               "low-impact" "high-impact"}
                   type-tag-list (filter #(contains? type-tags %) ex-tags)
                   muscle-tag-list (filter #(not (contains? type-tags %)) ex-tags)]
               (concat
                 ;; Single-sided badge first if applicable
                 (when ex-sided
                   [^{:key "sided"} [:span.tag-badge.sided-badge "single-sided"]])
                 ;; Then type tags
                 (for [tag type-tag-list]
                   ^{:key tag}
                   [:span.tag-badge.type-tag tag])
                 ;; Then muscle tags
                 (for [tag muscle-tag-list]
                   ^{:key tag}
                   [:span.tag-badge.muscle-tag tag])))]]]))]
     [:div.library-actions
      [:button {:on-click #(library/export-and-download!)
                :aria-label "Export exercise library to JSON file"}
       "Export"]
      [:label.import-button
       [:input {:type "file"
                :accept ".json"
                :style {:display "none"}
                :on-change #(when-let [file (-> % .-target .-files (aget 0))]
                             (handle-import-file! file)
                             (set! (-> % .-target .-value) ""))}]
       [:button {:on-click #(.click (-> % .-target .-previousSibling))
                 :aria-label "Import exercise library from JSON file"}
        "Import"]]
      [:button {:on-click #(update-ui! {:show-edit-exercise true
                                        :edit-exercise-name ""
                                        :edit-exercise-original-name ""
                                        :edit-exercise-difficulty 1.0
                                        :edit-exercise-equipment ""
                                        :edit-exercise-tags ""
                                        :edit-exercise-enabled true
                                        :edit-exercise-sided false
                                        :edit-exercise-error nil})
                :aria-label "Add new exercise to library"}
       "Add"]
      [:button {:on-click #(when (js/confirm "Reset all data and restore default exercises? This cannot be undone.")
                             (library/clear-library-for-testing!)
                             (update-exercises! (library/load-library)))
                :aria-label "Reset all data to defaults"
                :style {:background "#e74c3c"}}
       "Reset Data"]]]))
;; Exercise Dialog Component
(defn exercise-dialog []
  (let [ui (:ui @app-state)
        show? (:show-edit-exercise ui)]
    
    (when show?
      (let [name (:edit-exercise-name ui "")
            original-name (:edit-exercise-original-name ui "")
            difficulty (:edit-exercise-difficulty ui 1.0)
            equipment-str (:edit-exercise-equipment ui "")
            tags-str (:edit-exercise-tags ui "")
            enabled (:edit-exercise-enabled ui true)
            sided (:edit-exercise-sided ui false)
            error (:edit-exercise-error ui)
            new-equipment-input (:edit-exercise-new-equipment ui "")
            new-tag-input (:edit-exercise-new-tag ui "")
            is-new? (empty? original-name)
            
            ;; Parse current equipment from string to set
            current-equipment (if (empty? equipment-str)
                               #{}
                               (set (map clojure.string/trim (clojure.string/split equipment-str #","))))
            
            ;; Parse current tags from string to set
            current-tags (if (empty? tags-str)
                          #{}
                          (set (map clojure.string/trim (clojure.string/split tags-str #","))))
            
            ;; Get all known equipment types and merge with current equipment
            ;; This ensures newly added equipment types show up immediately
            all-equipment-types (clojure.set/union (library/get-equipment-types) current-equipment)
            
            ;; Get all known tags and merge with current tags
            all-tags (clojure.set/union (library/get-all-tags) current-tags)
            
            ;; Dialog configuration
            title (if is-new? "Add New Exercise" "Edit Exercise")
            button-text "Save"
            
            ;; Close handler
            close-fn #(update-ui! {:show-edit-exercise false
                                   :edit-exercise-new-equipment ""
                                   :edit-exercise-new-tag ""})
            
            ;; Save handler
            save-fn #(let [equipment-vec (if (empty? current-equipment)
                                           []
                                           (vec current-equipment))
                           tags-vec (if (empty? current-tags)
                                     []
                                     (vec current-tags))]
                      (save-exercise! original-name name difficulty equipment-vec tags-vec enabled sided))
            
            ;; Toggle equipment handler
            toggle-equipment-fn (fn [equip]
                                 (let [new-equipment (if (contains? current-equipment equip)
                                                      (disj current-equipment equip)
                                                      (conj current-equipment equip))
                                       new-str (if (empty? new-equipment)
                                                ""
                                                (clojure.string/join ", " (sort new-equipment)))]
                                   (update-ui! {:edit-exercise-equipment new-str})))
            
            ;; Toggle tag handler
            toggle-tag-fn (fn [tag]
                           (let [new-tags (if (contains? current-tags tag)
                                           (disj current-tags tag)
                                           (conj current-tags tag))
                                 new-str (if (empty? new-tags)
                                          ""
                                          (clojure.string/join ", " (sort new-tags)))]
                             (update-ui! {:edit-exercise-tags new-str})))
            
            ;; Add new equipment handler
            add-equipment-fn (fn []
                              (when-not (empty? new-equipment-input)
                                (let [trimmed (clojure.string/trim new-equipment-input)
                                      new-equipment (conj current-equipment trimmed)
                                      new-str (clojure.string/join ", " (sort new-equipment))]
                                  (update-ui! {:edit-exercise-equipment new-str
                                               :edit-exercise-new-equipment ""}))))
            
            ;; Add new tag handler
            add-tag-fn (fn []
                        (when-not (empty? new-tag-input)
                          (let [trimmed (clojure.string/trim new-tag-input)
                                new-tags (conj current-tags trimmed)
                                new-str (clojure.string/join ", " (sort new-tags))]
                            (update-ui! {:edit-exercise-tags new-str
                                         :edit-exercise-new-tag ""}))))]
        
        [:div.modal-overlay {:on-click close-fn
                             :role "dialog"
                             :aria-modal "true"
                             :aria-labelledby "exercise-dialog-title"}
         [:div.modal-content {:on-click #(.stopPropagation %)}
          [:h2#exercise-dialog-title title]
          
          (when error
            [:div.error-message {:role "alert" :aria-live "assertive"} error])
          
          [:div.form-group
           [:label {:for "exercise-name"} "Exercise Name:"]
           [:input {:type "text"
                    :id "exercise-name"
                    :value name
                    :placeholder "e.g., Push-ups"
                    :aria-required "true"
                    :ref (fn [el] (when el (.focus el)))
                    :on-change #(update-ui! {:edit-exercise-name (-> % .-target .-value)})
                    :on-key-press #(when (= (.-key %) "Enter") (save-fn))}]]
          
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
                    :on-change #(update-ui! {:edit-exercise-difficulty (js/parseFloat (-> % .-target .-value))})}]]
          
          [:div.form-group
           [:label "Equipment (select all that apply):"]
           [:div.equipment-checkboxes {:role "group" :aria-label "Equipment selection"}
            (for [equipment (sort all-equipment-types)]
              ^{:key equipment}
              [:label.equipment-checkbox
               [:input {:type "checkbox"
                        :checked (contains? current-equipment equipment)
                        :on-change #(toggle-equipment-fn equipment)}]
               [:span equipment]])]
           
           ;; Add new equipment type
           [:div.add-equipment {:style {:margin-top "10px" :display "flex" :gap "8px"}}
            [:input {:type "text"
                     :value new-equipment-input
                     :placeholder "Add new equipment type"
                     :style {:flex "1"}
                     :on-change #(update-ui! {:edit-exercise-new-equipment (-> % .-target .-value)})
                     :on-key-press #(when (= (.-key %) "Enter") 
                                     (.preventDefault %)
                                     (add-equipment-fn))}]
            [:button {:on-click #(do
                                   (.preventDefault %)
                                   (.stopPropagation %)
                                   (add-equipment-fn))
                      :disabled (empty? new-equipment-input)
                      :style {:min-width "60px"}}
             "Add"]]]
          
          ;; Type tags section
          [:div.form-group
           [:label "Type:"]
           [:div.equipment-checkboxes {:role "group" :aria-label "Exercise type selection"}
            (let [type-tags #{"cardio" "strength" "flexibility" "balance" "plyometric" 
                              "low-impact" "high-impact"}
                  type-tag-list (sort (filter #(contains? type-tags %) all-tags))]
              (for [tag type-tag-list]
                ^{:key tag}
                [:label.equipment-checkbox
                 [:input {:type "checkbox"
                          :checked (contains? current-tags tag)
                          :on-change #(toggle-tag-fn tag)}]
                 [:span tag]]))]]
          
          ;; Muscle group tags section
          [:div.form-group
           [:label "Muscle Groups:"]
           [:div.equipment-checkboxes {:role "group" :aria-label "Muscle group selection"}
            (let [type-tags #{"cardio" "strength" "flexibility" "balance" "plyometric" 
                              "low-impact" "high-impact"}
                  muscle-tag-list (sort (filter #(not (contains? type-tags %)) all-tags))]
              (for [tag muscle-tag-list]
                ^{:key tag}
                [:label.equipment-checkbox
                 [:input {:type "checkbox"
                          :checked (contains? current-tags tag)
                          :on-change #(toggle-tag-fn tag)}]
                 [:span tag]]))]]
          
          ;; Add new tag
          [:div.form-group
           [:label "Add Muscle Group:"]
           [:div.add-equipment {:style {:display "flex" :gap "8px"}}
            [:input {:type "text"
                     :value new-tag-input
                     :placeholder "Add new muscle group"
                     :style {:flex "1"}
                     :on-change #(update-ui! {:edit-exercise-new-tag (-> % .-target .-value)})
                     :on-key-press #(when (= (.-key %) "Enter") 
                                     (.preventDefault %)
                                     (add-tag-fn))}]
            [:button {:on-click #(do
                                   (.preventDefault %)
                                   (.stopPropagation %)
                                   (add-tag-fn))
                      :disabled (empty? new-tag-input)
                      :style {:min-width "60px"}}
             "Add"]]]
          
          [:div.form-group
           [:label.equipment-checkbox
            [:input {:type "checkbox"
                     :checked enabled
                     :on-change #(update-ui! {:edit-exercise-enabled (-> % .-target .-checked)})}]
            [:span "Include in sessions"]]]
          
          [:div.form-group
           [:label.equipment-checkbox
            [:input {:type "checkbox"
                     :checked sided
                     :on-change #(update-ui! {:edit-exercise-sided (-> % .-target .-checked)})}]
            [:span "Single-sided"]]]
          
          [:div.modal-actions
           [:button {:on-click save-fn
                     :aria-label (if is-new? "Add exercise to library" "Save exercise changes")}
            button-text]
           [:button {:on-click close-fn
                     :aria-label "Cancel and close dialog"}
            "Cancel"]
           ;; Search button (only show when there's a name)
           (when-not (empty? name)
             [:button {:on-click #(let [search-query (str "how to do " name " exercises")
                                        search-url (str "https://www.google.com/search?q=" (js/encodeURIComponent search-query))]
                                    (when (exists? js/window)
                                      (js/window.open search-url "_blank")))
                       :aria-label (str "Search for " name " instructions")
                       :style {:background "#3498db"}}
              "Search"])
           ;; Delete button (only show when editing existing exercise)
           (when-not is-new?
             [:button.delete-btn {:on-click #(when (js/confirm (str "Delete '" original-name "'?"))
                                               (library/delete-exercise! original-name)
                                               (update-exercises! (library/load-library))
                                               (update-ui! {:show-edit-exercise false}))
                                  :aria-label (str "Delete " original-name)
                                  :style {:background "#e74c3c"
                                          :margin-left "auto"}}
              "Delete"])]]]))))
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

;; Disclaimer Dialog Component
(defn disclaimer-dialog []
  (let [ui (:ui @app-state)
        show? (:show-disclaimer ui)]
    (when show?
      [:div.modal-overlay {:role "dialog"
                           :aria-modal "true"
                           :aria-labelledby "disclaimer-dialog-title"}
       [:div.modal-content.disclaimer-dialog {:on-click #(.stopPropagation %)}
        [:h2#disclaimer-dialog-title "⚠️ Disclaimer"]
        
        [:div.disclaimer-text
         [:p "By using this exercise timer application, you acknowledge and agree that:"]
         [:ul
          [:li "Exercise involves physical exertion and carries inherent risks of injury."]
          [:li "You should consult with a healthcare professional before beginning any exercise program."]
          [:li "You are responsible for your own safety and should stop immediately if you experience pain, dizziness, or discomfort."]
          [:li "The creator of this application assumes no liability for any injuries or damages resulting from your use of this application."]]
         [:p [:strong "Use this application at your own risk."]]]
        
        [:div.modal-actions
         [:button {:on-click (fn []
                                ;; Store acceptance in localStorage
                                (.setItem js/localStorage "disclaimer-accepted" "true")
                                ;; Close disclaimer
                                (update-ui! {:show-disclaimer false})
                                ;; Start the session
                                (let [all-exercises (:exercises @app-state)
                                      enabled-exercises (vec (filter #(:enabled % true) all-exercises))
                                      session-config (:session-config @app-state)
                                      duration (get-in @app-state [:ui :session-duration-minutes])
                                      equipment (:equipment session-config)
                                      excluded-tags (:excluded-tags session-config)
                                      config (session/make-session-config duration equipment excluded-tags)
                                      session-plan (session/generate-session config enabled-exercises)]
                                  (update-current-session! session-plan)
                                  (timer/initialize-session! session-plan)
                                  (update-timer-state! (timer/get-state))
                                  (speech/reset-announcement-tracking!)
                                  (wakelock/request-wake-lock!)))
                   :aria-label "Accept disclaimer and start session"
                   :style {:background "#27ae60"}}
          "I Understand - Start Workout"]
         [:button {:on-click #(update-ui! {:show-disclaimer false})
                   :aria-label "Cancel and close disclaimer"}
          "Cancel"]]]])))

;; ============================================================================
;; Root Component
;; ============================================================================

(defn app []
  (let [session (:current-session @app-state)
        progress-pct (if session (timer/calculate-progress-percentage) 0)]
    [:div.app-container
     [:header.session-header {:class (when session "active")}
      [:div.progress-fill {:style (when session {:width (str progress-pct "%")})}]
      [:h1 "Sweat Roulette"]
      (when session
        (let [timer-state (:timer-state @app-state)
              total-duration (:total-duration-seconds session)
              elapsed (:total-elapsed-seconds timer-state 0)
              formatted-elapsed (format/seconds-to-mm-ss elapsed)
              formatted-total (format/seconds-to-mm-ss total-duration)]
          [:div.session-timer (str formatted-elapsed " / " formatted-total)]))]
     
     [:main#main-content
      (if session
        ;; Active session view - show only the workout
        [:div.session-area
         [:div.active-session
          [exercise-display]
          [timer-display]
          [control-panel]
          [completion-screen]]]
        
        ;; Setup view - show configuration and library
        [:div.setup-view
         [:div.session-area
          [configuration-panel]]
         [:div.library-area
          [exercise-library-panel]]])]
     
     ;; Footer with version
     [:footer.app-footer
      [:div.version (str "Version: " (version/get-version-string))]]
     
     ;; Modals
     [exercise-dialog]
     [import-conflict-dialog]
     [disclaimer-dialog]]))

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
       (speech/speak-completion!))))
  
  ;; Announce "Switch sides" at halfway point for sided exercises
  (timer/on-switch-sides
   (fn []
     ;; Announce switch sides if speech enabled
     (when (get-in @app-state [:ui :speech-enabled])
       (speech/speak! "Switch sides" {:rate 1.0 :lang "en-GB"})))))

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
