(ns cljfmt-js.core
  "Public API of cljfmt-js. Pure library: no filesystem access, no config
  discovery — the consumer owns both."
  (:require [cljfmt.core :as cljfmt]))

(def default-config
  "cljfmt's default options, as an opaque handle."
  cljfmt/default-options)

(defn read-config
  "Placeholder — implemented in Task 2."
  [_edn]
  default-config)

(defn merge-config
  "Placeholder — implemented in Task 2."
  [base override]
  (merge base override))

(defn read-ns-context
  "Placeholder — implemented in Task 4."
  [_source]
  {})

(defn reformat-string
  "Format `source` the way JVM cljfmt would."
  ([source] (reformat-string source nil))
  ([source config]
   (cljfmt/reformat-string source (or config default-config))))

(def cljfmt-version
  "Placeholder — implemented in Task 2."
  "0.16.5")
