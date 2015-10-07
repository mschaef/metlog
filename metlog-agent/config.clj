(def start-t (System/currentTimeMillis))

(defsensor sine {:poll-interval (seconds 10)}
  (Math/sin (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(defsensor cosine {:poll-interval (seconds 10)}
  (Math/cos (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(defsensor cosine-2 {:poll-interval (seconds 5)}
  (Math/cos (/ (- (System/currentTimeMillis) start-t) 30000.0)))
