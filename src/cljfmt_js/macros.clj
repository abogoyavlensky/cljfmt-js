(ns cljfmt-js.macros
  "Compile-time helpers. Keeping the cljfmt version in deps.edn only, and
  inlining it here, means there is exactly one place to bump."
  (:require [clojure.edn :as edn]))

(defmacro cljfmt-version
  "Inline the version of `dev.weavejester/cljfmt` declared in deps.edn."
  []
  (or (-> (slurp "deps.edn")
          edn/read-string
          :deps
          (get 'dev.weavejester/cljfmt)
          :mvn/version)
      (throw (ex-info "dev.weavejester/cljfmt not found in deps.edn" {}))))
