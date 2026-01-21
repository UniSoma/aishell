#!/usr/bin/env bb

;; Create GitHub release with assets
;; Usage: ./scripts/create-release.clj
;;
;; Extracts version from dist/aishell, creates GitHub release with tag v{version}
;; Idempotent: safe to run multiple times (skips if release exists)
;;
;; Prerequisites:
;;   - dist/aishell must exist (run ./scripts/build-release.clj first)
;;   - dist/aishell.sha256 must exist
;;   - gh CLI must be authenticated

(ns create-release
  (:require [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def dist-binary "dist/aishell")
(def dist-checksum "dist/aishell.sha256")

(defn exit [code msg]
  (binding [*out* (if (zero? code) *out* *err*)]
    (println msg))
  (System/exit code))

(defn check-files-exist []
  (when-not (.exists (java.io.File. dist-binary))
    (exit 1 (str "Error: " dist-binary " not found. Run ./scripts/build-release.clj first.")))
  (when-not (.exists (java.io.File. dist-checksum))
    (exit 1 (str "Error: " dist-checksum " not found. Run ./scripts/build-release.clj first."))))

(defn get-version []
  (try
    (let [result (p/shell {:out :string :err :string}
                          dist-binary "--version" "--json")
          output (:out result)
          parsed (json/parse-string output true)
          version (:version parsed)]
      (when (str/blank? version)
        (exit 1 "Error: Failed to extract version from aishell binary"))
      version)
    (catch Exception e
      (exit 1 (str "Error: Failed to get version from " dist-binary ": " (.getMessage e))))))

(defn release-exists? [tag]
  (try
    (let [result (p/shell {:continue true :out :string :err :string}
                          "gh" "release" "view" tag)]
      (zero? (:exit result)))
    (catch Exception e
      false)))

(defn create-release [tag]
  (println (str "Creating release " tag "..."))
  (try
    (p/shell "gh" "release" "create" tag
             dist-binary
             dist-checksum
             "--title" tag
             "--generate-notes")
    (println)
    (println (str "Release " tag " created successfully!"))
    (println (str "View at: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/" tag))
    (catch Exception e
      (exit 1 (str "Error: Failed to create release: " (.getMessage e))))))

(defn main []
  (println "GitHub Release Automation")
  (println "=========================")
  (println)

  ;; Pre-flight checks
  (check-files-exist)

  ;; Extract version and construct tag
  (let [version (get-version)
        tag (str "v" version)]
    (println (str "Version: " version))
    (println (str "Tag: " tag))
    (println)

    ;; Check if release already exists
    (if (release-exists? tag)
      (do
        (println (str "Release " tag " already exists."))
        (println "Nothing to do.")
        (System/exit 0))

      ;; Create new release
      (create-release tag))))

(main)
