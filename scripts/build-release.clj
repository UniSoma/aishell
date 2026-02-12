#!/usr/bin/env bb

;; Build aishell uberscript for distribution
;; Usage: ./scripts/build-release.clj
;;
;; Produces:
;;   dist/aishell       - Executable uberscript with shebang
;;   dist/aishell.bat   - Windows CMD wrapper
;;   dist/aishell.sha256 - SHA256 checksum file
;;
;; Version is defined in src/aishell/cli.clj - update there before release.

(ns build-release
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

(def output-dir "dist")
(def output-file (str output-dir "/aishell"))
(def bat-file (str output-file ".bat"))

(defn compute-sha256
  "Compute SHA-256 hash of file, returning 64-character hex string."
  [file-path]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bytes (.digest md (fs/read-all-bytes file-path))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn create-bat-wrapper
  "Generate Windows .bat wrapper following neil pattern.
   Uses explicit CRLF line endings for Windows CMD compatibility."
  [script-name]
  (spit bat-file (str "@echo off\r\n"
                      "set ARGS=%*\r\n"
                      "set SCRIPT=%~dp0" script-name "\r\n"
                      "bb -f %SCRIPT% %ARGS%\r\n")))

(defn main []
  (println "Building aishell uberscript...")

  ;; Create output directory
  (fs/create-dirs output-dir)

  ;; Remove existing uberscript (bb uberscript refuses to overwrite)
  (fs/delete-if-exists output-file)
  (fs/delete-if-exists bat-file)

  ;; Build uberscript with main namespace
  ;; This bundles all namespaces reachable via static requires from core.clj
  (p/shell "bb" "uberscript" output-file "-m" "aishell.core")

  ;; Add shebang for direct execution (native Clojure, no sed)
  (let [content (slurp output-file)]
    (spit output-file (str "#!/usr/bin/env bb\n" content)))

  ;; Make executable
  (when-not (fs/windows?)
    (p/shell "chmod" "+x" output-file))

  ;; Generate Windows .bat wrapper
  (create-bat-wrapper "aishell")

  ;; Generate checksum using Java MessageDigest
  ;; Format: {hash}  {filename} (two spaces, relative filename)
  (let [hash (compute-sha256 output-file)
        checksum-file (str output-file ".sha256")
        checksum-content (str hash "  aishell\n")]
    (spit checksum-file checksum-content))

  ;; Print completion message
  (println)
  (println "Build complete!")
  (println (str "  Binary:   " output-file))
  (println (str "  Wrapper:  " bat-file))
  (println (str "  Checksum: " output-file ".sha256"))
  (println)
  (print (slurp (str output-file ".sha256"))))

(main)
