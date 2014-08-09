(ns clj-dtl.handler
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.util.response]
        [ring.middleware file file-info session ]
        [clj-dtl.api]
        )
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [cheshire.core :refer :all]
))



;; from http://kendru.github.io/restful-clojure/2014/02/19/getting-a-web-server-up-and-running-with-compojure-restful-clojure-part-2/
(defn- str-to [num]
  (apply str (interpose ", " (range 1 (inc num)))))

(defn- str-from [num]
  (apply str (interpose ", " (reverse (range 1 (inc num))))))

(defn set-dtl-file-path [path]
  (clj-dtl.api/set-dtl-file-path! path)
  (encode {:jobpath clj-dtl.api/dtl-job-path})
)

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/count-up/:to" [to] (str-to (Integer. to)))
  (GET "/count-down/:from" [from] (str-from (Integer. from)))
  (GET "/dtl/path" [] (encode {:path dtl-job-path}))
  (GET "/dtl/setpath/:path" [path] (set-dtl-file-path path))
  (GET "/dtl/tasks" [] (encode {:tasks (get-tasks)}))
  (GET "/dtl/node-data-array" [] (encode (gojs-node-data-array)))
  (GET "/dtl/link-data-array" [] (encode (gojs-link-data-array)))
  (route/resources "/")
  (route/not-found "Not Found"))

;; (def handler 
;;   (wrap-cors app-routes :access-control-allow-origin #"http://lemoncheeks"
;;                        :access-control-allow-methods [:get :put :post :delete]))
(defn allow-cross-origin
  "middleware to allow cross origin"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Access-Control-Allow-Origin"]
                "*"))))

;; (def app
;;   (handler/site app-routes))
(def app
  (->
   (handler/site app-routes)
   (allow-cross-origin)))
  
;; (defn start-server
;;   []
;;   (run-jetty (app) {:port 8888 :join? false}))

;; (defn -main [& args]
;;   (start-server))
