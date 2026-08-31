(ns parity.jvm
  "JVM half of the differential parity harness — the oracle.

  Formats every test/fixtures file under every test/fixtures/configs file with
  the real cljfmt, reading each config exactly the way the CLI does
  (`cljfmt.config/load-config`), and writes the results to target/parity/jvm.
  scripts/parity/js.mjs does the same through dist/cljfmt.js; `bb parity`
  diffs the two trees."
  (:require [cljfmt.config :as config]
            [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def fixtures-dir "test/fixtures")
(def configs-dir "test/fixtures/configs")
(def out-dir "target/parity/jvm")

(defn- files-in [dir pred]
  (->> (.listFiles (io/file dir))
       (filter #(.isFile ^java.io.File %))
       (filter #(pred (.getName ^java.io.File %)))
       (sort-by #(.getName ^java.io.File %))))

(defn -main [& _]
  (let [fixtures (files-in fixtures-dir #(re-find #"\.cljc?$" %))
        configs  (files-in configs-dir #(str/ends-with? % ".edn"))]
    (when (empty? fixtures) (throw (ex-info "no fixtures found" {})))
    (when (empty? configs) (throw (ex-info "no configs found" {})))
    (.mkdirs (io/file out-dir))
    (doseq [fixture fixtures
            config  configs]
      (let [opts (config/load-config (.getPath config))
            name (str (.getName fixture) "__"
                      (str/replace (.getName config) #"\.edn$" "") ".out")]
        (spit (io/file out-dir name)
              (cljfmt/reformat-string (slurp fixture) opts))))
    (println "parity/jvm:" (* (count fixtures) (count configs)) "files written to" out-dir)))
