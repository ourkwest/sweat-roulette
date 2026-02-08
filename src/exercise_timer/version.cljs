(ns exercise-timer.version)

;; This will be replaced by the build script in production
(goog-define VERSION "dev")
(goog-define BUILD_TIME "")

(defn get-version-string
  "Get the version string for display.
   
   Returns:
   - 'dev' in development
   - 'v{git-hash} ({build-time})' in production"
  []
  (if (= VERSION "dev")
    "dev"
    (str "v" VERSION (when (not-empty BUILD_TIME) (str " (" BUILD_TIME ")")))))
