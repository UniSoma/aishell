(ns aishell.detection.patterns
  "Pattern definitions and matching functions for sensitive files."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- in-excluded-dir?
  "Check if a path is within an excluded directory."
  [path excluded-dirs]
  (let [path-str (str path)]
    (some #(str/includes? path-str (str "/" % "/")) excluded-dirs)))

(defn- case-insensitive-basename-match?
  "Check if filename matches pattern (case-insensitive)."
  [path pattern-lower]
  (let [name-lower (str/lower-case (str (fs/file-name path)))]
    (or (= name-lower pattern-lower)
        (str/starts-with? name-lower (str pattern-lower ".")))))

(defn detect-env-files
  "Detect .env files (medium severity) and templates (low severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        env-files (filter (fn [path]
                           (let [name-lower (str/lower-case (str (fs/file-name path)))]
                             (or (= name-lower ".env")
                                 (str/starts-with? name-lower ".env.")
                                 (= name-lower ".envrc"))))
                         filtered)]
    (for [path env-files]
      (let [name-lower (str/lower-case (str (fs/file-name path)))
            is-template? (or (str/includes? name-lower "example")
                            (str/includes? name-lower "sample"))]
        {:path (str path)
         :type (if is-template? :env-template :env-file)
         :severity (if is-template? :low :medium)
         :reason (if is-template?
                   "Environment template file"
                   "Environment configuration file")}))))

(defn group-findings
  "Group findings by type and apply threshold-of-3 summarization.
   Returns seq of findings (individual or summary)."
  [findings]
  (let [by-type (group-by :type findings)]
    (mapcat
      (fn [[type group]]
        (if (<= (count group) 3)
          ;; Show individually
          group
          ;; Summarize with sample paths
          [{:type type
            :severity (:severity (first group))
            :path nil
            :reason (str (count group) " files detected")
            :summary? true
            :sample-paths (take 2 (map :path group))}]))
      by-type)))
