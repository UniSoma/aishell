(ns aishell.upgrade-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.upgrade :as upgrade]))

(deftest checksum-for-finds-the-hash-of-a-named-asset
  (testing "multi-line sums file, exact filename token"
    (let [sums (str "aaaa  aishell-linux-amd64\n"
                    "bbbb  aishell-macos-arm64\n")]
      (is (= "bbbb" (upgrade/checksum-for sums "aishell-macos-arm64"))))))

(deftest checksum-for-matches-the-whole-filename-token
  (testing "\"aishell\" does not match the aishell-linux-amd64 line"
    (is (nil? (upgrade/checksum-for "aaaa  aishell-linux-amd64\n" "aishell")))))

(deftest checksum-for-reads-the-legacy-single-line-file
  (testing "the v4.0.0 aishell.sha256 shape"
    (is (= "deadbeef" (upgrade/checksum-for "deadbeef  aishell\n" "aishell")))))

(deftest checksum-for-lowercases-the-hash
  (testing "certutil-style uppercase hashes compare case-insensitively"
    (is (= "abcdef" (upgrade/checksum-for "ABCDEF  aishell\n" "aishell")))))

(deftest checksum-for-ignores-the-binary-marker
  (testing "sha256sum's \"*name\" binary marker is not part of the token"
    (is (= "aaaa" (upgrade/checksum-for "aaaa *aishell\n" "aishell")))))

(deftest checksum-for-tolerates-crlf-and-blank-lines
  (testing "Windows line endings and trailing blank lines"
    (is (= "aaaa" (upgrade/checksum-for "\r\nbbbb  other\r\naaaa  aishell\r\n\r\n" "aishell")))))

(deftest checksum-for-returns-nil-for-a-missing-asset
  (testing "asset absent from the sums file"
    (is (nil? (upgrade/checksum-for "aaaa  aishell\n" "aishell-windows-amd64.exe")))))

(defn- with-temp-dir [f]
  (let [dir (str (fs/create-temp-dir {:prefix "aishell-upgrade-test"}))]
    (try
      (f dir)
      (finally
        (fs/delete-tree dir)))))

(deftest shebang?-detects-a-script
  (testing "leading #! bytes"
    (is (true? (upgrade/shebang? (.getBytes "#!/usr/bin/env bb\n"))))))

(deftest shebang?-rejects-a-binary
  (testing "ELF magic, empty, one byte and nil are all not scripts"
    (is (false? (upgrade/shebang? (byte-array [0x7f 0x45 0x4c 0x46]))))
    (is (false? (upgrade/shebang? (byte-array 0))))
    (is (false? (upgrade/shebang? (.getBytes "#"))))
    (is (false? (upgrade/shebang? nil)))))

(deftest read-leading-bytes-reads-only-the-head-of-a-file
  (testing "a large file yields exactly n bytes"
    (with-temp-dir
      (fn [dir]
        (let [path (str dir "/big")]
          (fs/write-bytes path (byte-array (* 1024 1024) (byte 7)))
          (is (= 8 (alength (upgrade/read-leading-bytes path 8)))))))))

(deftest read-leading-bytes-returns-what-a-short-file-has
  (testing "file shorter than n"
    (with-temp-dir
      (fn [dir]
        (let [path (str dir "/short")]
          (spit path "#!")
          (is (= "#!" (String. (upgrade/read-leading-bytes path 64)))))))))

(def ^:private unix-env
  {:os :linux
   :arch :amd64
   :install-path "/home/u/.local/bin/aishell"
   :script-install? true
   :bat-present? false
   :current-version "4.0.0"
   :target-version "4.1.0"
   :pinned? false})

(deftest upgrade-plan-fetches-the-uberscript-on-unix
  (testing "asset aishell, dest = installed path, chmod, no extras"
    (let [plan (upgrade/upgrade-plan unix-env)]
      (is (= "aishell" (:asset-name plan)))
      (is (= "https://github.com/UniSoma/aishell/releases/download/v4.1.0/aishell"
             (:asset-url plan)))
      (is (= "https://github.com/UniSoma/aishell/releases/download/v4.1.0/aishell.sha256"
             (:checksum-url plan)))
      (is (= "aishell" (:checksum-asset plan)))
      (is (= "/home/u/.local/bin/aishell" (:dest-path plan)))
      (is (= [] (:delete-after plan)))
      (is (false? (:rename-old? plan)))
      (is (nil? (:old-path plan)))
      (is (true? (:chmod-x? plan)))
      (is (= [] (:extra-downloads plan)))
      (is (= [] (:notes plan))))))

(deftest upgrade-plan-refreshes-the-bat-launcher-on-windows
  (testing "no chmod, and aishell.bat is fetched beside the script"
    (let [plan (upgrade/upgrade-plan (assoc unix-env
                                            :os :windows
                                            :install-path "C:\\bin\\aishell"))]
      (is (false? (:chmod-x? plan)))
      (is (= "C:\\bin\\aishell" (:dest-path plan)))
      (is (= [{:url "https://github.com/UniSoma/aishell/releases/download/v4.1.0/aishell.bat"
               :dest-path "C:\\bin\\aishell.bat"
               :optional? true}]
             (:extra-downloads plan)))
      (is (= [] (:delete-after plan)))
      (is (false? (:rename-old? plan))))))

(deftest upgrade-plan-uses-pinned-urls-either-way
  (testing "a pinned target and a resolved latest produce identical URLs"
    (let [latest (upgrade/upgrade-plan unix-env)
          pinned (upgrade/upgrade-plan (assoc unix-env :pinned? true))]
      (is (= (:asset-url latest) (:asset-url pinned)))
      (is (= (:checksum-url latest) (:checksum-url pinned))))))

(deftest upgrade-plan-honors-a-releases-url-override
  (testing "both URLs come from :releases-url"
    (let [plan (upgrade/upgrade-plan (assoc unix-env :releases-url "http://localhost:8000/releases"))]
      (is (= "http://localhost:8000/releases/download/v4.1.0/aishell" (:asset-url plan)))
      (is (= "http://localhost:8000/releases/download/v4.1.0/aishell.sha256" (:checksum-url plan))))))

(deftest upgrade-plan-flags-a-downgrade
  (testing ":downgrade? follows the version comparison"
    (is (false? (:downgrade? (upgrade/upgrade-plan unix-env))))
    (is (true? (:downgrade? (upgrade/upgrade-plan (assoc unix-env :target-version "3.9.0")))))))

(deftest upgrade-plan-carries-the-versions-through
  (testing "the executor prints them, so the plan must hold them"
    (let [plan (upgrade/upgrade-plan unix-env)]
      (is (= "4.0.0" (:current-version plan)))
      (is (= "4.1.0" (:target-version plan))))))

(deftest detect-env-reports-a-script-install
  (testing "leading #! on the installed file, and no .bat beside it"
    (with-temp-dir
      (fn [dir]
        (let [path (str dir "/aishell")]
          (spit path "#!/usr/bin/env bb\n")
          (with-redefs [upgrade/find-aishell-path (constantly path)]
            (let [env (upgrade/detect-env "4.0.0" "4.1.0" {})]
              (is (= path (:install-path env)))
              (is (true? (:script-install? env)))
              (is (false? (:bat-present? env)))
              (is (= "4.0.0" (:current-version env)))
              (is (= "4.1.0" (:target-version env)))
              (is (contains? #{:linux :macos :windows} (:os env))))))))))

(deftest detect-env-reports-a-binary-install-with-a-bat-beside-it
  (testing "no shebang, aishell.bat present"
    (with-temp-dir
      (fn [dir]
        (let [path (str dir "/aishell")]
          (fs/write-bytes path (byte-array [0x7f 0x45 0x4c 0x46]))
          (spit (str dir "/aishell.bat") "@echo off\n")
          (with-redefs [upgrade/find-aishell-path (constantly path)]
            (let [env (upgrade/detect-env "4.0.0" "4.1.0" {})]
              (is (false? (:script-install? env)))
              (is (true? (:bat-present? env))))))))))

(deftest detect-env-passes-opts-through
  (testing ":pinned? and :releases-url reach the env"
    (with-redefs [upgrade/find-aishell-path (constantly nil)]
      (let [env (upgrade/detect-env "4.0.0" "3.9.0" {:pinned? true :releases-url "http://localhost/r"})]
        (is (nil? (:install-path env)))
        (is (false? (:script-install? env)))
        (is (true? (:pinned? env)))
        (is (= "http://localhost/r" (:releases-url env)))))))

(defn- stub-downloads
  "Serve download-to-file from an in-memory {url content} map."
  [responses f]
  (with-redefs [upgrade/download-to-file
                (fn [_ url dest]
                  (if-let [content (get responses url)]
                    (spit dest content)
                    (throw (ex-info (str "404 " url) {}))))]
    (f)))

(deftest execute-plan-installs-a-verified-download
  (testing "asset lands at :dest-path after its hash matches the checksum file"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell")
              body "#!/usr/bin/env bb\n(println :hi)\n"
              hash (let [tmp (str dir "/probe")]
                     (spit tmp body)
                     (let [h (upgrade/compute-sha256-file tmp)] (fs/delete tmp) h))
              plan {:asset-name "aishell"
                    :asset-url "http://x/aishell"
                    :checksum-url "http://x/aishell.sha256"
                    :checksum-asset "aishell"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? true
                    :extra-downloads []
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes []}
              out (stub-downloads {"http://x/aishell" body
                                   "http://x/aishell.sha256" (str hash "  aishell\n")}
                                  #(with-out-str (upgrade/execute-plan plan :curl)))]
          (is (= body (slurp dest)))
          (is (str/includes? out "Upgrading aishell: v4.0.0 -> v4.1.0"))
          (is (str/includes? out "Upgraded aishell: v4.0.0 -> v4.1.0")))))))

(deftest execute-plan-aborts-on-a-checksum-mismatch
  (testing "a hash that does not match reports the mismatch and exits 1"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell")
              _ (spit dest "original\n")
              err (java.io.StringWriter.)
              exits (atom [])
              plan {:asset-name "aishell"
                    :asset-url "http://x/aishell"
                    :checksum-url "http://x/aishell.sha256"
                    :checksum-asset "aishell"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? false
                    :extra-downloads []
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes []}]
          ;; exit! throws here so control really stops, the way System/exit does
          ;; in production: a collecting stub would let execute-plan fall through
          ;; and install the tampered file.
          (with-redefs [output/exit! (fn [c]
                                       (swap! exits conj c)
                                       (throw (ex-info "exit" {:code c})))]
            (try
              (stub-downloads {"http://x/aishell" "tampered\n"
                               "http://x/aishell.sha256" (str (apply str (repeat 64 "a")) "  aishell\n")}
                              #(binding [*err* err]
                                 (with-out-str (upgrade/execute-plan plan :curl))))
              (catch clojure.lang.ExceptionInfo _ nil)))
          (is (str/includes? (str err) "Checksum verification failed!"))
          (is (= [1] @exits))
          (is (= "original\n" (slurp dest))
              "a failed checksum must leave the installed file untouched"))))))

(deftest execute-plan-removes-the-files-the-plan-lists
  (testing ":delete-after paths are gone once the install succeeds"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell.exe")
              stale (str dir "/aishell")
              body "binary\n"
              hash (let [tmp (str dir "/probe")]
                     (spit tmp body)
                     (let [h (upgrade/compute-sha256-file tmp)] (fs/delete tmp) h))
              plan {:asset-name "aishell"
                    :asset-url "http://x/aishell"
                    :checksum-url "http://x/sums"
                    :checksum-asset "aishell"
                    :dest-path dest
                    :delete-after [stale (str dir "/never-existed")]
                    :chmod-x? false
                    :extra-downloads []
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes []}]
          (spit stale "old script\n")
          (stub-downloads {"http://x/aishell" body
                           "http://x/sums" (str hash "  aishell\n")}
                          #(with-out-str (upgrade/execute-plan plan :curl)))
          (is (true? (fs/exists? dest)))
          (is (false? (fs/exists? stale))))))))

(deftest execute-plan-prints-the-plan-notes
  (testing "notes reach the user verbatim"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell")
              body "x\n"
              hash (let [tmp (str dir "/probe")]
                     (spit tmp body)
                     (let [h (upgrade/compute-sha256-file tmp)] (fs/delete tmp) h))
              plan {:asset-name "aishell"
                    :asset-url "http://x/aishell"
                    :checksum-url "http://x/sums"
                    :checksum-asset "aishell"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? false
                    :extra-downloads []
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes ["Removed the old aishell script."]}
              out (stub-downloads {"http://x/aishell" body
                                   "http://x/sums" (str hash "  aishell\n")}
                                  #(with-out-str (upgrade/execute-plan plan :curl)))]
          (is (str/includes? out "Removed the old aishell script.")))))))

(deftest parse-semver-and-version-compare-still-hold
  (testing "the executor still relies on these; behaviour is unchanged"
    (is (= [4 1 0] (upgrade/parse-semver "4.1.0")))
    (is (= [4 1 0] (upgrade/parse-semver "v4.1.0")))
    (is (= [4 1 0] (upgrade/parse-semver "4.1.0-rc1")))
    (is (nil? (upgrade/parse-semver "4.1")))
    (is (nil? (upgrade/parse-semver "not-a-version")))
    (is (nil? (upgrade/parse-semver nil)))
    (is (neg? (upgrade/version-compare "4.0.0" "4.1.0")))
    (is (zero? (upgrade/version-compare "4.1.0" "4.1.0")))
    (is (pos? (upgrade/version-compare "4.10.0" "4.9.0")))))
