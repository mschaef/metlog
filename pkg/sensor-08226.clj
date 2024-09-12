;; sensor.clj
;;
;; Custom sensor definitions go here


(defn constrain-sensor-range [ sensor-val low high ]
  (and sensor-val
       (>= sensor-val low)
       (<= sensor-val high)
       sensor-val))

(defn measure-http-get [ url ]
  (let [begin-t (System/currentTimeMillis)
        body (http-request-text url)]
    (and body
         {:bytes (.length body)
          :duration-msec (- (System/currentTimeMillis) begin-t)})))

(defn read-w1-sensor-at-path [ sensor-path ]
  (with-open [rdr (jio/reader sensor-path)]
    (let [ line (second (line-seq rdr)) ]
      (and (not (nil? line))
           (/ (Double/valueOf (.substring line 29)) 1000.0)))))

(defsensor "oc-garage-temp" {:poll-interval (minutes 1)}
  (read-w1-sensor-at-path "/sys/bus/w1/devices/28-0000068a5286/w1_slave"))

(defsensor "mschaef-main-08226" {:poll-interval (minutes 30)}
  (measure-http-get "https://www.mschaef.com"))

(defsensor "mschaef-rss-08226" {:poll-interval (minutes 30)}
  (measure-http-get "https://www.mschaef.com/feed/rss"))

(defsensor "bandwidth-test-08226" {:poll-interval (minutes 30)}
  (measure-http-get "https://s3.amazonaws.com/bandwidth-test.mschaef.com/10-mib"))