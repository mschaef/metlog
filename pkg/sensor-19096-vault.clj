;; sensor-19096-vault.clj
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

(defsensor "phl-downstairs-humidity" {:poll-interval (minutes 1)}
   (let [body (http-request-json "http://192.168.1.160/tstat/humidity")]
     (and body
        (ensure-number (get body :humidity false)))))

(defsensor "phl-upstairs" {:poll-interval (minutes 1)}
  (poll-ct-80 "http://192.168.1.161/tstat"))

(defsensor "phl-upstairs-humidity" {:poll-interval (minutes 1)}
   (let [body (http-request-json "http://192.168.1.161/tstat/humidity")]
     (and body
        (ensure-number (get body :humidity false)))))

(defsensor "mschaef-main-19096-vault" {:poll-interval (minutes 1)}
  (measure-http-get "https://mschaef.com"))

(defsensor "mschaef-rss-19096-vault" {:poll-interval (minutes 1)}
  (measure-http-get "https://mschaef.com/feed/rss"))

(defsensor "bandwidth-test-19096-vault" {:poll-interval (minutes 30)}
  (measure-http-get "https://s3.amazonaws.com/bandwidth-test.mschaef.com/10-mib"))
