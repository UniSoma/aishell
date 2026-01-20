(ns aishell.util
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn get-home
  "Get user home directory (cross-platform).
   Prefers HOME env var (works with network logins), falls back to fs/home."
  []
  (or (System/getenv "HOME")
      (str (fs/home))))

(defn expand-path
  "Expand ~ and $HOME in path string.
   Works on both Linux and macOS."
  [path]
  (when path
    (let [home (get-home)]
      (-> path
          (str/replace #"^~(?=/|$)" home)
          (str/replace #"\$HOME(?=/|$)" home)
          (str/replace #"\$\{HOME\}(?=/|$)" home)))))

(defn config-dir
  "Get aishell config directory path (~/.aishell)."
  []
  (str (fs/path (get-home) ".aishell")))

(defn state-dir
  "Get XDG state directory for aishell.
   Respects XDG_STATE_HOME if set, otherwise ~/.local/state/aishell."
  []
  (let [xdg-state (or (System/getenv "XDG_STATE_HOME")
                       (str (fs/path (get-home) ".local" "state")))]
    (str (fs/path xdg-state "aishell"))))

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
