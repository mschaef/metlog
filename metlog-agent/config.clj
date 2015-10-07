(def start-t (System/currentTimeMillis))

(defsensor sine {:poll-interval (seconds 60)}
  (Math/sin (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(defsensor cosine {:poll-interval (seconds 60)}
  (Math/cos (/ (- (System/currentTimeMillis) start-t) 60000.0)))
