(ns cljfmt-js.ns-aliases-test
  (:require [cljfmt-js.core :as sut]
            [cljs.test :refer-macros [deftest is testing]]))

;; `mything*` rather than `defthing*`: cljfmt's default indents already carry
;; #"^def(?!ault)(?!late)(?!er)" [[:inner 0]], so a `def`-prefixed name would
;; indent correctly even when alias resolution does nothing.
(def config
  (sut/read-config
   (str "{:extra-indents {my.lib/mything     [[:inner 0]]"
        "                 other.lib/mything2 [[:inner 0]]"
        "                 app.core/mything3  [[:inner 0]]}}")))

(def whole-source
  (str "(ns app.core\n"
       "  (:require [my.lib :as ml] [other.lib :refer [mything2]]))\n\n"
       "(ml/mything x\n(inc x))\n\n"
       "(mything2 y\n(dec y))\n\n"
       "(mything3 z\n(inc z))\n"))

;; --------------------------------------------------------- read-ns-context

(deftest read-ns-context-derives-aliases-refers-and-name
  (let [ctx (sut/read-ns-context whole-source)]
    (is (= {"ml" "my.lib"} (:alias-map ctx)))
    (is (= {"mything2" "other.lib"} (:refer-map ctx)))
    (is (= 'app.core (:ns-name ctx)))))

(deftest read-ns-context-is-empty-without-an-ns-form
  (let [ctx (sut/read-ns-context "(mything2 y\n(dec y))")]
    (is (= {} (:alias-map ctx)))
    (is (= {} (:refer-map ctx)))
    (is (nil? (:ns-name ctx)))))

(deftest read-ns-context-ignores-a-non-top-level-ns-form
  (let [ctx (sut/read-ns-context
             "(comment\n  (ns sneaky.core (:require [my.lib :as ml])))")]
    (is (= {} (:alias-map ctx)))
    (is (nil? (:ns-name ctx)))))

;; ------------------------------------------------ whole file (JVM parity)

(deftest whole-file-resolves-aliases-refers-and-the-current-ns
  (is (= (str "(ns app.core\n"
              "  (:require [my.lib :as ml] [other.lib :refer [mything2]]))\n\n"
              "(ml/mything x\n  (inc x))\n\n"
              "(mything2 y\n  (dec y))\n\n"
              "(mything3 z\n  (inc z))\n")
         (sut/reformat-string whole-source config))))

;; ----------------------------------------------------- windows (sub-forms)

(deftest a-window-without-context-cannot-resolve-anything
  (testing "documents why read-ns-context matters: bodies align under arg 1"
    (is (= "(ml/mything x\n            (inc x))"
           (sut/reformat-string "(ml/mything x\n(inc x))" config)))
    (is (= "(mything2 y\n          (dec y))"
           (sut/reformat-string "(mything2 y\n(dec y))" config)))
    (is (= "(mything3 z\n          (inc z))"
           (sut/reformat-string "(mything3 z\n(inc z))" config)))))

(deftest a-window-with-context-formats-like-the-whole-file
  (let [ctx (sut/read-ns-context whole-source)]
    (testing "alias"
      (is (= "(ml/mything x\n  (inc x))"
             (sut/reformat-string "(ml/mything x\n(inc x))" config ctx))))
    (testing "refer"
      (is (= "(mything2 y\n  (dec y))"
             (sut/reformat-string "(mything2 y\n(dec y))" config ctx))))
    (testing "the file's own namespace"
      (is (= "(mything3 z\n  (inc z))"
             (sut/reformat-string "(mything3 z\n(inc z))" config ctx))))
    (testing "the window's own leading whitespace is preserved"
      (is (= "  (mything3 z\n    (inc z))"
             (sut/reformat-string "  (mything3 z\n(inc z))" config ctx))))))

;; -------------------------------------------------------------- precedence

(deftest config-alias-map-overrides-the-derived-one
  (let [override (sut/merge-config
                  config (sut/read-config "{:alias-map {ml not.my.lib}}"))]
    (is (= (str "(ns app.core\n"
                "  (:require [my.lib :as ml] [other.lib :refer [mything2]]))\n\n"
                "(ml/mything x\n            (inc x))\n\n"
                "(mything2 y\n  (dec y))\n\n"
                "(mything3 z\n  (inc z))\n")
           (sut/reformat-string whole-source override)))))

(deftest a-real-refer-beats-the-current-ns-fallback
  (testing "mything2 refers other.lib, so it must not resolve to app.core"
    (let [ctx (sut/read-ns-context whole-source)]
      (is (= "(mything2 y\n  (dec y))"
             (sut/reformat-string "(mything2 y\n(dec y))" config ctx))))))
