(ns aishell.util
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn get-home
  "Get user home directory (cross-platform).
   Windows: USERPROFILE > HOME > fs/home
   Unix: HOME > fs/home
   Silent fallback to fs/home ensures function always succeeds."
  []
  (if (fs/windows?)
    (or (System/getenv "USERPROFILE")
        (System/getenv "HOME")
        (str (fs/home)))
    (or (System/getenv "HOME")
        (str (fs/home)))))

(defn expand-path
  "Expand ~ and $HOME in path string, normalize separators.
   Works on both Windows and Unix."
  [path]
  (when path
    (let [home (get-home)]
      (str (fs/path
             (-> path
                 (str/replace #"^~(?=[/\\]|$)" home)
                 (str/replace #"\$HOME(?=[/\\]|$)" home)
                 (str/replace #"\$\{HOME\}(?=[/\\]|$)" home)))))))

(defn config-dir
  "Get aishell config directory path (~/.aishell)."
  []
  (str (fs/path (get-home) ".aishell")))

(defn state-dir
  "Get platform-appropriate state directory for aishell.
   Windows: LOCALAPPDATA/aishell (fallback to XDG default)
   Unix: XDG_STATE_HOME/aishell or ~/.local/state/aishell"
  []
  (if (fs/windows?)
    (let [localappdata (System/getenv "LOCALAPPDATA")
          fallback (str (fs/path (get-home) ".local" "state"))]
      (str (fs/path (or localappdata fallback) "aishell")))
    (let [xdg-state (or (System/getenv "XDG_STATE_HOME")
                         (str (fs/path (get-home) ".local" "state")))]
      (str (fs/path xdg-state "aishell")))))

(defn ensure-dir
  "Create directory if it doesn't exist. Returns the path."
  [dir]
  (when-not (fs/exists? dir)
    (fs/create-dirs dir))
  dir)

(defn project-config-path
  "Get path to project config file (.aishell/config.yaml) relative to given dir."
  [project-dir]
  (str (fs/path project-dir ".aishell" "config.yaml")))
