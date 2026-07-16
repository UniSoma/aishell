(ns aishell.docker.naming-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [aishell.util :as util]
            [aishell.docker.naming :as naming]))

(deftest claude-state-paths-built-on-state-dir-and-hash
  (let [tmp-state (str (fs/create-temp-dir))
        tmp-proj (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/state-dir (fn [] tmp-state)]
        (let [hash (naming/project-hash tmp-proj)]
          (testing "state root is {state-dir}/claude/{project-hash}"
            (is (= (str (fs/path tmp-state "claude" hash))
                   (naming/claude-state-root tmp-proj))))
          (testing "dot-claude dir is under the state root"
            (is (= (str (fs/path tmp-state "claude" hash "dot-claude"))
                   (naming/claude-dot-claude-dir tmp-proj))))
          (testing "meta.edn is beside dot-claude"
            (is (= (str (fs/path tmp-state "claude" hash "meta.edn"))
                   (naming/claude-meta-file tmp-proj))))))
      (finally
        (fs/delete-tree tmp-state)
        (fs/delete-tree tmp-proj)))))

(deftest distinct-projects-get-distinct-state-roots
  (let [tmp-state (str (fs/create-temp-dir))
        proj-a (str (fs/create-temp-dir))
        proj-b (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/state-dir (fn [] tmp-state)]
        (is (not= (naming/claude-state-root proj-a)
                  (naming/claude-state-root proj-b))))
      (finally
        (fs/delete-tree tmp-state)
        (fs/delete-tree proj-a)
        (fs/delete-tree proj-b)))))
