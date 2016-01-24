(def start-t (System/currentTimeMillis))

(defsensor math {:poll-interval (seconds 10)}
  {:sine (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1)))
   :cosine (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))) })

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (mod (/ (- (System/currentTimeMillis) start-t) (minutes 4))
       3))

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

(defsensor weather-19096 {:poll-interval (minutes 15)}
  (read-wunderground-temp "965408b79c41f708" "19096"))


