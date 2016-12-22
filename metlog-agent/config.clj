(def start-t (System/currentTimeMillis))

(defsensor math {:poll-interval (seconds 10)}
  {:sine (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1)))
   :cosine (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))) })

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (+ 2.3
     (mod (/ (- (System/currentTimeMillis) start-t) (minutes 4))
          3)))

