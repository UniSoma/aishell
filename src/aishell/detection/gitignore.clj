(ns aishell.detection.gitignore
  "Wrapper for git check-ignore to determine if files are protected by .gitignore."
  (:require [babashka.process :as p]))

(defn gitignored?
  "Check if a file is gitignored using git check-ignore.

   Arguments:
   - project-dir: Root directory of the project (where .git lives)
   - file-path: Path to the file to check (relative or absolute)

   Returns:
   - true: File IS in .gitignore (protected from commit)
   - false: File is NOT in .gitignore (at risk of accidental commit)
   - nil: Not a git repository or git command failed (treat as unprotected)"
  [project-dir file-path]
  (try
    (let [result (p/shell {:dir project-dir
                          :out :string
                          :err :string
                          :continue true}
                         "git" "check-ignore" "-q" file-path)]
      (case (:exit result)
        0 true   ; Exit 0: file is ignored
        1 false  ; Exit 1: file is not ignored
        nil))    ; Other exit codes: not a git repo or error
    (catch Exception _e nil)))
