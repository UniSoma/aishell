(ns aishell.detection.patterns
  "Pattern definitions and matching functions for sensitive files."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- in-excluded-dir?
  "Check if a path is within an excluded directory."
  [path excluded-dirs]
  (let [path-str (str path)]
    (some #(str/includes? path-str (str "/" % "/")) excluded-dirs)))

(defn- case-insensitive-basename-match?
  "Check if filename matches pattern (case-insensitive)."
  [path pattern-lower]
  (let [name-lower (str/lower-case (str (fs/file-name path)))]
    (or (= name-lower pattern-lower)
        (str/starts-with? name-lower (str pattern-lower ".")))))

(defn detect-env-files
  "Detect .env files (medium severity) and templates (low severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        env-files (filter (fn [path]
                           (let [name-lower (str/lower-case (str (fs/file-name path)))]
                             (or (= name-lower ".env")
                                 (str/starts-with? name-lower ".env.")
                                 (= name-lower ".envrc"))))
                         filtered)]
    (for [path env-files]
      (let [name-lower (str/lower-case (str (fs/file-name path)))
            is-template? (or (str/includes? name-lower "example")
                            (str/includes? name-lower "sample"))]
        {:path (str path)
         :type (if is-template? :env-template :env-file)
         :severity (if is-template? :low :medium)
         :reason (if is-template?
                   "Environment template file"
                   "Environment configuration file")}))))

(defn detect-ssh-keys
  "Detect SSH private key files (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [;; Exact filenames (case-insensitive)
        exact-names ["id_rsa" "id_dsa" "id_ed25519" "id_ecdsa"]
        all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Match exact SSH key filenames
        ssh-keys (filter (fn [path]
                          (let [name-lower (str/lower-case (str (fs/file-name path)))]
                            (some #(= name-lower %) exact-names)))
                        filtered)
        ;; Match *.ppk files (PuTTY keys)
        ppk-files (filter (fn [path]
                           (str/ends-with? (str/lower-case (str path)) ".ppk"))
                         filtered)]
    (concat
      (for [path ssh-keys]
        {:path (str path)
         :type :ssh-key
         :severity :high
         :reason "SSH private key file"})
      (for [path ppk-files]
        {:path (str path)
         :type :ssh-key
         :severity :high
         :reason "SSH private key file"}))))

(defn detect-key-containers
  "Detect key container files (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [extensions [".p12" ".pfx" ".jks" ".keystore"]
        all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        key-containers (filter (fn [path]
                                (let [path-lower (str/lower-case (str path))]
                                  (some #(str/ends-with? path-lower %) extensions)))
                              filtered)]
    (for [path key-containers]
      {:path (str path)
       :type :key-container
       :severity :high
       :reason "Key container file (PKCS12/JKS)"})))

(defn detect-pem-key-files
  "Detect PEM and key files (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [extensions [".pem" ".key"]
        all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        pem-key-files (filter (fn [path]
                               (let [path-lower (str/lower-case (str path))]
                                 (some #(str/ends-with? path-lower %) extensions)))
                             filtered)]
    (for [path pem-key-files]
      {:path (str path)
       :type :pem-key
       :severity :medium
       :reason "PEM/key file (may contain private key or certificate)"})))

(defn detect-gcp-credentials
  "Detect GCP application_default_credentials.json (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Case-insensitive exact match
        matches (filter (fn [path]
                         (= (str/lower-case (str (fs/file-name path)))
                            "application_default_credentials.json"))
                       filtered)]
    (for [path matches]
      {:path (str path)
       :type :gcp-credentials
       :severity :high
       :reason "GCP application default credentials file"})))

(defn detect-terraform-state
  "Detect terraform.tfstate* files (high severity - contains plaintext secrets).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [;; Match terraform.tfstate and terraform.tfstate.backup etc
        state-files (fs/glob project-dir "**/terraform.tfstate*" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) state-files)
        ;; Case-insensitive post-filtering
        matches (filter (fn [path]
                         (str/starts-with?
                           (str/lower-case (str (fs/file-name path)))
                           "terraform.tfstate"))
                       filtered)]
    (for [path matches]
      {:path (str path)
       :type :terraform-state
       :severity :high
       :reason "Terraform state file (may contain plaintext secrets)"})))

(defn detect-kubeconfig
  "Detect kubeconfig or .kube/config patterns (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Match "kubeconfig" filename (case-insensitive)
        kubeconfig-files (filter (fn [path]
                                  (= (str/lower-case (str (fs/file-name path))) "kubeconfig"))
                                filtered)
        ;; Match .kube/config path pattern
        kube-configs (filter (fn [path]
                              (str/includes? (str/lower-case (str path)) ".kube/config"))
                            filtered)]
    (for [path (distinct (concat kubeconfig-files kube-configs))]
      {:path (str path)
       :type :kubeconfig
       :severity :medium
       :reason "Kubernetes cluster configuration file"})))

(defn group-findings
  "Group findings by type and apply threshold-of-3 summarization.
   Returns seq of findings (individual or summary)."
  [findings]
  (let [by-type (group-by :type findings)]
    (mapcat
      (fn [[type group]]
        (if (<= (count group) 3)
          ;; Show individually
          group
          ;; Summarize with sample paths
          [{:type type
            :severity (:severity (first group))
            :path nil
            :reason (str (count group) " files detected")
            :summary? true
            :sample-paths (take 2 (map :path group))}]))
      by-type)))
