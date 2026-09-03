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
        (let [null-dev (if (fs/windows?) "NUL" "/dev/null")
              result (p/shell {:out :string :err :string :continue true}
                              "curl" "-fsSLI" "-o" null-dev
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

(defn checksum-for
  "Look up one asset's hash in a `hash  filename` checksum file.
   Handles the legacy single-line aishell.sha256 and multi-line SHA256SUMS alike.
   The filename token must match exactly; a leading `*` binary marker is ignored.
   Returns the hash lower-cased, or nil when the asset is not listed."
  [sums-text asset-name]
  (when (and sums-text asset-name)
    (some (fn [line]
            (let [[hash name] (str/split (str/trim line) #"\s+" 2)]
              (when (and hash name)
                (let [name (if (str/starts-with? name "*") (subs name 1) name)]
                  (when (= name asset-name)
                    (str/lower-case hash))))))
          (str/split-lines sums-text))))

(defn shebang?
  "True when the given bytes start with `#!`, i.e. the file is a script."
  [^bytes b]
  (boolean (and b
                (>= (alength b) 2)
                (= 0x23 (bit-and (aget b 0) 0xff))
                (= 0x21 (bit-and (aget b 1) 0xff)))))

(defn read-leading-bytes
  "Read at most the first n bytes of a file. Never reads the whole file:
   the installed aishell may be a 90 MB binary."
  [path n]
  (with-open [in (java.io.FileInputStream. (str path))]
    (.readNBytes in n)))

(defn- leading-bytes-or-nil
  "read-leading-bytes, or nil when the file cannot be read.
   An install owned by root is unreadable but still exists; the caller wants
   the plain \"no write permission\" error, not a stack trace."
  [path n]
  (try
    (read-leading-bytes path n)
    (catch Exception _ nil)))

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

(defn upgrade-plan
  "Pure: decide what to fetch, where to put it, and what to clean up.
   Reads only the env map (see detect-env) and returns a plan map that
   execute-plan carries out. May return {:error {:message .. :code ..}} when the
   environment cannot be served."
  [{:keys [os install-path current-version target-version releases-url pinned?]
    :or {releases-url releases-url}}]
  (let [asset "aishell"
        download-url (fn [name] (str releases-url "/download/v" target-version "/" name))
        windows? (= :windows os)]
    {:asset-name asset
     :asset-url (download-url asset)
     :checksum-url (download-url (str asset ".sha256"))
     :checksum-asset asset
     :dest-path install-path
     :delete-after []
     :rename-old? false
     :old-path nil
     :chmod-x? (not windows?)
     :extra-downloads (if windows?
                        [{:url (download-url "aishell.bat")
                          :dest-path (str install-path ".bat")
                          :optional? true}]
                        [])
     :downgrade? (neg? (version-compare target-version current-version))
     :pinned? (boolean pinned?)
     :current-version current-version
     :target-version target-version
     :releases-url releases-url
     :notes []}))

(defn find-aishell-path
  "Find the installed aishell script path.
   On Windows, fs/which returns the .bat launcher (e.g. aishell.bat);
   we strip the extension to get the actual script path."
  []
  (let [path (or (fs/which "aishell")
                 (let [home (System/getProperty "user.home")
                       fallback (str home "/.local/bin/aishell")]
                   (when (fs/exists? fallback)
                     fallback)))]
    (when path
      (let [path-str (str path)]
        (if (and (fs/windows?)
                 (str/ends-with? (str/lower-case path-str) ".bat"))
          (subs path-str 0 (- (count path-str) 4))
          path-str)))))

(defn detect-env
  "Gather everything upgrade-plan needs from the machine.
   opts: :pinned? (the user named a version) and :releases-url (test/local override)."
  [current-version target-version {:keys [pinned? releases-url]}]
  (let [install-path (find-aishell-path)
        os (cond
             (fs/windows?) :windows
             (= "Mac OS X" (System/getProperty "os.name")) :macos
             :else :linux)
        raw-arch (System/getProperty "os.arch")
        arch (case raw-arch
               ("amd64" "x86_64") :amd64
               ("aarch64" "arm64") :aarch64
               (keyword raw-arch))]
    (cond-> {:os os
             :arch arch
             :install-path install-path
             :script-install? (boolean (and install-path
                                            (fs/exists? install-path)
                                            (shebang? (leading-bytes-or-nil install-path 2))))
             :bat-present? (boolean (and install-path
                                         (fs/exists? (str install-path ".bat"))))
             :current-version current-version
             :target-version target-version
             :pinned? (boolean pinned?)}
      releases-url (assoc :releases-url releases-url))))

(defn- install-file!
  "Move src onto dest, falling back to copy+delete across filesystems."
  [src dest]
  (try
    (fs/move src dest {:replace-existing true})
    (catch Exception _
      (fs/copy src dest {:replace-existing true})
      (fs/delete src))))

(defn execute-plan
  "Carry out an upgrade plan: download, verify, install, clean up.
   Every destination comes from the plan, so a plan pointing at a temp
   directory exercises the whole path without touching a real install."
  [plan downloader]
  (when-let [err (:error plan)]
    (output/error (:message err)))
  (let [{:keys [asset-name asset-url checksum-url checksum-asset dest-path
                delete-after chmod-x? extra-downloads
                current-version target-version notes]} plan
        releases (:releases-url plan releases-url)]
    ;; Write permission: the file itself when it is already there, else its directory.
    (when-not (if (fs/exists? dest-path)
                (fs/writable? dest-path)
                (fs/writable? (fs/parent dest-path)))
      (output/error (str "No write permission to " dest-path
                         "\nTry running with sudo or fix permissions.")))

    (println (str "Upgrading aishell: v" current-version " -> v" target-version))

    (let [tmp-dir (fs/create-temp-dir {:prefix "aishell-upgrade-"})
          tmp-asset (str tmp-dir "/" asset-name)
          tmp-checksum (str tmp-dir "/checksums")]
      (try
        (try
          (download-to-file downloader asset-url tmp-asset)
          (catch Exception _
            (output/error (str "Failed to download aishell v" target-version
                               "\nRelease may not exist: " releases "/tag/v" target-version))))

        (try
          (download-to-file downloader checksum-url tmp-checksum)
          (catch Exception _
            (output/error (str "Failed to download checksum for v" target-version))))

        (let [expected-hash (checksum-for (slurp tmp-checksum) checksum-asset)
              actual-hash (compute-sha256-file tmp-asset)]
          (when-not expected-hash
            (output/error (str "Checksum file does not list " checksum-asset ".\n"
                               "Download may be corrupted. Try again.")))
          (when (not= expected-hash actual-hash)
            (output/error (str "Checksum verification failed!\n"
                               "  Expected: " expected-hash "\n"
                               "  Got:      " actual-hash "\n"
                               "Download may be corrupted. Try again."))))

        (install-file! tmp-asset dest-path)

        (when chmod-x?
          (p/shell {:out :string :err :string}
                   "chmod" "+x" (str dest-path)))

        (doseq [{:keys [url dest-path optional?]} extra-downloads]
          (let [tmp (str tmp-dir "/" (fs/file-name dest-path))]
            (try
              (download-to-file downloader url tmp)
              (install-file! tmp dest-path)
              (catch Exception e
                (if optional?
                  (output/warn (str "Could not update " (fs/file-name dest-path) ": " (.getMessage e)))
                  (throw e))))))

        (doseq [path delete-after]
          (try
            (fs/delete-if-exists path)
            (catch Exception _ nil)))

        (println (str output/BOLD "Upgraded aishell: v" current-version
                      " -> v" target-version output/NC))
        (doseq [note notes]
          (println note))

        (finally
          (fs/delete-tree tmp-dir))))))

(defn do-upgrade
  "Main upgrade entry point.
   current-version: current aishell version string (e.g. \"3.3.0\")
   target-version: specific version to upgrade to, or nil for latest"
  [current-version target-version]
  (let [downloader (find-downloader)]
    (when-not downloader
      (output/error "Neither curl nor wget found. Install one to use upgrade."))

    (let [target (or target-version
                     (do
                       (println "Checking for latest version...")
                       (fetch-latest-version downloader)))]
      (when-not target
        (output/error "Could not determine latest version from GitHub.\nCheck your internet connection or specify a version: aishell upgrade <VERSION>"))

      (when (and (not target-version) (= current-version target))
        (println (str "Already up to date (v" current-version ")."))
        (output/exit! 0))

      (when (neg? (version-compare target current-version))
        (output/warn (str "Downgrading from v" current-version " to v" target)))

      (let [env (detect-env current-version target {:pinned? (some? target-version)})]
        (when-not (:install-path env)
          (output/error "Could not find aishell installation path.\nReinstall using: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash"))

        (execute-plan (upgrade-plan env) downloader)))))
