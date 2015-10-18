(def start-t (System/currentTimeMillis))

(defsensor sine {:poll-interval (seconds 10)}
  (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1))))

(defsensor cosine {:poll-interval (seconds 10)}
  (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))))

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (mod (/ (- (System/currentTimeMillis) start-t) (minutes 2))
       3))
