(ns metlog-client.tsplot
  (:require-macros [metlog-client.macros :refer [ with-preserved-ctx unless ]])
  (:require [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]
            [metlog-client.logger :as log]))


(defn floor [ x ]
  (.floor js/Math x))

(defn pixel-snap [ t ]
  (+ 0.5 (floor t)))


(def dtf-axis-label (time-format/formatter "MM-dd HH:mm"))
(def dtf-header (time-format/formatter "yyyy-MM-dd HH:mm"))
(def x-axis-space 20)
(def tsplot-right-margin 5)
(def y-axis-space 40)

(def pixels-per-x-label 100)
(def pixels-per-y-label 20)

(def y-line-intervals
  (mapcat (fn [ scale ]
            (map #(* % (.pow js/Math 10 scale))
                 [ 1 2 5 ]))
          (range -3 3)))

(defn seconds [ seconds ] (* 1000 seconds))
(defn minutes [ minutes ] (seconds (* 60 minutes)))
(defn hours [ hours ] (minutes (* 60 hours)))
(defn days [ days ] (hours (* 24 days)))

(def x-line-intervals
  [(seconds 1)
   (seconds 2)
   (seconds 5)
   (seconds 10)
   (seconds 15)
   (seconds 30)
   (minutes 1)
   (minutes 2)
   (minutes 5)
   (minutes 10)
   (minutes 15)
   (minutes 30)
   (hours 1)
   (hours 2)
   (hours 3)
   (hours 6)
   (hours 12)
   (days 1)
   (days 2)
   (days 3)
   (days 7)
   (days 14)
   (days 30)])

(def stroke-styles
  {:grid
   {:line-width 0
    :stroke-style "#707070"
    :line-dash #js [ 1 1 ]}

   :grid-emphasis
   {:line-width 0
    :stroke-style "#000000"
    :line-dash #js [ ]}

   :series-line
   {:line-width 0
    :stroke-style "#0000FF"
    :line-dash #js [ ]}
   
   :frame
   {:line-width 1
    :stroke-style "#000000"
    :line-dash #js [ ]}})

(defn range-contains-zero? [ range ]
  (and (< (:min range) 0.0)
       (> (:max range) 0.0)))

(defn range-magnitude [ range ]
  (- (:max range) (:min range)))

(defn clip-rect [ ctx x y w h ]
  (.beginPath ctx)
  (.rect ctx x y w h)
  (.clip ctx))

(defn first-that [ pred? xs ]
  (first (filter pred? xs)))

(defn draw-line [ ctx [ x-1 y-1 ] [ x-2 y-2 ] ]
  (.beginPath ctx)
  (.moveTo ctx x-1 y-1)
  (.lineTo ctx x-2 y-2)
  (.stroke ctx))

(defn s-yrange [ samples ]
  (loop [samples (next samples)
         min (:val (first samples))
         max min]
    (if (empty? samples)
      {:max max :min min}
      (let [val (:val (first samples))]
        (recur (next samples)
               (if (< val min) val min)
               (if (> val max) val max))))))

(def epsilon 0.001)

(defn rescale-y-range [ range factor ]
  (let [magnitude (.max js/Math (range-magnitude range) factor)
        scaled-delta (* (/ (- factor 1) 2) magnitude)]
    {:max (+ (:max range) scaled-delta)
     :min (- (:min range) scaled-delta)}))

(defn draw-xlabel [ ctx text x y ]
  (with-preserved-ctx ctx
    (aset ctx "textBaseline" "top")
    (aset ctx "textAlign" "center")
    (.fillText ctx text x y)))

(defn draw-ylabel [ ctx text x y ]
  (with-preserved-ctx ctx
    (aset ctx "textBaseline" "middle")
    (aset ctx "textAlign" "right")
    (.fillText ctx text x y)))

(defn translate-fn [ range size ]
  (let [min (:min range)
        max (:max range)
        delta (- max min)]
    #(* size (/ (- % min) delta))))

(defn translate-fn-invert [ range size ]
  (let [min (:min range)
        max (:max range)
        delta (- max min)]
    #(- size (* size (/ (- % min) delta)))))

(defn draw-series-line [ ctx data x-range y-range w h ]
  (let [tx (translate-fn x-range w)
        ty (translate-fn-invert y-range h)]
    (.beginPath ctx)
    (let [ pt (first data) ]
      (.moveTo ctx (tx (:t pt)) (ty (:val pt))))
    (doseq [ pt (rest data) ]
      (.lineTo ctx (tx (:t pt)) (ty (:val pt))))
    (.stroke ctx)))

(defn long-to-local-date-time [ val ]
  (time/to-default-time-zone (time-coerce/from-long val)))

(defn format-xlabel [ val ]
  (time-format/unparse dtf-axis-label (long-to-local-date-time val)))

(defn format-ylabel [ val ]
  (.toFixed val 2))

(defn largest-y-range-magnitude [ y-range ]
  (if (range-contains-zero? y-range)
    (if (> (:max y-range) (.abs js/Math (:min y-range)))
      {:t (/ (:max y-range) (range-magnitude y-range)) :magnitude (:max y-range)}
      {:t (.abs js/Math (/ (:min y-range) (range-magnitude y-range))) :magnitude (- (:min y-range))})
    {:t 1.0 :magnitude (range-magnitude y-range) }))

(defn find-y-grid-interval [ h y-range ]
  (let [{ t :t magnitude :magnitude } (largest-y-range-magnitude y-range)
        avail-pixels (* t h)]
    (first
     (first-that #(< (second %) avail-pixels)
                 (map #(vector % (* pixels-per-y-label (/ magnitude %)))
                      y-line-intervals)))))

(defn set-stroke-style [ ctx style-name ]
  (if-let [ style (get stroke-styles style-name false) ]
    (do
      (aset ctx "lineWidth" (:line-width style))      
      (aset ctx "strokeStyle" (:stroke-style style))
      (.setLineDash ctx (:line-dash style)))
    (.error js/console "Unknown stroke style:" (pr-str style-name))))

(defn draw-y-grid-line [ ctx w y value emphasize? ]
  (with-preserved-ctx ctx
    (draw-ylabel ctx (format-ylabel value) -2 y)
    (set-stroke-style ctx (if emphasize? :grid-emphasis :grid))
    (draw-line ctx [ 0 y ] [ w y ])))

(defn adjust-range-to-interval [ range interval ]
  {:min (* interval (.floor js/Math (/ (+ (:min range) interval) interval)))
   :max (:max range)})

(defn draw-y-grid [ ctx w h y-range ]
  (with-preserved-ctx ctx
    (let [ty (translate-fn-invert y-range h)
          y-interval (find-y-grid-interval h y-range)]
      (doseq [ y (if (range-contains-zero? y-range)
                   (concat (map #(* y-interval %)
                                (range (/ (:max y-range) y-interval)))
                           (map #(* (- y-interval) (+ % 1))
                                (range (- (/ (- (:min y-range)) y-interval) 1))))
                   (let [ y-range (adjust-range-to-interval y-range y-interval) ]
                     (map #(+ (:min y-range) (* y-interval %))
                          (range (/ (range-magnitude y-range) y-interval)))))]
        (draw-y-grid-line ctx w (pixel-snap (ty y)) y (= y 0))))))

(defn draw-x-grid-line [ ctx h x value ]
  (with-preserved-ctx ctx
    (draw-xlabel ctx (format-xlabel value) x h)
    (set-stroke-style ctx :grid)
    (draw-line ctx [ x 0 ] [ x h ])))

(defn find-x-grid-interval [ w x-range ]
  (let [magnitude (range-magnitude x-range)
        max-label-count (floor (/ w pixels-per-x-label))]
    (first
     (first-that #(< (second %) max-label-count)
                 (map #(vector % (/ magnitude %))
                      x-line-intervals)))))

(defn draw-x-grid [ ctx w h x-range ]
  (let [x-interval (find-x-grid-interval w x-range)
        tx (translate-fn x-range w)
        max-x (* (floor (/  (:max x-range) x-interval)) x-interval)]
    (doseq [ x (map #(- max-x (* % x-interval))
                    (range 0 (/ (range-magnitude x-range) x-interval)))]
      (draw-x-grid-line ctx h (tx x) x))))

(defn restrict-data [ data x-range ]
  (let [{min :min max :max} x-range
        min (- min (* 0.1 (- max min)))]
    (filter #(and (>= (:t %) min)
                  (<= (:t %) max))
            data)))

(defn draw-series [ ctx w h data x-range ]
  (with-preserved-ctx ctx
    (set-stroke-style ctx :series-line)
    (aset ctx "font" "12px Arial")
    (let [ data (restrict-data data x-range) ]
      (let [y-range (rescale-y-range (s-yrange data) 1.1)]
        (unless (empty? data)
                (draw-y-grid ctx w h y-range)
                (draw-x-grid ctx w h x-range)
                (with-preserved-ctx ctx
                  (clip-rect ctx 0 0 w h)
                  (draw-series-line ctx data x-range y-range w h))))))) 

(defn draw-series-background [ ctx w h ]
  (with-preserved-ctx ctx
    (aset ctx "fillStyle" "#FFFFFF")
    (.fillRect ctx 0 0 w h)))

(defn draw-frame [ ctx w h ]
  (with-preserved-ctx ctx
    (set-stroke-style ctx :frame)
    (draw-line ctx [ 0.5 0.5 ] [ 0.5 (- h 0.5) ] )))

(defn draw [ ctx w h data begin-t end-t ]
  (draw-series-background ctx w h)
  (let [w (- w y-axis-space tsplot-right-margin)
        h (- h x-axis-space)]
    (with-preserved-ctx ctx
      (.translate ctx y-axis-space 0)
      (draw-series-background ctx w h)
      (draw-series ctx w h data {:min begin-t :max end-t})
      (draw-frame ctx w h))))

