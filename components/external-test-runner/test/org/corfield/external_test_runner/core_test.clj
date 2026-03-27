(ns org.corfield.external-test-runner.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.corfield.external-test-runner.core :as core]))

(deftest dummy-test
  (is (= 1 1)))

(deftest merge-shadow-defaults-test
  (testing "no defaults — builds returned unchanged"
    (let [shadow {:builds {:test {:target :node-test :output-to "out/test.js"}}}]
      (is (= shadow (core/merge-shadow-defaults shadow)))))

  (testing ":build-defaults merged as base into every build"
    (let [shadow {:build-defaults {:output-dir "out"}
                  :builds         {:test {:target :node-test :output-to "out/test.js"}}}]
      (is (= {:output-dir "out" :target :node-test :output-to "out/test.js"}
             (-> shadow core/merge-shadow-defaults :builds :test)))))

  (testing ":target-defaults merged per build target"
    (let [shadow {:target-defaults {:node-test {:autorun true}}
                  :builds          {:test {:target :node-test :output-to "out/test.js"}}}]
      (is (= {:autorun true :target :node-test :output-to "out/test.js"}
             (-> shadow core/merge-shadow-defaults :builds :test)))))

  (testing ":target-defaults only applied to builds with matching target"
    (let [shadow {:target-defaults {:karma {:single-run true}}
                  :builds          {:test {:target :node-test :output-to "out/test.js"}}}]
      (is (= {:target :node-test :output-to "out/test.js"}
             (-> shadow core/merge-shadow-defaults :builds :test)))))

  (testing "build-specific settings win over build-defaults and target-defaults"
    (let [shadow {:build-defaults  {:autorun false :output-dir "out"}
                  :target-defaults {:node-test {:autorun true}}
                  :builds          {:test {:target :node-test :output-to "out/test.js" :autorun false}}}]
      ;; build-specific :autorun false wins over target-defaults :autorun true
      (is (= {:autorun false :output-dir "out" :target :node-test :output-to "out/test.js"}
             (-> shadow core/merge-shadow-defaults :builds :test)))))

  (testing "multiple builds each get their own target-defaults applied"
    (let [shadow {:build-defaults  {:source-paths ["src"]}
                  :target-defaults {:node-test {:autorun true}
                                    :karma     {:single-run true}}
                  :builds          {:node {:target :node-test :output-to "out/node.js"}
                                    :ci   {:target :karma    :output-to "out/ci.js"}}}
          result (-> shadow core/merge-shadow-defaults :builds)]
      (is (= {:source-paths ["src"] :autorun true  :target :node-test :output-to "out/node.js"}
             (:node result)))
      (is (= {:source-paths ["src"] :single-run true :target :karma :output-to "out/ci.js"}
             (:ci result)))))

  (testing ":build-defaults and :target-defaults preserved at top level"
    (let [shadow {:build-defaults  {:output-dir "out"}
                  :target-defaults {:node-test {:autorun true}}
                  :builds          {:test {:target :node-test :output-to "out/test.js"}}}
          result (core/merge-shadow-defaults shadow)]
      (is (= {:output-dir "out"} (:build-defaults result)))
      (is (= {:node-test {:autorun true}} (:target-defaults result))))))

(deftest parse-test-shard-test
  (testing "valid shard specs"
    (is (= [1 4] (core/parse-test-shard "1/4")))
    (is (= [2 4] (core/parse-test-shard "2/4")))
    (is (= [4 4] (core/parse-test-shard "4/4")))
    (is (= [1 1] (core/parse-test-shard "1/1"))))

  (testing "invalid shard specs return nil"
    (is (nil? (core/parse-test-shard nil)))
    (is (nil? (core/parse-test-shard "")))
    (is (nil? (core/parse-test-shard "abc")))
    (is (nil? (core/parse-test-shard "0/4")))
    (is (nil? (core/parse-test-shard "5/4")))
    (is (nil? (core/parse-test-shard "1/0")))))

(deftest shard-namespaces-test
  (testing "splits namespaces across shards round-robin"
    (let [nses ["d.ns" "b.ns" "a.ns" "c.ns" "e.ns" "f.ns"]]
      ;; sorted: ["a.ns" "b.ns" "c.ns" "d.ns" "e.ns" "f.ns"]
      ;; shard 1/3: indices 0, 3 -> ["a.ns" "d.ns"]
      ;; shard 2/3: indices 1, 4 -> ["b.ns" "e.ns"]
      ;; shard 3/3: indices 2, 5 -> ["c.ns" "f.ns"]
      (is (= ["a.ns" "d.ns"] (core/shard-namespaces nses [1 3])))
      (is (= ["b.ns" "e.ns"] (core/shard-namespaces nses [2 3])))
      (is (= ["c.ns" "f.ns"] (core/shard-namespaces nses [3 3])))))

  (testing "all shards together cover all namespaces"
    (let [nses ["ns1" "ns2" "ns3" "ns4" "ns5"]
          all-sharded (into [] cat [(core/shard-namespaces nses [1 3])
                                    (core/shard-namespaces nses [2 3])
                                    (core/shard-namespaces nses [3 3])])]
      (is (= (sort nses) (sort all-sharded)))))

  (testing "single shard returns all namespaces sorted"
    (let [nses ["c" "a" "b"]]
      (is (= ["a" "b" "c"] (core/shard-namespaces nses [1 1])))))

  (testing "empty namespaces"
    (is (= [] (core/shard-namespaces [] [1 3]))))

  (testing "fewer namespaces than shards"
    (let [nses ["a" "b"]]
      (is (= ["a"] (core/shard-namespaces nses [1 3])))
      (is (= ["b"] (core/shard-namespaces nses [2 3])))
      (is (= []    (core/shard-namespaces nses [3 3]))))))

(deftest shard-integration-test
  (let [make-nses (fn [shard-spec]
                    (delay
                      (cond-> (into [] cat [["c.ns" "a.ns"] ["b.ns" "d.ns" "e.ns"]])
                        shard-spec (core/shard-namespaces shard-spec))))]

    (testing "sharding with delay+cond-> matches create function pattern"
      (is (= ["a.ns" "d.ns"] @(make-nses [1 3])))
      (is (= ["b.ns" "e.ns"] @(make-nses [2 3])))
      (is (= ["c.ns"]        @(make-nses [3 3]))))

    (testing "nil shard-spec preserves original order (no sharding)"
      (is (= ["c.ns" "a.ns" "b.ns" "d.ns" "e.ns"] @(make-nses nil))))

    (testing "all shards cover all namespaces"
      (is (= (sort ["c.ns" "a.ns" "b.ns" "d.ns" "e.ns"])
             (sort (concat @(make-nses [1 3])
                           @(make-nses [2 3])
                           @(make-nses [3 3]))))))))
