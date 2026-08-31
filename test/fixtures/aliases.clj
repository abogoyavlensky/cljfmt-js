(ns fixtures.aliases
  (:require [my.lib :as ml]
            [other.lib :refer [mything2 with-thing]]
            [clojure.set :as set]))

;; Heads reached through an alias, a refer, and this file's own namespace.
;; extra_indents.edn qualifies indent keys against all three.

(ml/mything alpha
(println alpha)
(inc alpha))

(ml/mything beta
{:a 1
 :b 2})

(mything2 gamma
(println gamma))

(with-thing [res (ml/open)]
(println res))

(defn mything3-user []
(mything3 delta
(println delta)))

(mything3 epsilon
(let [a 1
b 2]
(+ a b)))

(defn intersecting [a b]
(set/intersection (set a)
(set b)))

(ml/mything zeta
(ml/mything eta
(println eta)))
