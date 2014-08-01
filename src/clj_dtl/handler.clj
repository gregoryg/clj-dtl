(ns clj-dtl.handler
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.util.response]
        [ring.middleware file file-info session ]
        )
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
))



(defroutes app-routes
  (GET "/" [] "Hello World")
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
 (handler/site app-routes))

(defn start-server
  []
  (run-jetty (app) {:port 8888 :join? false}))

(defn -main [& args]
  (start-server))
