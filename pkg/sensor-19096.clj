;; sensor-19096.clj
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

(defsensor "phl-basement-temp" {:poll-interval (minutes 1)}
  (read-w1-sensor-at-path "/sys/bus/w1/devices/28-0000068f7d15/w1_slave"))

(defsensor "phl-basement-window-frame" {:poll-interval (minutes 1)}
  (read-w1-sensor-at-path "/sys/bus/w1/devices/28-0000068f415f/w1_slave"))

(defsensor "phl-downstairs-humidity" {:poll-interval (minutes 1)}
   (let [body (http-request-json "http://192.168.1.160/tstat/humidity")]
     (and body
        (ensure-number (get body :humidity false)))))

(defsensor "phl-upstairs-humidity" {:poll-interval (minutes 1)}
   (let [body (http-request-json "http://192.168.1.161/tstat/humidity")]
     (and body
        (ensure-number (get body :humidity false)))))

(defn poll-ct-80 [ url ]
  (let [body (http-request-json url)]
    (and body
         (->
          {:temp-f (constrain-sensor-range (ensure-number (get body :temp false)) 20 120)
           :tmode (ensure-number (get body :tmode false))
           :fmode (ensure-number (get body :fmode false))
           :cool-setpoint-f (or (ensure-number (get body :t_cool false)) -1)
           :heat-setpoint-f (or (ensure-number (get body :t_heat false)) -1)}))))

(defsensor "phl-downstairs" {:poll-interval (minutes 1)}
  (poll-ct-80 "http://192.168.1.160/tstat"))

(defsensor "phl-upstairs" {:poll-interval (minutes 1)}
  (poll-ct-80 "http://192.168.1.161/tstat"))

(defsensor "mschaef-main" {:poll-interval (minutes 30)}
  (measure-http-get "https://www.mschaef.com"))

(defsensor "mschaef-rss" {:poll-interval (minutes 30)}
  (measure-http-get "https://www.mschaef.com/feed/rss"))

(defsensor "bandwidth-test-19096" {:poll-interval (minutes 30)}
  (measure-http-get "https://s3.amazonaws.com/bandwidth-test.mschaef.com/10-mib"))
