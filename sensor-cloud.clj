(def vault-url "http://metrics.mschaef.com/data")

(defsensor mschaef-site-cpu {:poll-interval (minutes 1)}
 (.getSystemCpuLoad (java.lang.management.ManagementFactory/getOperatingSystemMXBean)))

(defsensor mschaef-site-free-disk {:poll-interval (minutes 5)}
 (.getFreeSpace (java.io.File. "/")))

(defn delta-sensor [ sensor-fn combine-fn ]
 (let [ prev (atom nil) ]
   (fn []
     (let [sensor-current {:t (java.util.Date.)
                           :val (sensor-fn)}]
       (when (:val sensor-current)
         (let [ sensor-prev @prev ]
           (reset! prev sensor-current)
           (if sensor-prev
             (combine-fn sensor-prev sensor-current))))))))

(defn read-nginx-stats []
 (let [resp (http-request-text "http://localhost:8800/nginx_status")
       [ ac-line _ req-count-line ] (clojure.string/split-lines resp)
       ac (ensure-number (second (clojure.string/split ac-line #":")))
       [ accepts handled requests ] (map ensure-number (clojure.string/split (.trim req-count-line) #" "))]
   (and ac accepts handled requests
        {:connections ac
         :reqs-accepted accepts
         :reqs-handled handled
         :reqs requests})))

(defsensor* mschaef-site-nginx {:poll-interval (seconds 10)}
 (delta-sensor read-nginx-stats
               (fn [{prev-t :t prev-val :val}
                    {curr-t :t curr-val :val}]
                 (let [delta-t-sec (/ (- (.getTime curr-t)
                                         (.getTime prev-t))
                                      1000.0)]
                   {:connections (:connections curr-val)
                    :accepted-per-sec (max 0 (/ (- (:reqs-accepted curr-val) (:reqs-accepted prev-val)) delta-t-sec))
                    :handled-per-sec (max 0 (/ (- (:reqs-handled curr-val) (:reqs-handled prev-val)) delta-t-sec))
                    :reqs-per-sec (max 0 (/ (- (:reqs curr-val) (:reqs prev-val)) delta-t-sec))}))))


;;; USGS Data

(defn get-usgs-data []
 (if-let [data (http-request-json "https://waterservices.usgs.gov/nwis/iv/?site=01411320&format=json&period=P7D&indent=on")]
   (get-in data [:value :timeSeries])))

(defn get-usgs-value [ value ]
 {:t (time-coerce/to-date (time-format/parse (get value :dateTime)))
  :val (ensure-number (get value :value))})

(defn get-usgs-var [ var-info ]
 {:unit (get-in var-info [ :variable :unit :unitCode ])
  :variable-id (get-in var-info [ :variable :variableCode 0 :variableID ])
  :values (map get-usgs-value (get-in var-info [ :values 0 :value ]))})

(defn flatten-usgs-var [ var-data ]
 (map (fn [ sensor-reading ]
        (assoc sensor-reading :variable-id (:variable-id var-data)))
      (:values var-data)))

(defn get-flat-usgs-data []
 (mapcat
  flatten-usgs-var
  (map get-usgs-var (get-usgs-data))))

(def variable-names
 {45807042 :water-temp
  52333388 :water-level})

(defn to-timestamped [ var ]
 (timestamped-value (:t var) { (variable-names (:variable-id var)) (:val var)}))

(defn get-usgs-sensor-data [ ]
 (map to-timestamped (get-flat-usgs-data)))

(defsensor usgs-oc-bay  {:poll-interval (minutes 60)}
 (vec (get-usgs-sensor-data)))
