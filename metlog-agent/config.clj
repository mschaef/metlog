(def start-t (System/currentTimeMillis))

(defsensor math {:poll-interval (seconds 10)}
  {:sine (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1)))
   :cosine (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))) })

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (mod (/ (- (System/currentTimeMillis) start-t) (minutes 4))
       3))

(defn read-wunderground-temp [ key location ]
  (let [response (http/get (str "http://api.wunderground.com/api/" key "/conditions/q/" location ".json"))
        body (safe-json-read-str (:body response))]
    (and (= 200 (:status response))
         body
         (let [obv (get body "current_observation")]
           {:temp-c (ensure-number (get obv "temp_c" false))
            :pressure-in (ensure-number (get obv "pressure_in" false))
            :wind-degrees (ensure-number (get obv "wind_degrees" false))
            :wind-mph  (ensure-number (get obv "wind_mph" false))
            :wind-gust-mph (ensure-number (get obv "wind_gust_mph" false))
            :relative-humidity (try-parse-percentage (get obv "relative_humidity" false))          
            :precip-1hr-in (ensure-number (get obv "precip_1hr_in" false))
            :precip-today-in (ensure-number (get obv "precip_today_in" false))}))))

(defn read-w1-sensor-at-path [ sensor-path ]
  (with-open [rdr (jio/reader sensor-path)]
    (let [ line (second (line-seq rdr)) ]
      (and (not (nil? line))
           (/ (Double/valueOf (.substring line 29)) 1000.0)))))

(if-let [ sensor-path (config-property "sensor.path") ]
  (do
    (defsensor "basement-temp"
      (read-w1-sensor-at-path sensor-path))
    
    (defsensor "weather-19096" {:poll-interval (minutes 15)}
      (read-wunderground-temp "965408b79c41f708" "19096"))

    (defsensor "weather-08226" {:poll-interval (minutes 15)}
      (read-wunderground-temp "965408b79c41f708" "08226"))))


