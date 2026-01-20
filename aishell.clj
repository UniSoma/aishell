#!/usr/bin/env bb

(require '[babashka.fs :as fs])

;; Add src to classpath dynamically
(let [script-dir (fs/parent (fs/absolutize *file*))]
  (babashka.classpath/add-classpath (str (fs/path script-dir "src"))))

(require '[aishell.core :as core])
(apply core/-main *command-line-args*)
