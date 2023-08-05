(def start-t (System/currentTimeMillis))


(defn random-sampler [ ]
  (let [current (atom 0.0)]
    (fn [ ]
      (swap! current #(+ % (/ (+ (Math/random) (Math/random)) 2) -0.5)))))

(defsensor* random-unconstrained {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (rs))))

(defsensor* random-positive {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (+ 0.1 (Math/abs (rs))))))

(defsensor* random-negative {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (- -0.1 (Math/abs (rs))))))

(defsensor* random-very-positive {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (+ 10 (Math/abs (rs))))))

(defsensor* random-very-negative {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (- -10 (Math/abs (rs))))))

(defsensor constant-value {:poll-interval (seconds 10)}
  1.5)

(defsensor math {:poll-interval (seconds 10)}
  {:sine (+ 0.3 (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1))))
   :cosine (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))) })

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (+ 2.3
     (mod (/ (- (System/currentTimeMillis) start-t) (minutes 4))
          3)))

(defsensor* intermittent {:poll-interval (seconds 5)}
  (let [rs (random-sampler)]
    (fn []
      (if (> 0.5 (Math/random))
        (rs)))))

(defsensor* intermittent-nested {:poll-interval (seconds 5)}
  (let [rs (random-sampler)]
    (fn []
      (let [v (rs)]
        {:c v
         :i (if (> 0.5 (Math/random))
              v)}))))

(defsensor* multiple {:poll-interval (seconds 10)}
  (let [rs-1 (random-sampler)
        rs-2 (random-sampler)
        rs-3 (random-sampler)]
    (fn []
      [{:1 (rs-1)}
       {:2 (rs-2)}
       {:3 (rs-3)}])))

(defsensor* timestamped-fn {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (timestamped-value (java.util.Date.) (rs)))))

(defsensor timestamped {:poll-interval (seconds 10)}
  (timestamped-value (java.util.Date.) (Math/random)))

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

(defsensor failing-sensor {:poll-interval (seconds 15)}
  (if (> 0.5 (Math/random))
    (throw (Exception. "test failure"))
    (Math/random)))

(defsensor fx-btc-usd {:poll-interval (minutes 15)}
  (get-in (http-request-json "https://api.coindesk.com/v1/bpi/currentprice/usd.json")
          [:bpi :USD :rate_float]))
