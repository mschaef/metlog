(def start-t (System/currentTimeMillis))

(defn random-sampler [ ]
  (let [current (atom 0.0)]
    (fn [ ]
      (swap! current #(+ % (/ (+ (Math/random) (Math/random)) 2) -0.5)))))

(def rs-0 (random-sampler))
(def rs-1 (random-sampler))
(def rs-2 (random-sampler))

(defsensor random-unconstrained {:poll-interval (seconds 10)}
  (rs-0))

(defsensor random-positive {:poll-interval (seconds 10)}
  (+ 0.1 (Math/abs (rs-1))))

(defsensor random-negative {:poll-interval (seconds 10)}
  (- -0.1 (Math/abs (rs-2))))

(defsensor math {:poll-interval (seconds 10)}
  {:sine (+ 0.3 (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1))))
   :cosine (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))) })

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (+ 2.3
     (mod (/ (- (System/currentTimeMillis) start-t) (minutes 4))
          3)))

