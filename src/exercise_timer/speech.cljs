(ns exercise-timer.speech
  "Text-to-speech utilities using Web Speech API")

;; ============================================================================
;; Speech Synthesis
;; ============================================================================

(defn speech-available?
  "Check if speech synthesis is available in the browser.
   
   Returns:
   - true if speechSynthesis is available, false otherwise"
  []
  (and (exists? js/window)
       (exists? js/window.speechSynthesis)))

(defn speak!
  "Speak the given text using the Web Speech API.
   
   Parameters:
   - text: string to speak
   - options: optional map with :rate, :pitch, :volume, :lang
   
   Options:
   - :rate (0.1 to 10, default 1.0) - speed of speech
   - :pitch (0 to 2, default 1.0) - pitch of voice
   - :volume (0 to 1, default 1.0) - volume
   - :lang (string, default 'en-US') - language code
   
   Returns:
   - true if speech was initiated, false if not available
   
   Example:
   (speak! \"Push-ups\" {:rate 0.9 :volume 0.8})"
  ([text]
   (speak! text {}))
  ([text {:keys [rate pitch volume lang]
          :or {rate 1.0 pitch 1.0 volume 1.0 lang "en-US"}}]
   (when (speech-available?)
     (try
       (let [utterance (js/SpeechSynthesisUtterance. text)]
         (set! (.-rate utterance) rate)
         (set! (.-pitch utterance) pitch)
         (set! (.-volume utterance) volume)
         (set! (.-lang utterance) lang)
         (.speak js/window.speechSynthesis utterance)
         true)
       (catch js/Error e
         (js/console.error "Speech synthesis error:" e)
         false)))))

(defn cancel-speech!
  "Cancel any ongoing speech.
   
   Returns:
   - true if cancelled, false if not available"
  []
  (when (speech-available?)
    (.cancel js/window.speechSynthesis)
    true))

(defn speaking?
  "Check if speech is currently in progress.
   
   Returns:
   - true if speaking, false otherwise"
  []
  (and (speech-available?)
       (.-speaking js/window.speechSynthesis)))

;; ============================================================================
;; Exercise Timer Speech Helpers
;; ============================================================================

(defn speak-exercise-name!
  "Speak the name of an exercise.
   
   Parameters:
   - exercise-name: string name of the exercise
   
   Example:
   (speak-exercise-name! \"Push-ups\")"
  [exercise-name]
  (speak! exercise-name {:rate 0.9 :lang "en-GB"}))

(defn format-time-text
  "Format seconds into natural language text without speaking it.
   
   Parameters:
   - seconds: number of seconds
   
   Returns:
   - String representation of the time
   
   Examples:
   - 65 -> 'one minute five seconds'
   - 30 -> 'thirty seconds'
   - 10 -> 'ten'"
  [seconds]
  (let [minutes (quot seconds 60)
        secs (rem seconds 60)]
    (cond
      ;; Last 10 seconds: just say the number
      (<= seconds 10)
      (str seconds)
      
      ;; Less than a minute: say seconds
      (< seconds 60)
      (str seconds " seconds")
      
      ;; Exactly on the minute
      (zero? secs)
      (if (= minutes 1)
        "one minute"
        (str minutes " minutes"))
      
      ;; Minutes and seconds
      :else
      (str (if (= minutes 1) "one minute" (str minutes " minutes"))
           " "
           (if (= secs 1) "one second" (str secs " seconds"))))))

(defn speak-exercise-start!
  "Speak the exercise name and duration together.
   
   Parameters:
   - exercise-name: string name of the exercise
   - duration-seconds: number of seconds for the exercise
   
   Example:
   (speak-exercise-start! \"Push-ups\" 30)
   -> Speaks: 'Push-ups, thirty seconds'"
  [exercise-name duration-seconds]
  (let [time-text (format-time-text duration-seconds)
        full-text (str exercise-name ", " time-text)]
    (speak! full-text {:rate 0.9 :lang "en-GB"})))

(defn speak-time-remaining!
  "Speak the remaining time in a natural way.
   
   Parameters:
   - seconds: number of seconds remaining
   
   Examples:
   - 65 seconds -> 'One minute five seconds'
   - 30 seconds -> 'Thirty seconds'
   - 10 seconds -> 'Ten'
   - 5 seconds -> 'Five'"
  [seconds]
  (let [text (format-time-text seconds)]
    (speak! text {:rate 1.0 :lang "en-GB"})))

(defn speak-completion!
  "Speak a completion message.
   
   Example:
   (speak-completion!)"
  []
  (speak! "Workout complete! Great job!" {:rate 0.9 :pitch 1.1 :lang "en-GB"}))

;; ============================================================================
;; Timer Integration
;; ============================================================================

;; Track the last time we announced the time (for 10-second intervals)
(defonce ^:private last-announcement-time
  (atom 0))

(defn reset-announcement-tracking!
  "Reset the announcement tracking for a new session.
   Call this when starting a new session to ensure announcements work correctly.
   
   Returns:
   - nil"
  []
  (reset! last-announcement-time 0))

(defn should-announce-time?
  "Check if we should announce the time based on 10-second intervals.
   Announces at 10, 20, 30, 40, 50, 60, etc.
   
   Parameters:
   - seconds: current remaining seconds
   
   Returns:
   - true if we should announce (at 10-second multiples), false otherwise"
  [seconds]
  ;; Announce when seconds is a multiple of 10 (10, 20, 30, etc.)
  ;; and we haven't announced this exact value yet
  (when (and (pos? seconds)
             (or (zero? (rem seconds 10))
                 (#{1 2 3} seconds))
             (not= @last-announcement-time seconds))
    (reset! last-announcement-time seconds)
    true))

;(defn setup-speech-callbacks!
;  "Set up speech callbacks for the timer.
;   This should be called after timer callbacks are initialized.
;   
;   Features:
;   - Announces exercise name when changing exercises
;   - Announces time remaining every 10 seconds
;   - Announces completion
;   
;   Parameters:
;   - timer-ns: the timer namespace (exercise-timer.timer)
;   
;   Example:
 ;  (setup-speech-callbacks! timer)"
;  [timer-ns]
;  (when (speech-available?)
;    ;; Announce exercise name on change
;    (.on-exercise-change timer-ns
;                         (fn [_index]
;                           ;; Get the current exercise name from app state
;                           ;; This would need to be passed in or accessed from app state
;                           (js/console.log "Exercise changed - would announce here")))
;    
;    ;; Announce time every 10 seconds
;    (.on-tick timer-ns
 ;             (fn [remaining]
 ;               (when (should-announce-time? remaining)
 ;                 (speak-time-remaining! remaining))))
 ;   
 ;   ;; Announce completion
 ;   (.on-complete timer-ns
 ;                 (fn []
 ;                   (speak-completion!)))))
