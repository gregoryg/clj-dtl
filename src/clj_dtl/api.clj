(ns clj-dtl.api
  (:require [cheshire.core :refer :all]))

(def dtl-job-path "/home/gregj/Copy/projects/programming/syncsort-dtl/dtl-projects/sessionize-weblogs/sessionize-weblogs-job.dtl")

(defn set-dtl-file-path! [path]
    (def dtl-job-path path))

(defn project-dir 
  "Project directory (directory of the job file)"
  [path]
  (.getParent (java.io.File. path)))

(defn get-links []
  (map #(into {} {:from (nth % 2) :to (nth % 3) :mapreduce (string? (nth % 1))}) 
       (re-seq #"\s*(/MAPREDUCE)?\s*/?FLOW\s+([^ \n]+)\s+([^ \n]+)" 
               (slurp dtl-job-path))))

(defn get-tasks 
  "Return all tasks (nodes) in current job."
  []
  (map #(nth % 1) (re-seq #"\s*/TASK\s+FILE\s+([^ \n]+)" (slurp dtl-job-path))))

(defn mapreduce-link
  "Return links which are mapreduce sequences"
  []
  (filter #(true? (:mapreduce %)) (get-links)))

(defn final-mapper-task
  "Return the task to the \"left\" side of the mapreduce link"
  []
  (if (empty? (mapreduce-link)) '()
      (:from (first (mapreduce-link)))))

(defn first-reducer-task
  "Return the task to the \"right\" side of the mapreduce link"
  []
  (if (empty? (mapreduce-link)) '()
      (:to (first (mapreduce-link)))))

(defn get-task
  [task-id]
  (filter #(= task-id %) (get-tasks)))

(defn previous-task
  "Return the task linked FROM current task"
  [task-id]
  (:from (first
        (filter #(= task-id (:to %)) (get-links)))))

(defn previous-tasks
  "Return ALL tasks linked from current task"
  ([task-id] (previous-tasks task-id nil))
  ([task-id tasks]
     (if (empty? (previous-task task-id))
       tasks
        (concat
         (list (previous-task task-id))
             (previous-tasks (previous-task task-id) tasks)))))

(defn links-from-task
  "Return to and from links"
  [task-id]
  "TODO"
  nil)

(defn tagged-tasks
  "Return tasks tagged for use with Go.js"
  []
  (let [mapper-tasks (previous-tasks (first-reducer-task))]
    (map #(into {} {:key % :text % :group (cond (empty? mapper-tasks) "standard" (some #{%} mapper-tasks) "mapper" :else "reducer") :fields (task-files %)})
         (get-tasks))))

(defn gojs-node-data-array
  "Complete nodeDataArray for GraphLinksModel in Go.js"
  []
  (into (tagged-tasks)
         '({:key "mapper", :text "mapper logic", :color "blue", :isGroup true} 
         {:key "reducer", :text "reducer logic", :color "red", :isGroup true})))
        
(defn gojs-link-data-array
  "Complete linkDataArray for GraphLinksModel in Go.js"
  []
  (map #(assoc % :color (if (:mapreduce %) "red" "blue")) (get-links)))

;;; functions that read the task
(defn task-files
  "Source and Target files in the task"
  [task-id]
  (let [task (slurp (str (clj-dtl.api/project-dir dtl-job-path) "/" task-id))
        files (map #(into {} {:type (cond (= "IN" (nth % 1)) "source" (= "OUT" (nth % 1)) "target" :else "unknown") :path (nth % 2)}) (re-seq #"\s*/(IN|OUT)FILE\s+([^ \n]+)" task))]
    (into [] files)
  ))
