(println :begin-config)

(defsensor "sine" (Math/sin (/ (- (System/currentTimeMillis) start-t) 60000.0)))
(defsensor "cosine" (Math/cos (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(println :end-config)