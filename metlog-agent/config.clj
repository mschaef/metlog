(def start-t (System/currentTimeMillis))

(defn random-sampler [ ]
  (let [current (atom 0.0)]
    (fn [ ]
      (swap! current #(+ % (/ (+ (Math/random) (Math/random)) 2) -0.5)))))

(defsensor* random-unconstrained {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (rs))))

(defsensor* random-positive {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (+ 0.1 (Math/abs (rs))))))

(defsensor* random-negative {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (- -0.1 (Math/abs (rs))))))

(defsensor* random-very-positive {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (+ 10 (Math/abs (rs))))))

(defsensor* random-very-negative {:poll-interval (seconds 10)}
  (let [rs (random-sampler)]
    (fn [] (- -10 (Math/abs (rs))))))

(defsensor math {:poll-interval (seconds 10)}
  {:sine (+ 0.3 (Math/sin (/ (- (System/currentTimeMillis) start-t) (minutes 1))))
   :cosine (Math/cos (/ (- (System/currentTimeMillis) start-t) (minutes 1))) })

(defsensor steps-ascending {:poll-interval (seconds 5)}
  (+ 2.3
     (mod (/ (- (System/currentTimeMillis) start-t) (minutes 4))
          3)))

