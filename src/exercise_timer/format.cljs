(ns exercise-timer.format
  "Formatting utilities for display")

;; ============================================================================
;; Time Formatting
;; ============================================================================

(defn seconds-to-mm-ss
  "Convert seconds to MM:SS format with zero-padding.
   
   Parameters:
   - seconds: non-negative integer representing seconds
   
   Returns:
   - String in MM:SS format (e.g., \"05:30\", \"00:05\", \"12:45\")
   
   Examples:
   - (seconds-to-mm-ss 0) => \"00:00\"
   - (seconds-to-mm-ss 5) => \"00:05\"
   - (seconds-to-mm-ss 65) => \"01:05\"
   - (seconds-to-mm-ss 3661) => \"61:01\"
   
   Validates: Requirements 4.5"
  [seconds]
  {:pre [(>= seconds 0)]}
  (let [minutes (quot seconds 60)
        remaining-seconds (rem seconds 60)
        pad (fn [n] (if (< n 10) (str "0" n) (str n)))]
    (str (pad minutes) ":" (pad remaining-seconds))))
