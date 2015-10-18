(def start-t (System/currentTimeMillis))

(defsensor sine {:poll-interval (seconds 10)}
  (Math/sin (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(defsensor cosine {:poll-interval (seconds 10)}
  (Math/cos (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (mod (/ (- (System/currentTimeMillis) start-t) 120000.0)
       3))
