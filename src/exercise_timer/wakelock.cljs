(ns exercise-timer.wakelock)

;; ============================================================================
;; Screen Wake Lock Management
;; ============================================================================

(defonce wake-lock-state (atom nil))

(defn wake-lock-supported?
  "Check if the Screen Wake Lock API is supported in this browser.
   
   Returns:
   - true if supported, false otherwise"
  []
  (and (exists? js/navigator)
       (exists? js/navigator.wakeLock)))

(defn request-wake-lock!
  "Request a screen wake lock to prevent the screen from turning off.
   
   Side effects:
   - Requests wake lock from browser
   - Stores wake lock in wake-lock-state atom
   - Logs success or failure
   
   Returns:
   - {:ok true} on success
   - {:error \"message\"} on failure"
  []
  (if (wake-lock-supported?)
    (-> (js/navigator.wakeLock.request "screen")
        (.then (fn [lock]
                 (reset! wake-lock-state lock)
                 (js/console.log "Screen wake lock activated")
                 {:ok true}))
        (.catch (fn [err]
                  (js/console.warn "Failed to acquire wake lock:" (.-message err))
                  {:error (.-message err)})))
    (do
      (js/console.warn "Screen Wake Lock API not supported")
      {:error "Wake Lock API not supported"})))

(defn release-wake-lock!
  "Release the current screen wake lock.
   
   Side effects:
   - Releases wake lock if active
   - Clears wake-lock-state atom
   - Logs release
   
   Returns:
   - {:ok true} if released or no lock active
   - {:error \"message\"} on failure"
  []
  (if-let [lock @wake-lock-state]
    (try
      (.release lock)
      (reset! wake-lock-state nil)
      (js/console.log "Screen wake lock released")
      {:ok true}
      (catch js/Error e
        (js/console.warn "Failed to release wake lock:" (.-message e))
        {:error (.-message e)}))
    {:ok true}))

(defn wake-lock-active?
  "Check if a wake lock is currently active.
   
   Returns:
   - true if wake lock is active, false otherwise"
  []
  (and @wake-lock-state
       (not (.-released @wake-lock-state))))
