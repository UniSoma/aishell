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

(defn download-argv
  "Command line for fetching url to dest.
   With a meter when stdout is a terminal: `curl -s` would suppress `-#`, so the
   quiet flags cannot simply be reused."
  [downloader url dest tty?]
  (case downloader
    :curl (if tty?
            ["curl" "-fSL" "-#" "-o" (str dest) url]
            ["curl" "-fsSL" "-o" (str dest) url])
    :wget (if tty?
            ["wget" "-q" "--show-progress" "-O" (str dest) url]
            ["wget" "-q" "-O" (str dest) url])))

(defn content-length-from-headers
  "Size in bytes from the last content-length header of a response chain, or nil."
  [headers]
  (when headers
    (some->> (re-seq #"(?i)content-length:\s*(\d+)" headers)
             seq
             last
             second
             parse-long)))

(defn remote-content-length
  "Ask the server how big the asset is. nil when the probe fails."
  [downloader url]
  (try
    (let [result (case downloader
                   :curl (p/shell {:out :string :err :string :continue true}
                                  "curl" "-fsSLI" url)
                   :wget (p/shell {:out :string :err :string :continue true}
                                  "wget" "--spider" "-S" "--max-redirect=5" url))]
      (when (zero? (:exit result))
        (content-length-from-headers (str (:out result) (:err result)))))
    (catch Exception _ nil)))

(defn describe-size
  "Human-readable download size. Falls back to the ADR's estimate when unknown."
  [bytes]
  (if bytes
    (str (Math/round (double (/ bytes 1024 1024))) " MB")
    "about 90 MB"))

(defn run-download!
  "Run a download command line, letting its progress meter reach the terminal."
  [argv]
  (apply p/shell argv))

(defn download-asset!
  "Fetch the release binary, showing a progress bar on a terminal and printing
   one size line otherwise."
  [downloader url dest asset-name]
  (let [tty? (output/tty?)]
    (when-not tty?
      (println (str "Downloading " asset-name " (" (describe-size (remote-content-length downloader url)) ")...")))
    (run-download! (download-argv downloader url dest tty?))))

(defn http-head
  "Headers of a HEAD request, following redirects. For curl the text is the
   effective URL; for wget it is the header dump wget writes to stderr.
   Returns nil when the request fails."
  [downloader url]
  (case downloader
    :curl (let [null-dev (if (fs/windows?) "NUL" "/dev/null")
                result (p/shell {:out :string :err :string :continue true}
                                "curl" "-fsSLI" "-o" null-dev
                                "-w" "%{url_effective}" url)]
            (when (zero? (:exit result))
              (str/trim (:out result))))
    :wget (:err (p/shell {:out :string :err :string :continue true}
                         "wget" "--spider" "-S" "--max-redirect=5" url))))

(defn fetch-latest-version
  "Fetch latest release version by following the /releases/latest redirect.
   Returns version string like \"3.3.0\" (without v prefix), or nil on failure."
  ([downloader] (fetch-latest-version downloader releases-url))
  ([downloader base-url]
   (let [url (str base-url "/latest")]
     (try
       (let [text (http-head downloader url)]
         (case downloader
           :curl
           (when (and text (str/includes? text "/tag/v"))
             (subs text (+ (str/last-index-of text "/v") 2)))

           :wget
           (let [location-line (last (filter #(str/includes? % "Location:")
                                             (str/split-lines (or text ""))))]
             (when (and location-line (str/includes? location-line "/tag/v"))
               (let [loc (str/trim (subs location-line (+ (str/index-of location-line "Location:") 9)))]
                 (subs loc (+ (str/last-index-of loc "/v") 2)))))))
       (catch Exception _
         nil)))))

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

(def supported-targets
  "Platform table: [os arch] -> the release asset built for it."
  {[:linux   :amd64]   "aishell-linux-amd64"
   [:linux   :aarch64] "aishell-linux-aarch64"
   [:macos   :amd64]   "aishell-macos-amd64"
   [:macos   :aarch64] "aishell-macos-aarch64"
   [:windows :amd64]   "aishell-windows-amd64.exe"})

(defn- split-dir
  "Split a path into [directory separator file-name].
   Handles Windows paths on any host: fs/parent cannot see the backslashes in
   \"C:\\\\bin\\\\aishell\" when the plan is built or tested on Linux."
  [path]
  (let [idx (max (long (or (str/last-index-of path "/") -1))
                 (long (or (str/last-index-of path "\\") -1)))]
    (if (neg? idx)
      [nil nil path]
      [(subs path 0 idx) (subs path idx (inc idx)) (subs path (inc idx))])))

(defn old-path-for
  "Where a destination is parked while it is still running.
   Both the plan and the startup cleanup derive the name from here, so they
   cannot drift apart."
  [dest-path]
  (str dest-path ".old"))

(defn- sibling
  "Path of `name` in the same directory as `path`."
  [path name]
  (let [[dir sep _] (split-dir path)]
    (if dir (str dir sep name) name)))

(defn upgrade-plan
  "Pure: decide what to fetch, where to put it, and what to clean up.
   Reads only the env map (see detect-env) and returns a plan map that
   execute-plan carries out. May return {:error {:message .. :code ..}} when the
   environment cannot be served."
  [{:keys [os arch install-path script-install? bat-present?
           current-version target-version releases-url pinned?]
    :or {releases-url releases-url}}]
  (if-let [asset (get supported-targets [os arch])]
    (let [download-url (fn [name] (str releases-url "/download/v" target-version "/" name))
          windows? (= :windows os)
          dest-path (if windows?
                      (sibling install-path "aishell.exe")
                      (sibling install-path "aishell"))
          rename-old? (and windows? (not script-install?))]
      {:asset-name asset
       :asset-url (download-url asset)
       :checksum-url (download-url "SHA256SUMS")
       :checksum-asset asset
       :dest-path dest-path
       :delete-after (if windows?
                       (vec (concat (when script-install? [(sibling install-path "aishell")])
                                    (when bat-present? [(sibling install-path "aishell.bat")])))
                       [])
       :rename-old? rename-old?
       :old-path (when rename-old? (old-path-for dest-path))
       :chmod-x? (not windows?)
       :downgrade? (neg? (version-compare target-version current-version))
       :pinned? (boolean pinned?)
       :current-version current-version
       :target-version target-version
       :releases-url releases-url
       :notes (if (and windows? script-install?)
                [(str "Removed the old aishell script"
                      (when bat-present? " and aishell.bat")
                      "; aishell.exe is now on PATH.")]
                [])})
    {:error {:message (str "Unsupported platform: " (name (or os :unknown)) "/"
                           (name (or arch :unknown)) ". Supported: "
                           (str/join ", " (sort (vals supported-targets))) ".")
             :code "unsupported_platform"}}))

(defn find-aishell-path
  "Find the installed aishell, as it sits on PATH.
   On Windows a pre-4.1.0 install is found through its aishell.bat launcher;
   the .bat suffix is stripped so the caller sees the script itself, which is
   what tells a script install from a binary one. The plan derives the
   destination from the directory, so either shape lands in the right place."
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

(defn release-base-url
  "Base URL for release assets: an explicit override, else AISHELL_RELEASE_URL
   (the same variable the installers honour), else the GitHub releases page."
  [override]
  (let [raw (or override (System/getenv "AISHELL_RELEASE_URL"))
        trimmed (some-> raw str/trim not-empty)]
    (if trimmed
      (str/replace trimmed #"/+$" "")
      releases-url)))

(defn cleanup-stale-old!
  "Delete the aishell.exe.old a previous upgrade left behind.
   Runs at startup, so every failure is swallowed: a leftover is cosmetic and
   must never keep the CLI from starting."
  ([]
   (try
     (when (fs/windows?)
       (cleanup-stale-old! (find-aishell-path)))
     (catch Exception _ nil)))
  ([install-path]
   (try
     (when install-path
       (fs/delete-if-exists (old-path-for (sibling install-path "aishell.exe"))))
     (catch Exception _ nil))
   nil))

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
    {:os os
     :arch arch
     :install-path install-path
     :script-install? (boolean (and install-path
                                    (fs/exists? install-path)
                                    (shebang? (leading-bytes-or-nil install-path 2))))
     :bat-present? (boolean (and install-path
                                 (fs/exists? (sibling install-path "aishell.bat"))))
     :current-version current-version
     :target-version target-version
     :pinned? (boolean pinned?)
     :releases-url (release-base-url releases-url)}))

(defn install-file!
  "Move src onto dest, falling back to copy+delete across filesystems."
  [src dest]
  (try
    (fs/move src dest {:replace-existing true})
    (catch Exception _
      (fs/copy src dest {:replace-existing true})
      (fs/delete src))))

(defn- staging-dir
  "A temp directory to download into, beside the destination where possible.
   Same filesystem means installing is a rename, so a 90 MB asset can never be
   caught half-written on PATH; the system temp dir is the fallback for an
   install directory we may not create files in."
  [dest-path]
  (let [parent (fs/parent dest-path)]
    (or (try
          (when (and parent (fs/writable? parent))
            (fs/create-temp-dir {:dir parent :prefix ".aishell-upgrade-"}))
          (catch Exception _ nil))
        (fs/create-temp-dir {:prefix "aishell-upgrade-"}))))

(defn execute-plan
  "Carry out an upgrade plan: download, verify, install, clean up.
   Every destination comes from the plan, so a plan pointing at a temp
   directory exercises the whole path without touching a real install."
  [plan downloader]
  (when-let [err (:error plan)]
    (output/error (:message err)))
  (let [{:keys [asset-name asset-url checksum-url checksum-asset dest-path
                delete-after chmod-x? rename-old? old-path
                current-version target-version notes]} plan
        releases (:releases-url plan releases-url)]
    ;; Write permission: the file itself when it is already there, else its directory.
    (when-not (if (fs/exists? dest-path)
                (fs/writable? dest-path)
                (fs/writable? (fs/parent dest-path)))
      (output/error (str "No write permission to " dest-path
                         "\nTry running with sudo or fix permissions.")))

    (println (str "Upgrading aishell: v" current-version " -> v" target-version))

    (let [tmp-dir (staging-dir dest-path)
          tmp-asset (str tmp-dir "/" asset-name)
          tmp-checksum (str tmp-dir "/checksums")]
      (try
        (try
          (download-asset! downloader asset-url tmp-asset asset-name)
          (catch Exception _
            (output/error (str "Failed to download " asset-name " v" target-version
                               " from " asset-url
                               "\nCheck your connection, and that the release exists: "
                               releases "/tag/v" target-version))))

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

        ;; Executable before it is installed, never after: dest must not exist
        ;; for a moment as a file nobody can run.
        (when chmod-x?
          (p/shell {:out :string :err :string}
                   "chmod" "+x" tmp-asset))

        ;; A running Windows executable cannot be replaced, only renamed:
        ;; move it aside first and let the next start delete the leftover.
        (let [parked? (and rename-old? old-path (fs/exists? dest-path))]
          (when parked?
            (try
              (fs/delete-if-exists old-path)
              (install-file! dest-path old-path)
              (catch Exception e
                (output/error (str "Could not move the running " (fs/file-name dest-path)
                                   " aside: " (.getMessage e))))))

          ;; The staging directory sits beside dest, so this is a rename on the
          ;; same filesystem and nobody ever sees a half-written aishell. If it
          ;; fails anyway, put back what was parked rather than leaving the user
          ;; with nothing on PATH.
          (try
            (install-file! tmp-asset dest-path)
            (catch Exception e
              (when parked?
                (try
                  (install-file! old-path dest-path)
                  (catch Exception _ nil)))
              (output/error (str "Could not install " (fs/file-name dest-path) ": "
                                 (.getMessage e)
                                 (when parked?
                                   "\nThe previous version was put back."))))))

        (let [failed (doall (remove (fn [path]
                                      (try
                                        (fs/delete-if-exists path)
                                        true
                                        (catch Exception _ false)))
                                    delete-after))]
          (println (str output/BOLD "Upgraded aishell: v" current-version
                        " -> v" target-version output/NC))
          (if (seq failed)
            (doseq [path failed]
              (output/warn (str "Could not remove " path "; delete it by hand.")))
            (doseq [note notes]
              (println note))))

        (finally
          (fs/delete-tree tmp-dir))))))

(defn do-upgrade
  "Main upgrade entry point.
   current-version: current aishell version string (e.g. \"3.3.0\")
   target-version: specific version to upgrade to, or nil for latest"
  [current-version target-version]
  (let [downloader (find-downloader)
        base-url (release-base-url nil)]
    (when-not downloader
      (output/error "Neither curl nor wget found. Install one to use upgrade."))

    (let [target (or target-version
                     (do
                       (println "Checking for latest version...")
                       (fetch-latest-version downloader base-url)))]
      (when-not target
        (output/error "Could not determine latest version from GitHub.\nCheck your internet connection or specify a version: aishell upgrade <VERSION>"))

      (when (and (not target-version) (= current-version target))
        (println (str "Already up to date (v" current-version ")."))
        (output/exit! 0))

      (when (neg? (version-compare target current-version))
        (output/warn (str "Downgrading from v" current-version " to v" target)))

      (let [env (detect-env current-version target {:pinned? (some? target-version)
                                                   :releases-url base-url})]
        (when-not (:install-path env)
          (output/error "Could not find aishell installation path.\nReinstall using: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash"))

        (execute-plan (upgrade-plan env) downloader)))))
