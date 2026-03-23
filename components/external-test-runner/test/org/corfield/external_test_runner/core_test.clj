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
