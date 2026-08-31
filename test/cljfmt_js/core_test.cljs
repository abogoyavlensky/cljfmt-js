(ns cljfmt-js.core-test
  (:require [cljfmt.core :as cljfmt]
            [cljfmt-js.core :as sut]
            [cljs.test :refer-macros [deftest is testing]]))

;; ---------------------------------------------------------------- read-config

(deftest read-config-reads-re-tagged-patterns
  (let [config (sut/read-config "{:extra-indents {#re \"^my-\" [[:inner 0]]}}")
        [k v]  (first (:extra-indents config))]
    (is (instance? js/RegExp k))
    (is (= "^my-" (.-source k)))
    (is (= [[:inner 0]] v))))

(deftest read-config-converts-legacy-keys
  (let [config (sut/read-config
                "{:legacy/merge-indents? true :indents {foo [[:inner 0]]}}")]
    (is (= {:extra-indents '{foo [[:inner 0]]}} config))
    (is (not (contains? config :indents)))
    (is (not (contains? config :legacy/merge-indents?)))))

(deftest read-config-leaves-non-legacy-configs-alone
  (is (= {:function-arguments-indentation :cursive}
         (sut/read-config "{:function-arguments-indentation :cursive}"))))

(deftest read-config-throws-on-invalid-edn
  (is (thrown? js/Error (sut/read-config "{:a"))))

;; ------------------------------------------------- default-config/merge-config

(deftest default-config-is-cljfmts-defaults
  (is (= cljfmt/default-options sut/default-config)))

(deftest merge-config-lets-the-override-win
  (is (= {:a 1 :b 3 :c 4}
         (sut/merge-config {:a 1 :b 2} {:b 3 :c 4}))))

;; ------------------------------------------------------------ reformat-string

(deftest reformat-string-uses-cljfmt-defaults
  (is (= "(foo bar\n     baz)" (sut/reformat-string "(foo bar\nbaz)")))
  (is (= "(foo bar\n     baz)" (sut/reformat-string "(foo bar\nbaz)" nil))))

(deftest reformat-string-honours-the-config
  (is (= "(foo\n  bar)"
         (sut/reformat-string
          "(foo\nbar)"
          (sut/read-config "{:function-arguments-indentation :cursive}"))))
  (testing "an :extra-indents regex key that cljfmt has no default for"
    (is (= "(my-x y\n      z)" (sut/reformat-string "(my-x y\nz)"))
        "baseline: without the config the body aligns under the first argument")
    (is (= "(my-x y\n  z)"
           (sut/reformat-string
            "(my-x y\nz)"
            (sut/read-config "{:extra-indents {#re \"^my-\" [[:inner 0]]}}"))))))

(deftest reformat-string-throws-on-unbalanced-source
  (is (thrown? js/Error (sut/reformat-string "(foo"))))

;; -------------------------------------------------------------- cljfmt-version

(deftest cljfmt-version-is-the-bundled-version
  (is (re-matches #"^\d+\.\d+\.\d+$" sut/cljfmt-version))
  ;; Bumped by the weekly cljfmt-update PR, together with deps.edn.
  (is (= "0.16.5" sut/cljfmt-version)))
