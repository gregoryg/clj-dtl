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
(defn set-dtl-file-path [path]
  (clj-dtl.api/set-dtl-file-path! path)
  (encode {:jobpath clj-dtl.api/dtl-job-path})
)

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/dtl/path" [] (encode {:path dtl-job-path}))
  (GET "/dtl/setpath/:path" [path] (set-dtl-file-path path))
  (GET "/dtl/tasks" [] (encode {:tasks (get-tasks)}))
  (GET "/dtl/tagged-tasks" [] (encode {:tasks (tagged-tasks)}))
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

(defn set-json-content
  "middleware to set media type as application/json"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Content-Type"]
                "application/json; charset=utf-8"))))
      ;; (if (contains? (:headers response) "Content-Type")
      ;;   response
      ;;   (content-type response "application/json; charset=utf-8"))
      ;; response)))

;; (def app
;;   (handler/site app-routes))
(def app
  (->
   (handler/site app-routes)
   (allow-cross-origin)
   (set-json-content)))

  
;; (defn start-server
;;   []
;;   (run-jetty (app) {:port 8888 :join? false}))

;; (defn -main [& args]
;;   (start-server))
