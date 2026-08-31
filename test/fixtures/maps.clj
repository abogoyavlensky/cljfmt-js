(ns fixtures.maps)

(def config {:server {:host "localhost" :port 8080 :threads 4}
:database {:url "jdbc:postgresql://localhost/db" :user "app" :pool-size 10}
:logging {:level :info :appenders [:console :file]}})

(def nested
{:a {:b {:c {:d 1 :e 2}
:f 3}
:g 4}
 :h 5})

(def routes
[{:path "/" :handler :index :methods #{:get}}
{:path "/users" :handler :users :methods #{:get :post}}
{:path "/users/:id" :handler :user :methods #{:get :put :delete}}])

(defn build [opts]
(merge {:retries 3 :timeout-ms 1000}
opts
{:built-at :now}))

(def sparse {:only-one-key "value"})

(def empty-ish {})
