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
