(ns cljfmt-js.core
  "Public API of cljfmt-js. Pure library: no filesystem access, no config
  discovery — the consumer owns both.

  Configs are opaque handles (ClojureScript maps). They deliberately never
  cross the JS boundary as plain objects, so regex and symbol keys survive."
  (:require [cljfmt.core :as cljfmt]
            [cljfmt-js.ns-aliases :as ns-aliases]
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
  "Derive the aliases, refers and namespace name from a whole file's `ns` form.

  Pass the result to `reformat-string` when formatting a sub-form (a window)
  that does not contain the `ns` form itself — on Enter, say. Without it a
  window cannot resolve `ml/mything` or an unqualified name against a
  namespace-qualified indent key, and the result diverges from the CLI."
  [source]
  (ns-aliases/derive-context source))

(defn- effective-options
  "cljfmt's own precedence: values derived from the source being formatted
  first, then the caller's ns context, then the user's config."
  [source config ns-context]
  (let [derived (ns-aliases/derive-context source)
        ;; cljfmt finds the namespace itself whenever the source carries the
        ;; ns form; the context only fills in for a window that does not.
        ns-name  (when-not (:ns-name derived) (:ns-name ns-context))]
    (assoc config
           :alias-map (merge (:alias-map derived)
                             (ns-aliases/stringify-map (:alias-map ns-context))
                             (ns-aliases/stringify-map (:alias-map config)))
           :refer-map (merge (when ns-name (ns-aliases/ns-name-refers source ns-name))
                             (:refer-map derived)
                             (ns-aliases/stringify-map (:refer-map ns-context))
                             (ns-aliases/stringify-map (:refer-map config))))))

(defn reformat-string
  "Format `source` the way JVM cljfmt would. Throws when `source` cannot be
  parsed. `config` defaults to cljfmt's defaults; `ns-context` supplies the
  namespace information of the surrounding file when `source` is a window."
  ([source] (reformat-string source nil nil))
  ([source config] (reformat-string source config nil))
  ([source config ns-context]
   (cljfmt/reformat-string
    source
    (effective-options source (or config default-config) ns-context))))
