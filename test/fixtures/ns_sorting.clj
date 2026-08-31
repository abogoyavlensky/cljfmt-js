(ns fixtures.ns-sorting
  (:require [zebra.core :as zebra]
            [alpha.core :as alpha]
            [middle.core :refer [thing other-thing]]
            [beta.core]
            clojure.string)
  (:import (java.util Date UUID ArrayList)
           (java.io File)))

(defn run [x]
(alpha/go x)
(zebra/stop x)
(thing x)
(other-thing x))

(defn make-date []
(Date.))

(defn make-id []
(UUID/randomUUID))

(defn listing []
(ArrayList.))

(defn file [p]
(File. ^String p))
