(def vault-url "http://metrics.mschaef.com/data")

;;; USGS Data

(defn get-usgs-data []
  (if-let [data (http-request-json "https://waterservices.usgs.gov/nwis/iv/?site=01411320&format=json&period=P1D&indent=on")]
    (get-in data ["value" "timeSeries"])))

(defn get-usgs-value [ value ]
  {:t (time-coerce/to-date (time-format/parse (get value "dateTime")))
   :val (ensure-number (get value "value"))})

(defn get-usgs-var [ var-info ]
  {:unit (get-in var-info [ "variable" "unit" "unitCode" ])
   :variable-id (get-in var-info [ "variable" "variableCode" 0 "variableID"])
   :values (map get-usgs-value (get-in var-info [ "values" 0 "value" ]))})

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

(defsensor mschaef-site-cpu {:poll-interval (minutes 1)}
  (.getSystemCpuLoad (java.lang.management.ManagementFactory/getOperatingSystemMXBean)))

(defsensor mschaef-site-free-disk {:poll-interval (minutes 5)}
  (.getFreeSpace (java.io.File. "/")))

