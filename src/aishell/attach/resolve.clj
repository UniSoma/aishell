(ns aishell.attach.resolve
  "Pure resolver for a bare `aishell attach`: picks the container to attach
   to from the project's container listing, or explains why it cannot.

   Candidates are exactly what `aishell ps` shows, filtered to running.
   Bootstrap state does not gate the pick — explicit attach ignores it, so
   inference must not be stricter."
  (:require [clojure.string :as str]
            [aishell.docker.bootstrap :as bootstrap]
            [aishell.docker.naming :as naming]))

(defn- pad
  [s width]
  (str s (str/join (repeat (- width (count s)) \space))))

(defn- none-running-error
  "Stopped containers are named when there are any. The Docker hint is
   there because the listing swallows exceptions and returns an empty
   vector, so a dead daemon looks the same as an empty project."
  [stopped]
  (str "No running containers in this project.\n\n"
       "To start one:  aishell claude\n\n"
       (when (seq stopped)
         (str "Stopped: " (str/join ", " stopped) "\n"))
       "If this is unexpected, check Docker: aishell check"))

(defn- ambiguous-error
  [candidates]
  (let [width (+ 4 (apply max (map (comp count :name) candidates)))]
    (str "Multiple running containers — name one:\n\n"
         (str/join (for [{:keys [name status]} candidates]
                     (str "  " (pad name width) status "\n")))
         "\n  aishell attach " (:name (first candidates)))))

(defn resolve-attach-target
  "Pick the container a bare `aishell attach` should target.

   Takes the raw container vector from `list-project-containers` (maps with
   :name and :status) and returns {:name <short name>} when exactly one is
   running, or {:error <message>} otherwise. Listing order is preserved, so
   the error reads in the same order as `aishell ps`."
  [containers]
  (let [rows (map (fn [c] {:name (naming/extract-short-name (:name c))
                           :status (:status c)})
                  containers)
        {running true stopped false} (group-by #(bootstrap/running? (:status %)) rows)]
    (case (count running)
      1 {:name (:name (first running))}
      0 {:error (none-running-error (map :name stopped))}
      {:error (ambiguous-error running)})))
