(ns metlog-common.core
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]))

(defmacro unless [ condition & body ]
  `(when (not ~condition)
     ~@body))

(defn string-empty? [ str ]
  (or (nil? str)
      (= 0 (count (.trim str)))))

(defn in? 
  "true if seq contains elm"
  [seq elm]  
  (some #(= elm %) seq))

(defn assoc-if [ map assoc? k v ]
  (if assoc?
    (assoc map k v)
    map))

(defn string-leftmost
  ( [ string count ellipsis ]
      (let [length (.length string)
            leftmost (min count length)]
        (if (< leftmost length)
          (str (.substring string 0 leftmost) ellipsis)
          string)))
  ( [ string count ]
      (string-leftmost string count "")))

(defn try-parse-integer
  ([ str default-value ]
   (try
     (Integer/parseInt str)
     (catch Exception ex
       default-value)))
  ([ str ]
    (try-parse-integer str false)))

(defn try-parse-long
  ([ str default-value ]
   (try
     (Long/parseLong str)
     (catch Exception ex
       default-value)))
  ([ str ]
    (try-parse-long str false)))

(defn try-parse-double
  ([ str default-value ]
   (try
     (Double/parseDouble str)
     (catch Exception ex
       default-value)))
  ([ str ]
   (try-parse-double str false)))


(defn safe-json-read-str [ json-string ]
  (try
    (json/read-str json-string)
    (catch Exception ex
      (log/warn "Bad JSON:" (.getMessage ex) json-string)
      false)))

(defn try-parse-percentage [ str ]
  (and (string? str)
       (let [ str (if (= \% (.charAt str (- (.length str) 1)))
                    (.substring str 0 (- (.length str) 1))
                    str)]
         (try-parse-double str))))

(defn ensure-number [ val ]
  (if (number? val)
    val
    (try-parse-double val)))


(defn config-property 
  ( [ name ] (config-property name nil))
  ( [ name default ]
      (let [prop-binding (System/getProperty name)]
        (if (nil? prop-binding)
          default
          (if-let [ int (try-parse-integer prop-binding) ]
            int
            prop-binding)))))

(defn add-shutdown-hook [ shutdown-fn ]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (shutdown-fn)))))

(defn exception-barrier
  ([ fn label ]
   #(try
      (fn)
      (catch Exception ex
        (log/error ex (str "Uncaught exception: " label))))))

(defn query-all [ db-connection query-spec ]
  (log/debug "query-all:" query-spec)
  (jdbc/query db-connection query-spec))

(defn query-first [ db-connection query-spec ]
  (log/debug "query-first:" query-spec)
  (first (jdbc/query db-connection query-spec)))

(defn query-scalar [ db-connection query-spec ]
  (log/debug "query-scalar:" query-spec)
  (let [first-row (first (jdbc/query db-connection query-spec))
        row-keys (keys first-row)]
    (when (> (count row-keys) 1)
      (log/warn "Queries used for query-scalar should only return one field per row:" query-spec))
    (get first-row (first row-keys))))
