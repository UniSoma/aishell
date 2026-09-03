#!/usr/bin/env bb

;; Build aishell release assets
;; Usage: ./scripts/build-release.clj [--target all|host|<platform>]
;;
;; Produces one standalone executable per platform - the upstream babashka
;; binary with aishell's uberjar appended - plus a SHA256SUMS file:
;;   dist/aishell-linux-amd64
;;   dist/aishell-linux-aarch64
;;   dist/aishell-macos-amd64
;;   dist/aishell-macos-aarch64
;;   dist/aishell-windows-amd64.exe
;;   dist/SHA256SUMS
;;
;; And, while the legacy gate below is on, the pre-4.1.0 trio:
;;   dist/aishell        - Executable uberscript with shebang
;;   dist/aishell.bat    - Windows CMD wrapper
;;   dist/aishell.sha256 - SHA256 checksum of dist/aishell
;;
;; Version is defined in src/aishell/cli.clj - update there before release.

(ns build-release
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; The one place the host CLI's babashka runtime is pinned. Independent of the
;; foundation image's BABASHKA_VERSION (src/aishell/docker/templates.clj):
;; one is the CLI's own runtime, the other a tool offered inside the sandbox.
(def babashka-version "1.13.220")

;; Bridging release only: v4.0.0 installs run an `upgrade` that fetches the
;; legacy asset names. Remove this gate, and the code it guards, in 4.2.0
;; together with the assets themselves.
(def legacy-assets? true)

(def output-dir "dist")
(def legacy-script (str output-dir "/aishell"))
(def legacy-bat (str legacy-script ".bat"))
(def legacy-checksum (str legacy-script ".sha256"))
(def sums-file (str output-dir "/SHA256SUMS"))

(def targets
  "Release targets, keyed by platform. :archive is the upstream babashka asset
   for the pinned version; :member is the executable inside it."
  {"linux-amd64"    {:asset "aishell-linux-amd64"
                     :archive (str "babashka-" babashka-version "-linux-amd64-static.tar.gz")
                     :member "bb"}
   "linux-aarch64"  {:asset "aishell-linux-aarch64"
                     :archive (str "babashka-" babashka-version "-linux-aarch64-static.tar.gz")
                     :member "bb"}
   "macos-amd64"    {:asset "aishell-macos-amd64"
                     :archive (str "babashka-" babashka-version "-macos-amd64.tar.gz")
                     :member "bb"}
   "macos-aarch64"  {:asset "aishell-macos-aarch64"
                     :archive (str "babashka-" babashka-version "-macos-aarch64.tar.gz")
                     :member "bb"}
   "windows-amd64"  {:asset "aishell-windows-amd64.exe"
                     :archive (str "babashka-" babashka-version "-windows-amd64.zip")
                     :member "bb.exe"}})

(def target-order
  ["linux-amd64" "linux-aarch64" "macos-amd64" "macos-aarch64" "windows-amd64"])

(defn archive-url [target]
  (str "https://github.com/babashka/babashka/releases/download/v"
       babashka-version "/" (:archive target)))

(defn host-platform
  "The target key matching the machine running this script, or nil."
  []
  (let [os (cond
             (fs/windows?) "windows"
             (str/starts-with? (System/getProperty "os.name") "Mac OS X") "macos"
             :else "linux")
        arch (case (System/getProperty "os.arch")
               ("amd64" "x86_64") "amd64"
               ("aarch64" "arm64") "aarch64"
               nil)]
    (when arch
      (let [key (str os "-" arch)]
        (when (contains? targets key) key)))))

(defn die [msg]
  (binding [*out* *err*] (println msg))
  (System/exit 1))

(defn parse-target
  "Read the --target selector from argv, returning target keys to build."
  [args]
  (let [selector (or (second (drop-while #(not= "--target" %) args)) "all")]
    (case selector
      "all" target-order
      "host" (if-let [host (host-platform)]
               [host]
               (die (str "Error: unsupported host platform "
                         (System/getProperty "os.name") "/"
                         (System/getProperty "os.arch")
                         ". Supported targets: " (str/join ", " target-order))))
      (if (contains? targets selector)
        [selector]
        (die (str "Error: unknown target '" selector
                  "'. Use all, host, or one of: " (str/join ", " target-order)))))))

(defn compute-sha256
  "Compute SHA-256 hash of file, returning 64-character hex string."
  [file-path]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (with-open [in (io/input-stream (fs/file file-path))]
      (let [buf (byte-array 65536)]
        (loop []
          (let [n (.read in buf)]
            (when (pos? n)
              (.update md buf 0 n)
              (recur))))))
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md)))))

(defn build-uberjar
  "Build the aishell uberjar on an explicit src classpath so test/ - which
   bb.edn puts on :paths - is not bundled. Returns the jar path."
  [work-dir]
  (let [jar (str (fs/path work-dir "aishell.jar"))]
    (fs/delete-if-exists jar)
    (println "Building uberjar...")
    (p/shell "bb" "--classpath" "src" "uberjar" jar "-m" "aishell.core")
    jar))

(defn download [url dest]
  (println (str "  downloading " url))
  (p/shell "curl" "-fsSL" "-o" (str dest) url))

(defn verify-archive
  "Check a downloaded upstream archive against the .sha256 babashka publishes
   beside it. A release built on a corrupted download would ship five corrupted
   binaries."
  [url archive-path]
  (let [expected (str/lower-case
                  (str/trim (first (str/split (:out (p/shell {:out :string}
                                                             "curl" "-fsSL" (str url ".sha256")))
                                              #"\s+"))))
        actual (compute-sha256 archive-path)]
    (when (not= expected actual)
      (die (str "Error: checksum mismatch for " url "\n"
                "  expected: " expected "\n"
                "  got:      " actual)))))

(defn extract-bb
  "Extract the babashka executable from an upstream archive. Returns its path."
  [archive member dest-dir]
  (fs/create-dirs dest-dir)
  (if (str/ends-with? (str archive) ".zip")
    (fs/unzip archive dest-dir {:replace-existing true})
    (p/shell "tar" "-xzf" (str archive) "-C" (str dest-dir) member))
  (let [direct (fs/path dest-dir member)]
    (or (when (fs/exists? direct) direct)
        (first (fs/glob dest-dir (str "**/" member)))
        (die (str "Error: " member " not found in " archive)))))

(defn append-jar
  "Write bb-bytes ++ jar-bytes to out-path, streaming both."
  [bb-path jar-path out-path]
  (fs/delete-if-exists out-path)
  (with-open [out (io/output-stream (fs/file out-path))]
    (with-open [in (io/input-stream (fs/file bb-path))]
      (io/copy in out))
    (with-open [in (io/input-stream (fs/file jar-path))]
      (io/copy in out))))

(defn build-target
  "Produce dist/<asset> for one platform. Returns the asset filename."
  [target-key jar work-dir]
  (let [target (get targets target-key)
        {:keys [asset archive member]} target
        archive-path (fs/path work-dir archive)
        out-path (str output-dir "/" asset)]
    (println (str "Building " asset "..."))
    (download (archive-url target) archive-path)
    (verify-archive (archive-url target) archive-path)
    (let [bb-path (extract-bb archive-path member (fs/path work-dir target-key))]
      (append-jar bb-path jar out-path))
    (when-not (or (fs/windows?) (str/ends-with? asset ".exe"))
      (fs/set-posix-file-permissions out-path "rwxr-xr-x"))
    asset))

(defn create-bat-wrapper
  "Generate Windows .bat wrapper following neil pattern.
   Uses explicit CRLF line endings for Windows CMD compatibility."
  [script-name]
  (spit legacy-bat (str "@echo off\r\n"
                        "set ARGS=%*\r\n"
                        "set SCRIPT=%~dp0" script-name "\r\n"
                        "bb -f %SCRIPT% -- %ARGS%\r\n")))

(defn build-legacy-trio
  "Pre-4.1.0 assets: the uberscript, its CMD wrapper and its checksum file.
   Returns the asset filenames. Removed in 4.2.0."
  []
  (println "Building legacy uberscript (bridging release)...")
  (fs/delete-if-exists legacy-script)
  (fs/delete-if-exists legacy-bat)
  (p/shell "bb" "uberscript" legacy-script "-m" "aishell.core")
  (let [content (slurp legacy-script)]
    (spit legacy-script (str "#!/usr/bin/env bb\n" content)))
  (when-not (fs/windows?)
    (p/shell "chmod" "+x" legacy-script))
  (create-bat-wrapper "aishell")
  ;; Format: {hash}  {filename} (two spaces, relative filename)
  (spit legacy-checksum (str (compute-sha256 legacy-script) "  aishell\n"))
  ["aishell" "aishell.bat" "aishell.sha256"])

(defn write-sums
  "SHA256SUMS lists every asset produced by this run, in `hash  filename` form."
  [assets]
  (spit sums-file
        (str/join (for [asset assets]
                    (str (compute-sha256 (str output-dir "/" asset)) "  " asset "\n")))))

(defn main [args]
  (let [target-keys (parse-target args)
        work-dir (fs/create-temp-dir {:prefix "aishell-build-"})]
    (try
      (fs/create-dirs output-dir)
      (let [jar (build-uberjar work-dir)
            binaries (doall (for [k target-keys] (build-target k jar work-dir)))
            legacy (when legacy-assets? (build-legacy-trio))
            assets (concat binaries legacy)]
        (write-sums assets)
        (println)
        (println "Build complete!")
        (doseq [asset assets]
          (println (str "  " output-dir "/" asset)))
        (println (str "  " sums-file))
        (println)
        (print (slurp sums-file)))
      (finally
        (fs/delete-tree work-dir)))))

(main *command-line-args*)
