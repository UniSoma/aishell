(ns aishell.upgrade-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [babashka.process :as p]
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

(defn- plan-for [& kvs]
  (upgrade/upgrade-plan (merge unix-env (apply hash-map kvs))))

(deftest upgrade-plan-picks-the-asset-for-every-supported-platform
  (testing "each [os arch] pair maps to its release archive, script or binary install alike"
    (doseq [[[os arch] asset] {[:linux :amd64] "aishell-linux-amd64.tar.gz"
                               [:linux :aarch64] "aishell-linux-aarch64.tar.gz"
                               [:macos :amd64] "aishell-macos-amd64.tar.gz"
                               [:macos :aarch64] "aishell-macos-aarch64.tar.gz"
                               [:windows :amd64] "aishell-windows-amd64.zip"}
            script? [true false]
            pinned? [true false]]
      (let [install-path (if (= :windows os)
                           (if script? "C:\\bin\\aishell" "C:\\bin\\aishell.exe")
                           "/home/u/.local/bin/aishell")
            plan (plan-for :os os :arch arch :install-path install-path
                           :script-install? script? :pinned? pinned?)]
        (is (nil? (:error plan)))
        (is (= asset (:asset-name plan)))
        (is (= asset (:checksum-asset plan)))
        (is (= (if (= :windows os) "aishell.exe" "aishell") (:member plan)))
        (is (= (str "https://github.com/UniSoma/aishell/releases/download/v4.1.0/" asset)
               (:asset-url plan)))
        (is (= "https://github.com/UniSoma/aishell/releases/download/v4.1.0/SHA256SUMS"
               (:checksum-url plan)))
        (is (= (not= :windows os) (:chmod-x? plan)))))))

(deftest upgrade-plan-writes-aishell-exe-on-windows-and-aishell-elsewhere
  (testing "the destination comes from the install directory, not the resolved file name"
    (is (= "/home/u/.local/bin/aishell" (:dest-path (plan-for))))
    (is (= "C:\\bin\\aishell.exe"
           (:dest-path (plan-for :os :windows :install-path "C:\\bin\\aishell"))))
    (is (= "C:\\bin\\aishell.exe"
           (:dest-path (plan-for :os :windows :install-path "C:\\bin\\aishell.exe"
                                 :script-install? false))))))

(deftest upgrade-plan-rejects-an-unsupported-platform
  (testing "an unknown [os arch] pair yields the error envelope, not a plan"
    (let [plan (plan-for :arch :riscv64)]
      (is (= "unsupported_platform" (get-in plan [:error :code])))
      (is (str/includes? (get-in plan [:error :message]) "linux/riscv64"))
      (is (str/includes? (get-in plan [:error :message]) "aishell-linux-amd64"))
      (is (nil? (:asset-url plan))))
    (is (= "unsupported_platform"
           (get-in (plan-for :os :windows :arch :aarch64) [:error :code])))))

(deftest upgrade-plan-overwrites-a-unix-script-in-place
  (testing "nothing is deleted; the binary lands on the script's own path"
    (let [plan (plan-for :script-install? true)]
      (is (= [] (:delete-after plan)))
      (is (false? (:rename-old? plan)))
      (is (nil? (:old-path plan)))
      (is (= [] (:notes plan))))))

(deftest upgrade-plan-removes-the-old-script-and-bat-on-a-windows-migration
  (testing "aishell and aishell.bat go, aishell.exe stays, and the user is told"
    (let [plan (plan-for :os :windows :install-path "C:\\bin\\aishell"
                         :script-install? true :bat-present? true)]
      (is (= ["C:\\bin\\aishell" "C:\\bin\\aishell.bat"] (:delete-after plan)))
      (is (false? (:rename-old? plan)))
      (is (= 1 (count (:notes plan))))
      (is (str/includes? (first (:notes plan)) "aishell.bat")))))

(deftest upgrade-plan-renames-a-running-windows-binary-out-of-the-way
  (testing "replacing an existing exe needs the .old dance; a script migration does not"
    (let [plan (plan-for :os :windows :install-path "C:\\bin\\aishell.exe"
                         :script-install? false)]
      (is (true? (:rename-old? plan)))
      (is (= "C:\\bin\\aishell.exe.old" (:old-path plan)))
      (is (= [] (:delete-after plan))))
    (is (false? (:rename-old? (plan-for :script-install? false))))))

(deftest upgrade-plan-still-clears-a-stale-bat-beside-a-windows-binary
  (testing "a leftover shim ahead of aishell.exe on PATH is removed"
    (let [plan (plan-for :os :windows :install-path "C:\\bin\\aishell.exe"
                         :script-install? false :bat-present? true)]
      (is (= ["C:\\bin\\aishell.bat"] (:delete-after plan))))))

(deftest upgrade-plan-uses-pinned-urls-either-way
  (testing "a pinned target and a resolved latest produce identical URLs"
    (let [latest (upgrade/upgrade-plan unix-env)
          pinned (upgrade/upgrade-plan (assoc unix-env :pinned? true))]
      (is (= (:asset-url latest) (:asset-url pinned)))
      (is (= (:checksum-url latest) (:checksum-url pinned))))))

(deftest upgrade-plan-honors-a-releases-url-override
  (testing "both URLs come from :releases-url"
    (let [plan (upgrade/upgrade-plan (assoc unix-env :releases-url "http://localhost:8000/releases"))]
      (is (= "http://localhost:8000/releases/download/v4.1.0/aishell-linux-amd64.tar.gz" (:asset-url plan)))
      (is (= "http://localhost:8000/releases/download/v4.1.0/SHA256SUMS" (:checksum-url plan))))))

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

(deftest detect-env-finds-the-bat-beside-a-migrated-windows-binary
  (testing "aishell.bat sits beside aishell.exe, not at aishell.exe.bat"
    (with-temp-dir
      (fn [dir]
        (let [path (str dir "/aishell.exe")]
          (fs/write-bytes path (byte-array [0x4d 0x5a]))
          (spit (str dir "/aishell.bat") "@echo off\n")
          (with-redefs [upgrade/find-aishell-path (constantly path)]
            (is (true? (:bat-present? (upgrade/detect-env "4.0.0" "4.1.0" {}))))))))))

(deftest detect-env-passes-opts-through
  (testing ":pinned? and :releases-url reach the env"
    (with-redefs [upgrade/find-aishell-path (constantly nil)]
      (let [env (upgrade/detect-env "4.0.0" "3.9.0" {:pinned? true :releases-url "http://localhost/r"})]
        (is (nil? (:install-path env)))
        (is (false? (:script-install? env)))
        (is (true? (:pinned? env)))
        (is (= "http://localhost/r" (:releases-url env)))))))

(defn- stub-downloads
  "Serve download-to-file from an in-memory {url content} map.
   Content is a string, or a byte array for a binary asset such as an archive."
  [responses f]
  (let [serve (fn [url dest]
                (if-let [content (get responses url)]
                  (if (bytes? content)
                    (fs/write-bytes dest content)
                    (spit dest content))
                  (throw (ex-info (str "404 " url) {}))))]
    (with-redefs [upgrade/download-to-file (fn [_ url dest] (serve url dest))
                  upgrade/download-asset! (fn [_ url dest _] (serve url dest))]
      (f))))

(defn- archive-of
  "Bytes of a release-shaped archive holding one file, `member`, with `body`.
   kind is :tar-gz or :zip, the two shapes the release publishes."
  [dir kind member body]
  (let [src (str (fs/create-dirs (str dir "/archive-src")))
        out (str dir "/archive" (case kind :tar-gz ".tar.gz" :zip ".zip"))]
    (spit (str src "/" member) body)
    (case kind
      :tar-gz (p/shell {:out :string :err :string}
                       "tar" "-czf" out "-C" src member)
      :zip (fs/zip out [(str src "/" member)] {:root src}))
    (let [b (fs/read-all-bytes out)]
      (fs/delete-tree src)
      (fs/delete out)
      b)))

(defn- sha256-of-bytes
  "Hash of a byte array as the checksum file would list it."
  [^bytes b]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md b)))))

(deftest extract-member!-unpacks-a-tar-gz-and-a-zip
  (testing "the one file inside lands in the directory under its own name"
    (with-temp-dir
      (fn [dir]
        (doseq [[kind file member] [[:tar-gz "a.tar.gz" "aishell"]
                                    [:zip "a.zip" "aishell.exe"]]]
          (let [archive (str dir "/" file)
                out (str (fs/create-dirs (str dir "/out-" (name kind))))]
            (fs/write-bytes archive (archive-of dir kind member "payload\n"))
            (is (= (str out "/" member) (upgrade/extract-member! archive out member)))
            (is (= "payload\n" (slurp (str out "/" member))))))))))

(deftest extract-member!-fails-when-the-member-is-missing
  (testing "an archive without the expected file is an error, not an empty install"
    (with-temp-dir
      (fn [dir]
        (let [archive (str dir "/a.tar.gz")]
          (fs/write-bytes archive (archive-of dir :tar-gz "something-else" "x\n"))
          (is (thrown? Exception (upgrade/extract-member! archive dir "aishell"))))))))

(deftest execute-plan-installs-a-verified-download
  (testing "the file inside the archive lands at :dest-path after the archive's hash matches"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell")
              body "binary\n"
              archive (archive-of dir :tar-gz "aishell" body)
              plan {:asset-name "aishell-linux-amd64.tar.gz"
                    :asset-url "http://x/a"
                    :checksum-url "http://x/sums"
                    :checksum-asset "aishell-linux-amd64.tar.gz"
                    :member "aishell"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? true
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes []}
              out (stub-downloads {"http://x/a" archive
                                   "http://x/sums" (str (sha256-of-bytes archive)
                                                        "  aishell-linux-amd64.tar.gz\n")}
                                  #(with-out-str (upgrade/execute-plan plan :curl)))]
          (is (= body (slurp dest)))
          (is (fs/executable? dest))
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
              plan {:asset-name "aishell-linux-amd64.tar.gz"
                    :asset-url "http://x/a"
                    :checksum-url "http://x/sums"
                    :checksum-asset "aishell-linux-amd64.tar.gz"
                    :member "aishell"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? false
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
              (stub-downloads {"http://x/a" (archive-of dir :tar-gz "aishell" "tampered\n")
                               "http://x/sums" (str (apply str (repeat 64 "a"))
                                                    "  aishell-linux-amd64.tar.gz\n")}
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
              archive (archive-of dir :zip "aishell.exe" "binary\n")
              plan {:asset-name "aishell-windows-amd64.zip"
                    :asset-url "http://x/a"
                    :checksum-url "http://x/sums"
                    :checksum-asset "aishell-windows-amd64.zip"
                    :member "aishell.exe"
                    :dest-path dest
                    :delete-after [stale (str dir "/never-existed")]
                    :chmod-x? false
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes []}]
          (spit stale "old script\n")
          (stub-downloads {"http://x/a" archive
                           "http://x/sums" (str (sha256-of-bytes archive) "  aishell-windows-amd64.zip\n")}
                          #(with-out-str (upgrade/execute-plan plan :curl)))
          (is (true? (fs/exists? dest)))
          (is (false? (fs/exists? stale))))))))

(deftest execute-plan-prints-the-plan-notes
  (testing "notes reach the user verbatim"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell")
              archive (archive-of dir :tar-gz "aishell" "x\n")
              plan {:asset-name "aishell-linux-amd64.tar.gz"
                    :asset-url "http://x/a"
                    :checksum-url "http://x/sums"
                    :checksum-asset "aishell-linux-amd64.tar.gz"
                    :member "aishell"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? false
                    :current-version "4.0.0"
                    :target-version "4.1.0"
                    :notes ["Removed the old aishell script."]}
              out (stub-downloads {"http://x/a" archive
                                   "http://x/sums" (str (sha256-of-bytes archive) "  aishell-linux-amd64.tar.gz\n")}
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

(deftest execute-plan-renames-the-running-binary-before-installing-over-it
  (testing "Windows locks a running exe: the old file moves to .old, the new one takes its place"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell.exe")
              old (str dest ".old")
              body "new binary\n"
              archive (archive-of dir :zip "aishell.exe" body)]
          (spit dest "running binary\n")
          (stub-downloads {"http://x/a" archive
                           "http://x/sums" (str (sha256-of-bytes archive) "  aishell-windows-amd64.zip\n")}
                          #(with-out-str
                             (upgrade/execute-plan
                              {:asset-name "aishell-windows-amd64.zip"
                               :asset-url "http://x/a"
                               :checksum-url "http://x/sums"
                               :checksum-asset "aishell-windows-amd64.zip"
                               :member "aishell.exe"
                               :dest-path dest
                               :delete-after []
                               :rename-old? true
                               :old-path old
                               :chmod-x? false
                               :current-version "4.1.0"
                               :target-version "4.2.0"
                               :notes []}
                              :curl)))
          (is (= "running binary\n" (slurp old)))
          (is (= body (slurp dest))))))))

(deftest execute-plan-names-the-missing-asset-when-a-download-fails
  (testing "the platform asset name, not the legacy \"aishell\""
    (with-temp-dir
      (fn [dir]
        (let [err (java.io.StringWriter.)]
          (with-redefs [output/exit! (fn [c] (throw (ex-info "exit" {:code c})))]
            (try
              (stub-downloads {}
                              #(binding [*err* err]
                                 (with-out-str
                                   (upgrade/execute-plan
                                    {:asset-name "aishell-macos-aarch64.tar.gz"
                                     :asset-url "http://x/a"
                                     :checksum-url "http://x/sums"
                                     :checksum-asset "aishell-macos-aarch64.tar.gz"
                                     :member "aishell"
                                     :dest-path (str dir "/aishell")
                                     :delete-after []
                                     :chmod-x? false
                                     :current-version "4.0.0"
                                     :target-version "4.1.0"
                                     :notes []}
                                    :curl))))
              (catch clojure.lang.ExceptionInfo _ nil)))
          (is (str/includes? (str err) "aishell-macos-aarch64")))))))

(deftest download-argv-shows-a-meter-on-a-terminal
  (testing "curl -# and wget --show-progress replace the quiet flags"
    (is (= ["curl" "-fSL" "-#" "-o" "/tmp/a" "http://x/a"]
           (upgrade/download-argv :curl "http://x/a" "/tmp/a" true)))
    (is (= ["wget" "-q" "--show-progress" "-O" "/tmp/a" "http://x/a"]
           (upgrade/download-argv :wget "http://x/a" "/tmp/a" true)))))

(deftest download-argv-stays-quiet-off-a-terminal
  (testing "piped output gets no progress meter"
    (is (= ["curl" "-fsSL" "-o" "/tmp/a" "http://x/a"]
           (upgrade/download-argv :curl "http://x/a" "/tmp/a" false)))
    (is (= ["wget" "-q" "-O" "/tmp/a" "http://x/a"]
           (upgrade/download-argv :wget "http://x/a" "/tmp/a" false)))))

(deftest content-length-from-headers-reads-the-last-value
  (testing "a redirect chain ends with the real asset's size"
    (is (= 94371840
           (upgrade/content-length-from-headers
            (str "HTTP/1.1 302 Found\r\ncontent-length: 0\r\n\r\n"
                 "HTTP/1.1 200 OK\r\nContent-Length: 94371840\r\n\r\n"))))
    (is (nil? (upgrade/content-length-from-headers "HTTP/1.1 200 OK\r\n\r\n")))
    (is (nil? (upgrade/content-length-from-headers nil)))))

(deftest describe-size-rounds-to-megabytes
  (testing "a known length becomes MB; an unknown one falls back to the ADR's estimate"
    (is (= "90 MB" (upgrade/describe-size (* 90 1024 1024))))
    (is (= "about 25 MB" (upgrade/describe-size nil)))))

(deftest download-asset!-announces-the-size-when-there-is-no-terminal
  (testing "one line with the asset name and its size, in place of a progress bar"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/asset")
              out (with-redefs [output/tty? (constantly false)
                                upgrade/remote-content-length (constantly (* 90 1024 1024))
                                upgrade/run-download! (fn [_argv] (spit dest "payload"))]
                    (with-out-str
                      (upgrade/download-asset! :curl "http://x/a" dest "aishell-linux-amd64")))]
          (is (str/includes? out "aishell-linux-amd64"))
          (is (str/includes? out "90 MB"))
          (is (= "payload" (slurp dest))))))))

(deftest download-asset!-stays-silent-when-the-meter-will-show
  (testing "on a TTY the progress bar is the only progress output"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/asset")
              seen (atom nil)
              out (with-redefs [output/tty? (constantly true)
                                upgrade/remote-content-length (fn [& _] (throw (ex-info "no HEAD on a TTY" {})))
                                upgrade/run-download! (fn [argv] (reset! seen argv) (spit dest "payload"))]
                    (with-out-str
                      (upgrade/download-asset! :curl "http://x/a" dest "aishell-linux-amd64")))]
          (is (= "" out))
          (is (= ["curl" "-fSL" "-#" "-o" dest "http://x/a"] @seen)))))))

(deftest release-base-url-prefers-an-explicit-override
  (testing "an override wins, a trailing slash goes, and the default is the GitHub releases page"
    (is (= "http://localhost:8000/releases" (upgrade/release-base-url "http://localhost:8000/releases/")))
    (is (= upgrade/releases-url (upgrade/release-base-url nil)))))

(deftest detect-env-carries-the-release-base-url
  (testing "install and upgrade cannot point at different places"
    (with-redefs [upgrade/find-aishell-path (constantly nil)]
      (is (= "http://localhost/r"
             (:releases-url (upgrade/detect-env "4.0.0" "4.1.0" {:releases-url "http://localhost/r"}))))
      (is (= upgrade/releases-url
             (:releases-url (upgrade/detect-env "4.0.0" "4.1.0" {})))))))

(deftest fetch-latest-version-asks-the-configured-release-base
  (testing "the latest lookup follows the same base URL the download will use"
    (let [asked (atom nil)]
      (with-redefs [upgrade/http-head (fn [_ url] (reset! asked url) "")]
        (upgrade/fetch-latest-version :curl "http://localhost:8000/releases")
        (is (= "http://localhost:8000/releases/latest" @asked))))))

(deftest cleanup-stale-old!-deletes-a-leftover-beside-the-install
  (testing "the .old file the previous upgrade could not remove goes on the next start"
    (with-temp-dir
      (fn [dir]
        (let [install (str dir "/aishell.exe")
              old (str install ".old")]
          (spit install "binary\n")
          (spit old "previous\n")
          (upgrade/cleanup-stale-old! install)
          (is (false? (fs/exists? old)))
          (is (true? (fs/exists? install))))))))

(deftest cleanup-stale-old!-is-silent-when-there-is-nothing-to-clean
  (testing "no leftover, an undeletable one, no install at all: never throws, never prints"
    (with-temp-dir
      (fn [dir]
        (let [locked (str dir "/locked")
              install (str locked "/aishell.exe")]
          (fs/create-dirs locked)
          (spit (str install ".old") "previous\n")
          ;; A read-only directory is how a delete fails for real: startup must
          ;; shrug it off rather than keep the CLI from running.
          (fs/set-posix-file-permissions locked "r-xr-xr-x")
          (try
            (is (= "" (with-out-str (upgrade/cleanup-stale-old! install))))
            (is (true? (fs/exists? (str install ".old"))))
            (finally
              (fs/set-posix-file-permissions locked "rwxr-xr-x"))))
        (is (= "" (with-out-str (upgrade/cleanup-stale-old! (str dir "/aishell.exe")))))
        (is (= "" (with-out-str (upgrade/cleanup-stale-old! nil))))))))

(deftest execute-plan-puts-the-parked-binary-back-when-the-install-fails
  (testing "a Windows-shaped upgrade that cannot install must not leave PATH empty"
    (with-temp-dir
      (fn [dir]
        (let [dest (str dir "/aishell.exe")
              err (java.io.StringWriter.)
              exits (atom [])
              body "installed\n"
              archive (archive-of dir :zip "aishell.exe" "new\n")
              plan {:asset-name "aishell-windows-amd64.zip"
                    :asset-url "http://x/aishell-windows-amd64.zip"
                    :checksum-url "http://x/SHA256SUMS"
                    :checksum-asset "aishell-windows-amd64.zip"
                    :member "aishell.exe"
                    :dest-path dest
                    :delete-after []
                    :chmod-x? false
                    :rename-old? true
                    :old-path (str dest ".old")
                    :current-version "4.1.0"
                    :target-version "4.2.0"
                    :notes []}]
          (spit dest body)
          (with-redefs [output/exit! (fn [c]
                                       (swap! exits conj c)
                                       (throw (ex-info "exit" {:code c})))
                        ;; only the unpacked binary fails to install; putting the
                        ;; parked one back must still work
                        upgrade/install-file! (fn [src dst]
                                                (if (not= (fs/parent src) (fs/parent dst))
                                                  (throw (ex-info "disk full" {}))
                                                  (fs/move src dst {:replace-existing true})))]
            (try
              (stub-downloads {"http://x/aishell-windows-amd64.zip" archive
                               "http://x/SHA256SUMS" (str (sha256-of-bytes archive)
                                                          "  aishell-windows-amd64.zip\n")}
                              #(binding [*err* err]
                                 (with-out-str (upgrade/execute-plan plan :curl))))
              (catch clojure.lang.ExceptionInfo _ nil)))
          (is (= [1] @exits))
          (is (str/includes? (str err) "previous version was put back"))
          (is (= body (slurp dest))
              "the parked binary is back where PATH expects it"))))))
