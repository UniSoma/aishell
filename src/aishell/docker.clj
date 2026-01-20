(ns aishell.docker
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.output :as output]))

(defn docker-available?
  "Check if docker command exists in PATH"
  []
  (some? (fs/which "docker")))

(defn docker-running?
  "Check if Docker daemon is responsive"
  []
  (try
    (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                   "docker" "info")]
      (zero? exit))
    (catch Exception _ false)))

(defn check-docker!
  "Verify Docker is available and running, exit with error if not"
  []
  (cond
    (not (docker-available?))
    (output/error "Docker is not installed")

    (not (docker-running?))
    (output/error "Docker not running")))

(defn image-exists?
  "Check if a Docker image exists locally"
  [image-tag]
  (try
    (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                   "docker" "image" "inspect" image-tag)]
      (zero? exit))
    (catch Exception _ false)))

(defn get-image-label
  "Get a specific label from a Docker image.
   Uses Go template index syntax to handle label names with dots."
  [image label-key]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "inspect"
                   (str "--format={{index .Config.Labels \"" label-key "\"}}")
                   image)]
      (when (zero? exit)
        (let [value (str/trim out)]
          (when-not (or (empty? value) (= value "<no value>"))
            value))))
    (catch Exception _ nil)))

(defn format-size
  "Format bytes to human-readable size"
  [bytes]
  (let [bytes (if (string? bytes) (parse-long bytes) bytes)]
    (cond
      (nil? bytes) nil
      (< bytes 1024) (str bytes "B")
      (< bytes (* 1024 1024)) (format "%.1fKB" (/ bytes 1024.0))
      (< bytes (* 1024 1024 1024)) (format "%.1fMB" (/ bytes 1024.0 1024.0))
      :else (format "%.2fGB" (/ bytes 1024.0 1024.0 1024.0)))))

(defn get-image-size
  "Get human-readable image size"
  [image-tag]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "image" "inspect" "--format={{.Size}}" image-tag)]
      (when (zero? exit)
        (format-size (str/trim out))))
    (catch Exception _ nil)))
