(ns cljfmt-js.core
  "Public API of cljfmt-js. Pure library: no filesystem access, no config
  discovery — the consumer owns both.

  Configs are opaque handles (ClojureScript maps). They deliberately never
  cross the JS boundary as plain objects, so regex and symbol keys survive."
  (:require [cljfmt.core :as cljfmt]
            [cljs.tools.reader.edn :as edn])
  (:require-macros [cljfmt-js.macros :as macros]))

(def default-config
  "cljfmt's default options."
  cljfmt/default-options)

(def cljfmt-version
  "Version of the bundled cljfmt, inlined from deps.edn at compile time."
  (macros/cljfmt-version))

(defn- convert-legacy-keys
  "Port of `cljfmt.config/convert-legacy-keys` — `:legacy/merge-indents? true`
  means the config's `:indents` add to cljfmt's defaults rather than replacing
  them, which cljfmt expresses as `:extra-indents`."
  [config]
  (cond-> config
    (:legacy/merge-indents? config)
    (-> (assoc :extra-indents (:indents config))
        (dissoc :legacy/merge-indents? :indents))))

(defn read-config
  "Parse the contents of a `.cljfmt.edn` / `cljfmt.edn` file. Supports the
  `#re` tag and applies cljfmt's legacy-key conversion. Throws on invalid EDN."
  [edn-string]
  (-> (edn/read-string {:readers {'re re-pattern}} edn-string)
      convert-legacy-keys))

(defn merge-config
  "Merge two configs; keys in `override` win."
  [base override]
  (merge base override))

(defn read-ns-context
  "Placeholder — implemented in Task 4."
  [_source]
  {})

(defn reformat-string
  "Format `source` the way JVM cljfmt would. Throws when `source` cannot be
  parsed. `config` defaults to cljfmt's defaults."
  ([source] (reformat-string source nil))
  ([source config]
   (cljfmt/reformat-string source (or config default-config))))
