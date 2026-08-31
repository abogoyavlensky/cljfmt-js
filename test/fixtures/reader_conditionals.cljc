(ns fixtures.reader-conditionals
  (:require #?(:clj [clojure.java.io :as io]
               :cljs [goog.string :as gstring])
            [clojure.string :as str]))

(def platform
#?(:clj :jvm
:cljs :browser
:default :unknown))

(defn ^:private fmt [s & args]
#?(:clj (apply format s args)
:cljs (apply gstring/format s args)))

(defn slurp-or-nil [p]
#?(:clj (try (slurp (io/file p))
(catch Exception _ nil))
:cljs nil))

(defn arity
([] (arity 0))
([a] (arity a 1))
([a b]
(+ a b))
([a b & more]
(apply + a b more)))

(def ^{:doc "metadata on a def"
:added "0.1"}
documented 42)

(defn upper [s]
#?@(:clj [(str/upper-case s)]
:cljs [(str/upper-case s)]))
