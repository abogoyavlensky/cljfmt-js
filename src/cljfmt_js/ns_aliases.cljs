(ns cljfmt-js.ns-aliases
  "Namespace-context derivation for the ClojureScript build.

  cljfmt 0.16.5 parses the `ns` form on the JVM only: `alias-map-for-form`,
  `refer-map-for-form` and their helpers sit behind `#?(:clj …)`, and on
  ClojureScript `reformat-form` uses nothing but the `:alias-map` / `:refer-map`
  passed in the options. A plain JS build therefore diverges from the CLI for
  any config with a namespace-qualified indent key.

  The functions below are ports of those `:clj`-only definitions — see NOTICE.
  rewrite-clj is cross-platform, so the reader conditionals are simply dropped."
  (:require [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

;; --- ported from cljfmt.core (EPL-1.0, © James Reeves) ---------------------

(defn- find-all [zloc p?]
  (loop [matches []
         zloc    zloc]
    (if-let [zloc (z/find-next zloc z/next* p?)]
      (recur (conj matches zloc) (z/next* zloc))
      matches)))

(defn- root? [zloc]
  (nil? (z/up* zloc)))

(defn- root [zloc]
  (if (root? zloc) zloc (recur (z/up zloc))))

(defn- top? [zloc]
  (some-> zloc z/up root?))

(defn- meta? [zloc]
  (#{:meta :meta*} (z/tag zloc)))

(defn- skip-meta [zloc]
  (if (meta? zloc)
    (-> zloc z/down z/right recur)
    zloc))

(defn- token? [zloc]
  (= (z/tag zloc) :token))

(defn- ns-token? [zloc]
  (and (token? zloc) (= 'ns (z/sexpr zloc))))

(defn- ns-form? [zloc]
  (and (top? zloc)
       (= (z/tag zloc) :list)
       (some-> zloc z/down ns-token?)))

(defn- top-level-form [zloc]
  (->> zloc (iterate z/up) (take-while (complement root?)) last))

(defn- symbol-node? [zloc]
  (some-> zloc z/node n/symbol-node?))

(defn- leftmost-symbol [zloc]
  (some-> zloc z/leftmost (z/find (comp symbol-node? skip-meta))))

(defn- ns-require-form? [zloc]
  (and (some-> zloc top-level-form ns-form?)
       (some-> zloc z/child-sexprs first (= :require))))

(defn- as-keyword? [zloc]
  (and (token? zloc) (= :as (z/sexpr zloc))))

(defn- refer-keyword? [zloc]
  (and (token? zloc) (= :refer (z/sexpr zloc))))

(defn- ns-require-form-parent [grandparent-node]
  (when-not (ns-require-form? grandparent-node)
    (when (or (z/vector? grandparent-node)
              (z/list? grandparent-node))
      (some-> (z/find (-> grandparent-node z/down skip-meta)
                      (comp skip-meta z/right)
                      symbol-node?)
              z/sexpr))))

(defn- join-ns-str [parent-namespace current-ns]
  ;; cljfmt uses (format "%s.%s" …); cljs has no format.
  (if parent-namespace
    (str parent-namespace "." current-ns)
    (str current-ns)))

(defn- grandparent [zloc]
  (some-> zloc
          (z/find-next z/up (complement meta?))
          (z/find-next z/up (complement meta?))))

(defn- refer-zloc->refer-mapping [refer-zloc]
  (let [refers     (some-> refer-zloc
                           (z/find-next (comp skip-meta z/right)
                                        (some-fn z/vector? z/list?))
                           z/sexpr)
        current-ns (some-> refer-zloc leftmost-symbol z/sexpr)
        parent-ns  (ns-require-form-parent (grandparent refer-zloc))]
    (when (and (sequential? refers) (symbol? current-ns))
      (let [ns-str (join-ns-str parent-ns current-ns)]
        (->> refers (map (fn [sym] [(str sym) ns-str])) (into {}))))))

(defn- refer-map-for-form [form]
  (when-let [req-zloc (-> form z/of-node (z/find z/next ns-require-form?))]
    (->> (find-all req-zloc refer-keyword?)
         (map refer-zloc->refer-mapping)
         (apply merge))))

(defn- as-zloc->alias-mapping [as-zloc]
  (let [alias      (some-> as-zloc
                           (z/find-next (comp skip-meta z/right) symbol-node?)
                           z/sexpr)
        current-ns (some-> as-zloc leftmost-symbol z/sexpr)
        parent-ns  (ns-require-form-parent (grandparent as-zloc))]
    (when (and (symbol? alias) (symbol? current-ns))
      {(str alias) (join-ns-str parent-ns current-ns)})))

(defn- alias-map-for-form [form]
  (when-let [req-zloc (-> form z/of-node (z/find z/next ns-require-form?))]
    (->> (find-all req-zloc as-keyword?)
         (map as-zloc->alias-mapping)
         (apply merge))))

(defn- find-namespace [zloc]
  (some-> zloc root z/down (z/find z/right ns-form?) z/down z/next z/sexpr))

(defn stringify-map
  "Port of cljfmt.core/stringify-map. Must be applied to each map *before*
  merging: cljfmt merges the derived (already-string) map with the stringified
  option map, so stringifying after the merge would leave symbol and string
  keys for the same alias fighting over map order."
  [m]
  (into {} (map (fn [[k v]] [(str k) (str v)])) m))

;; --- cljfmt-js -------------------------------------------------------------

(defn derive-context
  "Aliases, refers and the namespace name declared by `source`'s `ns` form.
  Empty maps and a nil `:ns-name` when there is no top-level `ns` form."
  [source]
  (let [form (p/parse-string-all source)]
    {:alias-map (or (alias-map-for-form form) {})
     :refer-map (or (refer-map-for-form form) {})
     :ns-name   (find-namespace (z/of-node form))}))

(defn ns-name-refers
  "A `:refer-map` that reproduces cljfmt's current-namespace fallback for a
  window that has no `ns` form of its own.

  cljfmt qualifies any otherwise-unresolved symbol with the current namespace
  (`qualify-symbol-by-ns-name`), but it only ever learns that namespace by
  finding an `ns` form in the source it is handed: `reformat-form` overwrites
  its internal `::ns-name` option unconditionally, so that option cannot be
  supplied from outside — not on ClojureScript and not on the JVM either.

  `:refer-map` is consulted for exactly the same symbols, one step earlier in
  `fully-qualified-symbol`. Mapping every unqualified symbol in `source` to
  `ns-name` at the *lowest* precedence is therefore equivalent: genuine refers
  and aliases still win, and everything else resolves to the current namespace."
  [source ns-name]
  (let [ns-str (str ns-name)]
    (->> (find-all (z/of-node (p/parse-string-all source)) symbol-node?)
         (map z/sexpr)
         (remove namespace)
         (reduce (fn [m sym] (assoc m (str sym) ns-str)) {}))))
