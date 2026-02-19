(ns build-hooks
  (:require [clojure.java.io :as io]))

(defn copy-file [source-path dest-path]
  "Copy a file from source to destination, creating parent directories if needed."
  (let [source (io/file source-path)
        dest (io/file dest-path)]
    (io/make-parents dest)
    (io/copy source dest)))

(defn copy-static-assets
  "Build hook to copy static assets from src/public to .dev-build
   This runs before each compilation to ensure static files are up to date."
  {:shadow.build/stage :compile-prepare}
  [build-state & args]
  (println "Copying static assets from src/public/ to .dev-build/...")
  (try
    (copy-file "src/public/index.html" ".dev-build/index.html")
    (copy-file "src/public/favicon.svg" ".dev-build/favicon.svg")
    (copy-file "src/public/css/styles.css" ".dev-build/css/styles.css")
    (println "✓ Static assets copied successfully")
    (catch Exception e
      (println "Warning: Failed to copy static assets:" (.getMessage e))))
  build-state)
