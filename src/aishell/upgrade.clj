(ns aishell.upgrade
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [aishell.output :as output]))

(def github-repo "UniSoma/aishell")
(def releases-url (str "https://github.com/" github-repo "/releases"))

(defn find-downloader
  "Returns :curl or :wget based on what's available, or nil."
  []
  (cond
    (fs/which "curl") :curl
    (fs/which "wget") :wget
    :else nil))

(defn download-to-file
  "Download URL to dest file using curl or wget."
  [downloader url dest]
  (case downloader
    :curl (p/shell {:out :string :err :string}
                   "curl" "-fsSL" "-o" (str dest) url)
    :wget (p/shell {:out :string :err :string}
                   "wget" "-q" "-O" (str dest) url)))

(defn fetch-latest-version
  "Fetch latest release version from GitHub by following the /releases/latest redirect.
   Returns version string like \"3.3.0\" (without v prefix), or nil on failure."
  [downloader]
  (let [url (str releases-url "/latest")]
    (try
      (case downloader
        :curl
        (let [result (p/shell {:out :string :err :string :continue true}
                              "curl" "-fsSLI" "-o" "/dev/null"
                              "-w" "%{url_effective}" url)
              effective-url (str/trim (:out result))]
          (when (and (zero? (:exit result))
                     (str/includes? effective-url "/tag/v"))
            (subs effective-url (+ (str/last-index-of effective-url "/v") 2))))

        :wget
        (let [result (p/shell {:out :string :err :string :continue true}
                              "wget" "--spider" "-S" "--max-redirect=5" url)
              ;; wget prints headers to stderr
              stderr (:err result)
              lines (str/split-lines stderr)
              location-line (last (filter #(str/includes? % "Location:") lines))]
          (when (and location-line (str/includes? location-line "/tag/v"))
            (let [loc (str/trim (subs location-line (+ (str/index-of location-line "Location:") 9)))]
              (subs loc (+ (str/last-index-of loc "/v") 2))))))
      (catch Exception _
        nil))))

(defn compute-sha256-file
  "Compute SHA-256 hash of a file, returning 64-character hex string."
  [file-path]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bytes (.digest md (fs/read-all-bytes file-path))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn parse-semver
  "Parse \"X.Y.Z\" into [x y z] ints. Returns nil if invalid."
  [version-str]
  (when version-str
    (let [;; Strip leading v if present
          v (if (str/starts-with? version-str "v")
              (subs version-str 1)
              version-str)
          ;; Strip prerelease/build metadata for comparison
          base (first (str/split v #"[-+]"))
          parts (str/split base #"\.")]
      (when (= 3 (count parts))
        (try
          (mapv #(Integer/parseInt %) parts)
          (catch Exception _ nil))))))

(defn version-compare
  "Compare two version strings. Returns negative if a < b, 0 if equal, positive if a > b."
  [a b]
  (compare (parse-semver a) (parse-semver b)))

(defn find-aishell-path
  "Find the installed aishell script path."
  []
  (or (fs/which "aishell")
      (let [home (System/getProperty "user.home")
            fallback (str home "/.local/bin/aishell")]
        (when (fs/exists? fallback)
          fallback))))

(defn do-upgrade
  "Main upgrade entry point.
   current-version: current aishell version string (e.g. \"3.3.0\")
   target-version: specific version to upgrade to, or nil for latest"
  [current-version target-version]
  (let [downloader (find-downloader)]
    ;; 1. Check for downloader
    (when-not downloader
      (output/error "Neither curl nor wget found. Install one to use upgrade."))

    ;; 2. Resolve target version
    (let [target (or target-version
                     (do
                       (println "Checking for latest version...")
                       (fetch-latest-version downloader)))]
      (when-not target
        (output/error "Could not determine latest version from GitHub.\nCheck your internet connection or specify a version: aishell upgrade <VERSION>"))

      ;; 3. Check if already up to date
      (when (and (not target-version) (= current-version target))
        (println (str "Already up to date (v" current-version ")."))
        (System/exit 0))

      ;; 4. Check for downgrade
      (when (neg? (version-compare target current-version))
        (output/warn (str "Downgrading from v" current-version " to v" target)))

      ;; 5. Find aishell binary
      (let [aishell-path (find-aishell-path)]
        (when-not aishell-path
          (output/error "Could not find aishell installation path.\nReinstall using: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash"))

        ;; 6. Check write permission
        (when-not (fs/writable? aishell-path)
          (output/error (str "No write permission to " aishell-path "\nTry running with sudo or fix permissions.")))

        (println (str "Upgrading aishell: v" current-version " -> v" target))

        ;; 7. Download to temp dir
        (let [tmp-dir (fs/create-temp-dir {:prefix "aishell-upgrade-"})
              script-url (str releases-url "/download/v" target "/aishell")
              checksum-url (str releases-url "/download/v" target "/aishell.sha256")
              tmp-script (str tmp-dir "/aishell")
              tmp-checksum (str tmp-dir "/aishell.sha256")]
          (try
            ;; Download script
            (try
              (download-to-file downloader script-url tmp-script)
              (catch Exception _
                (output/error (str "Failed to download aishell v" target
                                   "\nRelease may not exist: " releases-url "/tag/v" target))))

            ;; Download checksum
            (try
              (download-to-file downloader checksum-url tmp-checksum)
              (catch Exception _
                (output/error (str "Failed to download checksum for v" target))))

            ;; 8. Verify checksum
            (let [expected-hash (first (str/split (str/trim (slurp (str tmp-checksum))) #"\s+"))
                  actual-hash (compute-sha256-file tmp-script)]
              (when (not= expected-hash actual-hash)
                (output/error (str "Checksum verification failed!\n"
                                   "  Expected: " expected-hash "\n"
                                   "  Got:      " actual-hash "\n"
                                   "Download may be corrupted. Try again."))))

            ;; 9. Replace binary (atomic move with fallback)
            (try
              (fs/move tmp-script aishell-path {:replace-existing true})
              (catch Exception _
                ;; Cross-filesystem fallback: copy + delete
                (fs/copy tmp-script aishell-path {:replace-existing true})
                (fs/delete tmp-script)))

            ;; 10. chmod +x on Unix
            (when-not (fs/windows?)
              (p/shell {:out :string :err :string}
                       "chmod" "+x" (str aishell-path)))

            ;; 11. On Windows, also replace .bat
            (when (fs/windows?)
              (let [bat-url (str releases-url "/download/v" target "/aishell.bat")
                    bat-path (str aishell-path ".bat")
                    tmp-bat (str tmp-dir "/aishell.bat")]
                (try
                  (download-to-file downloader bat-url tmp-bat)
                  (try
                    (fs/move tmp-bat bat-path {:replace-existing true})
                    (catch Exception _
                      (fs/copy tmp-bat bat-path {:replace-existing true})
                      (fs/delete tmp-bat)))
                  (catch Exception e
                    (output/warn (str "Could not update aishell.bat: " (.getMessage e)))))))

            ;; 12. Success
            (println (str output/BOLD "Upgraded aishell: v" current-version " -> v" target output/NC))

            (finally
              ;; Cleanup temp dir
              (fs/delete-tree tmp-dir))))))))
