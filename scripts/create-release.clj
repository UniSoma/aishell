#!/usr/bin/env bb

;; Create GitHub release with assets
;; Usage: ./scripts/create-release.clj
;;
;; Runs the host platform's binary to read the version, then creates the
;; GitHub release with tag v{version} and every built asset.
;; Idempotent: safe to run multiple times (skips if release exists)
;;
;; Prerequisites:
;;   - a full ./scripts/build-release.clj run, so dist/ holds the five platform
;;     binaries, SHA256SUMS and the legacy trio
;;   - gh CLI must be authenticated

(ns create-release
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def dist-dir "dist")

(def platform-binaries
  {"linux-amd64"   "aishell-linux-amd64"
   "linux-aarch64" "aishell-linux-aarch64"
   "macos-amd64"   "aishell-macos-amd64"
   "macos-aarch64" "aishell-macos-aarch64"
   "windows-amd64" "aishell-windows-amd64.exe"})

;; Bridging release only - keep in step with legacy-assets? in
;; scripts/build-release.clj, and drop both in 4.2.0.
(def legacy-assets? true)

(def legacy-trio ["aishell" "aishell.bat" "aishell.sha256"])

(defn dist-path [asset]
  (str dist-dir "/" asset))

(defn release-assets []
  (map dist-path
       (concat (map platform-binaries
                    ["linux-amd64" "linux-aarch64" "macos-amd64" "macos-aarch64" "windows-amd64"])
               ["SHA256SUMS"]
               (when legacy-assets? legacy-trio))))

(defn host-binary
  "The dist binary this machine can run, used to read the version."
  []
  (let [os (cond
             (fs/windows?) "windows"
             (str/starts-with? (System/getProperty "os.name") "Mac OS X") "macos"
             :else "linux")
        arch (case (System/getProperty "os.arch")
               ("amd64" "x86_64") "amd64"
               ("aarch64" "arm64") "aarch64"
               nil)]
    (some-> (get platform-binaries (str os "-" arch)) dist-path)))

(defn exit [code msg]
  (binding [*out* (if (zero? code) *out* *err*)]
    (println msg))
  (System/exit code))

(defn check-files-exist []
  (doseq [asset (release-assets)]
    (when-not (.exists (java.io.File. ^String asset))
      (exit 1 (str "Error: " asset " not found. Run ./scripts/build-release.clj first.")))))

(defn get-version []
  (let [dist-binary (or (host-binary)
                        (exit 1 (str "Error: no dist binary for this platform ("
                                     (System/getProperty "os.name") "/"
                                     (System/getProperty "os.arch") ")")))]
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
        (exit 1 (str "Error: Failed to get version from " dist-binary ": " (.getMessage e)))))))

(defn release-exists? [tag]
  (try
    (let [result (p/shell {:continue true :out :string :err :string}
                          "gh" "release" "view" tag)]
      (zero? (:exit result)))
    (catch Exception _
      false)))

(defn create-release [tag]
  (println (str "Creating release " tag "..."))
  (try
    (apply p/shell "gh" "release" "create" tag
           (concat (release-assets) ["--title" tag "--generate-notes"]))
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
