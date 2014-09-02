(ns clj-dtl.api
  (:require [cheshire.core :refer :all]
            [clojure.string :as s]
            [instaparse.core :as insta]
            ))
;; TODO:
;;   ensure first source is DFS
;;      - /INFILE source has SERVERCONNECTION
;;      - /SERVERCONNECTION is HDFS type


;; {:dtl {:tasktype :sort :infile {:value "${APACHE_WEBLOGS_DIR}/SAMPLE-access_1.log" :serverconnection "HDFSConnection" :filetype :stlf :fieldseparator " " :enclosedby ["\"\"" "[]"] :layout :apache-weblogs-layout} :serverconnection {:value "$HDFS_SERVER" :alias "HDFSConnection"}}}

(def dtl-job-path "/work/syncsort-dtl/dtl-projects/sessionize-weblogs/sessionize-weblogs-job.dtl")

(defn set-dtl-file-path! [path]
  (def dtl-job-path path))

(defn project-dir 
  "Project directory (directory of the job file)"
  [path]
  (.getParent (java.io.File. path)))

(defn- get-dtl-file
  "Read DTL job or task file, removing comments"
  [path]
  (s/replace  ;; filter out comments (DTL allows only single-line comments)
   (slurp path)
   #"\s*/\*.*?\*/" 
   ""))

(defn- read-task
  "Read task, return as string with comments and multi-line formatting stripped out."
  [task-id]
  (s/replace 
   (s/replace 
    (get-dtl-file (str (clj-dtl.api/project-dir dtl-job-path) "/" task-id))
    #"\n+\s*" "\n")
   #"\n+\s*([^/])" " $1"))


(defn get-links []
  (map #(into {} {:from (nth % 2) :to (nth % 3) :mapreduce (string? (nth % 1))}) 
       (re-seq #"(/MAPREDUCE)?\s*/?FLOW\s+([^ \n]+)\s+([^ \n]+)" 
               (get-dtl-file  dtl-job-path))))

(defn get-tasks 
  "Return all tasks (nodes) in current job."
  []
  (map #(nth % 1) (re-seq #"\s*/TASK\s+FILE\s+([^ \n]+)" (get-dtl-file dtl-job-path))))

(defn mapreduce-link
  "Return links which are mapreduce sequences"
  []
  (filter #(true? (:mapreduce %)) (get-links)))

;; (defn task-type
;;   (

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

(defn previous-tasks
  "Return the tasks linked FROM current task"
  [task-id]
  (map #(:from %)
       (filter #(= task-id (:to %)) (get-links))))

(defn all-previous-tasks
  "Return ALL tasks linked from current task"
  ([task-id] (all-previous-tasks task-id nil))
  ([task-id tasks]
     (if (empty? (previous-tasks task-id))
       tasks
       (filter (fn [x] (not (empty? x)))
               (flatten
                (map (fn [x] 
                       (list x
                             (all-previous-tasks x) tasks)
                       ) ; anon fun
                     (previous-tasks task-id)
                     ) ;; map
                )))))

(defn links-from-task
  "Return to and from links"
  [task-id]
  "TODO"
  nil)

;;; functions that read the task
(defn task-files
  "Source and Target files in the task"
  [task-id]
  (let [task (get-dtl-file (str (clj-dtl.api/project-dir dtl-job-path) "/" task-id))
        files (map #(into {} 
                          {
                           :type   (cond (= "IN" (nth % 1)) "source" (= "OUT" (nth % 1)) "target" :else "unknown") 
                           :path (nth % 2) 
                           :name (nth % 2)
                           :info (last (s/split (nth % 2) #"/"))
                           :figure (cond (= "IN" (nth % 1)) "Ellipse" (= "OUT" (nth % 1)) "Diamond" :else "Rectangle")
                           :color "#F7B84B"
                           }) 
                   (re-seq #"\s*/(IN|OUT)FILE\s+([^ \n]+)" task))]
    (into [] files)
    ))

(defn tagged-tasks
  "Return tasks tagged for use with Go.js"
  []
  (let [mapper-tasks (all-previous-tasks (first-reducer-task))]
    (map #(into {} 
                {
                 :key % :text % 
                 :group (cond (empty? mapper-tasks) "standard" (some #{%} mapper-tasks) "mapper" :else "reducer") 
                 :fields (task-files %)
                 
                 })
         (get-tasks))))

(defn tagged-links
  "Add in source/target linkage to links sequence"
  []
  (flatten
   (let [my-tasks (tagged-tasks)]
     (map #(let [my-link %
                 my-from-task (first (filter (fn [x] (= (:key x) (:from my-link))) my-tasks))
                 my-to-task   (first (filter (fn [x] (= (:key x) (:to my-link)))   my-tasks))
                 my-targets (map :path (filter (fn [x] (= (:type x) "target")) (:fields my-from-task)))
                 my-sources (map :path (filter (fn [x] (= (:type x) "source")) (:fields my-to-task)))
                 matched-ports (clojure.set/intersection (set my-targets) (set my-sources))
                 ]
             (if-not (empty? matched-ports)
               (map (fn [x] (conj my-link {:fromPort x :toPort x})) (seq matched-ports))
               my-link))
          (get-links)
          ))

   ))

(defn- source-paths
  "All paths defined as sources in DTL tasks"
  []
  (map 
   (fn [x] (:path x)) 
   (filter #(= "source" (:type %)) 
           (flatten (map #(:fields %) (tagged-tasks)))))) ;; could use (set tagged-tasks) to ensure uniqueness

(defn- target-paths
  "All paths defined as targets in DTL tasks"
  []
  (map 
   (fn [x] (:path x)) 
   (filter #(= "target" (:type %)) 
           (flatten (map #(:fields %) (tagged-tasks)))))) ;; could use (set tagged-tasks) to ensure uniqueness

(defn unmatched-to-links
  []
  (let [links (tagged-links)
        to-links (map #(:to %) links)
        from-links (map #(:from %) links)]
    (seq
     (clojure.set/difference (set to-links) (set from-links)))))

(defn unmatched-from-links
  []
  (let [links (tagged-links)
        to-links (map #(:to %) links)
        from-links (map #(:from %) links)]
    (seq
     (clojure.set/difference (set from-links) (set to-links)))))

(defn- unmatched-source-paths
  "Paths defined as sources with no matching targets"
  []
  (seq 
   (clojure.set/difference (set (source-paths)) (set (target-paths)))))

(defn- unmatched-target-paths
  "Paths defined as targets with no matching sources"
  []
  (seq 
   (clojure.set/difference (set (target-paths)) (set (source-paths)))))

(def gojs-groups-data '(
                        {:group "mapper" :color "blue" :text "mapper logic"} 
                        {:group "reducer" :color "red" :text "reducer logic"} 
                        {:group "standard" :color "green" :text "non-mapreduce"}))

(defn gojs-node-data-array
  "Complete nodeDataArray for GraphLinksModel in Go.js"
  []
  (let [tt (tagged-tasks)
        groups (map #(:group %) tt)
        gojs-groups (map (fn [x] (conj {} (when (some (fn [y] (= (:group x) y)) groups) {:key (:group x) :color (:color x) :isGroup true :text (:text x)})))
                         gojs-groups-data)
        ]
    (filter #(not-empty %) (into tt gojs-groups))))
(defn gojs-link-data-array
  "Complete linkDataArray for GraphLinksModel in Go.js"
  []
  (map #(assoc % :color (if (:mapreduce %) "red" "blue"))  (tagged-links)))

;; work on determining if first source is HDFS
(defn unformatted-task
  "Seq of commands with arguments from task."
  [task-id]
  (map (fn [x] (s/replace x "\n" "")) 
       (re-seq #"\n/[^/\n]+" 
               (s/replace 
                (read-task task-id) #"\n\s*" "\n"))))

(defn- validate-initial-mapper-count
  "Mapreduce job must have single initial mapper task."
  [errors]
  (let [initial-tasks (unmatched-from-links)]
    (if-not (= 1 (count initial-tasks))
      (into errors {:invalid-initial-mapper-count (str "There can be only one initial mapper task; Found " (count initial-tasks) ": " (s/join ", " initial-tasks))}))))

(defn- validate-dfs-source
  [errors]
  (into errors {:HNODITSR "Initial mapper source must be HDFS type."}))

(defn validate-mapreduce-job
  []
  (-> {}
      (validate-initial-mapper-count)
      (validate-dfs-source)
      )
  )

(defn task-source-params
  [task-id]
  (filter 
   #(let [cmd (first (s/split % #"\s"))] 
      (or 
       (= cmd "/INFILE") 
       (= cmd "/INPIPE") 
       (= cmd "/SERVERCONNECTION"))) 
   (unformatted-task task-id)))

(defn dtl-command-to-keyword
  [cmd]
  (keyword (s/lower-case (nth (s/split (s/replace cmd #"^/" "") #"\s") 0))))


(def g "/DTL
/TASKTYPE SORT    NODUPLICATE 
    /INFILE ${HDFS_SOURCE_DIR}/LineItemsPrevious.txt SERVERCONNECTION HDFSConnection STLF FIELDSEPARATOR \",\" ENCLOSEDBY \"\"\"\" LAYOUT lineitem_delim
    /INFILE ${HDFS_SOURCE_DIR}/LineItemsCurrent.txt  SERVERCONNECTION HDFSConnection STLF FIELDSEPARATOR \",\" ENCLOSEDBY \"\"\"\" LAYOUT lineitem_delim
    /KEYS APartitionNum ASCENDING, someOther DESCENDING
/END")

(def dtl-parser
  (insta/parser (slurp "src/clj_dtl/dtl.ebnf") :output-format :hiccup :auto-whitespace :standard))

;; (dtl-parser g :total true)

