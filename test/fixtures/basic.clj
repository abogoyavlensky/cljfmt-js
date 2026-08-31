(ns fixtures.basic
  "A deliberately messy namespace: every top-level form below is mis-indented
   or carries whitespace noise, so the formatter has real work to do."
  (:require [clojure.string :as str]))

(def ^:private separator-re
#"[,;]\s*")

(def escape-table
{\newline "\\n"
 \tab   "\\t"
 \" "\\\""})

(defn parse-list
"Split `s` on commas or semicolons, dropping blanks."
[s]
(->> (str/split s separator-re)
(map str/trim)
(remove str/blank?)
(vec)))

(defn classify [n]
(cond
(neg? n) :negative
(zero? n)  :zero
:else :positive))

(defn describe [{:keys [name count]
:or {count 0}}]
(let [label (if (= 1 count)
"item"
"items")]
(str name ": " count " " label)))

(defn- summarise [xs]
(when (seq xs)
(let [total (reduce + xs)
      mean (/ total (double (count xs)))]
{:total total
 :mean mean
 :size (count xs)})))

(def known-tags
#{:alpha  :beta
  :gamma})

#_(defn dead-code [x]
(inc x))

(defn render [xs]
;; a line comment inside a body
(doseq [x xs]
(println (describe x))))

(comment
(parse-list "a, b; c")
(classify -1))
