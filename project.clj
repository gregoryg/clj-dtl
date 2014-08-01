(defproject clj-dtl "0.1.0-SNAPSHOT"
  :description "Restful DTL thang"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [ring/ring-core "1.3.0"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [cheshire "5.3.1"]
                 ]
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler clj-dtl.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
