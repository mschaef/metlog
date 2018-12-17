(def vault-url "http://metrics.mschaef.com/data")

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

(defsensor "weather-19096" {:poll-interval (minutes 15)}
   (read-wunderground-temp "965408b79c41f708" "19096"))

(defsensor "weather-08226" {:poll-interval (minutes 15)}
   (read-wunderground-temp "965408b79c41f708" "08226"))

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
