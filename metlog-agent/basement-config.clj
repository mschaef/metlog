(def vault-url "http://dunitall.com:8080/data")

(defn constrain-sensor-range [ sensor-val low high ]
  (and sensor-val
       (>= sensor-val low)
       (<= sensor-val high)
       sensor-val))

(defn request-json [ url ]
  (let [response (http/get url)
        body (safe-json-read-str (:body response))]
    (and (= 200 (:status response))
         body)))

(defn read-wunderground-temp [ key location ]
  (let [body (request-json (str "http://api.wunderground.com/api/" key "/conditions/q/" location ".json"))]
    (and body
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

(defsensor "upstairs-temp-f" {:poll-interval (minutes 1)}
   (let [body (request-json "http://192.168.1.150/tstat")]
     (and body
        (constrain-sensor-range (ensure-number (get body "temp" false)) 20 120))))

(defsensor "upstairs-humidity" {:poll-interval (minutes 1)}
   (let [body (request-json "http://192.168.1.150/tstat/humidity")]
     (and body
        (ensure-number (get body "humidity" false)))))

(defsensor "basement-temp"
  (read-w1-sensor-at-path "/sys/bus/w1/devices/28-00000690f5b9/w1_slave"))
    
(defsensor "weather-19096" {:poll-interval (minutes 15)}
   (read-wunderground-temp "965408b79c41f708" "19096"))

(defsensor "weather-08226" {:poll-interval (minutes 15)}
   (read-wunderground-temp "965408b79c41f708" "08226"))

