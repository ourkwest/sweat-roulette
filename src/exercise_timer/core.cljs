(ns exercise-timer.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; Global app state atom - will be implemented in task 8
(defonce app-state
  (r/atom
    {:exercises []
     :current-session nil
     :timer-state {:current-exercise-index 0
                   :remaining-seconds 0
                   :session-state :not-started}
     :ui {:show-add-exercise false
          :show-import-dialog false
          :import-conflicts nil}}))

;; Root component - will be implemented in task 10
(defn app []
  [:div
   [:h1 "Exercise Timer App"]
   [:p "Application structure initialized. UI components will be implemented in later tasks."]])

;; Initialize the app
(defn init! []
  (rdom/render [app]
               (.getElementById js/document "app")))

;; Hot reload support
(defn ^:dev/after-load reload! []
  (init!))
