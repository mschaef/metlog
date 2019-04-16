(def vault-url "http://metrics.mschaef.com/data")

(defn request-json [ url ]
  (let [response (http/get url)
        body (safe-json-read-str (:body response))]
    (and (= 200 (:status response))
         body)))

(def darksky-api-key "587a8fa1a383bafeeef406acf12fb98a")

(def darksky-19096 "39.9996,-75.2730")
(def darksky-08226 "39.2776,-74.5746")

(defn request-json [ url ]
  (let [response (http/get url)
        body (safe-json-read-str (:body response))]
    (and (= 200 (:status response))
         body)))

(defn read-darksky [ key location ]
  (let [body (request-json (str "https://api.darksky.net/forecast/" key "/" location "?exclude=minutely,hourly,daily,alerts,flags"))]
    (and body
         (let [obv (get body "currently")]
           {:temp-f (ensure-number (get obv "temperature" false))            
            :apparent-temp-f (ensure-number (get obv "apparentTemperature" false))
            :dew-point-f (ensure-number (get obv "dewPoint" false))
            :humidity (ensure-number (get obv "humidity" false))
            :ozone (ensure-number (get obv "ozone" false))
            :precip-intensity (ensure-number (get obv "precipIntensity" false))
            :precip-probability (ensure-number (get obv "precipProbability" false))
            :pressure (ensure-number (get obv "pressure" false))
            :uv-index (ensure-number (get obv "uvIndex" false))
            :wind-bearing (ensure-number (get obv "windBearing" false))
            :wind-gust (ensure-number (get obv "windGust" false))
            :wind-speed (ensure-number (get obv "windSpeed" false))}))))

(defsensor "darksky-19096" {:poll-interval (minutes 15)}
  (read-darksky darksky-api-key darksky-19096))

(defsensor "darksky-08226" {:poll-interval (minutes 15)}
  (read-darksky darksky-api-key darksky-08226))

;;; USGS Data

(defn get-usgs-data []
  (if-let [data (request-json "https://waterservices.usgs.gov/nwis/iv/?site=01411320&format=json&period=P1D&indent=on")]
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
   45807202 :water-level})

(defn to-timestamped [ var ]
  (TimestampedValue. (:t var) { (variable-names (:variable-id var)) (:val var)}))

(defn get-usgs-sensor-data [ ]
  (map to-timestamped (get-flat-usgs-data)))

(defsensor usgs-oc-bay  {:poll-interval (minutes 60)}
  (vec (get-usgs-sensor-data)))



