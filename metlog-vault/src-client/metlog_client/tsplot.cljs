(ns metlog-client.tsplot
  (:require-macros [metlog-client.macros :refer [ with-preserved-ctx unless ]])
  (:require [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]))

(def dtf-axis-label (time-format/formatter "MM-dd HH:mm"))
(def dtf-header (time-format/formatter "yyyy-MM-dd HH:mm"))
(def x-axis-space 20)
(def tsplot-right-margin 5)
(def y-axis-space 40)

(defn s-yrange [ samples ]
  (let [ vals (map :val samples) ]
    {:max (apply Math/max vals)
     :min (apply Math/min vals)}))

(defn s-xrange [ samples ]
  (let [ ts (map :t samples) ]
    {:max (apply Math/max ts)
     :min (apply Math/min ts)}))

(defn rescale-range [ range factor ]
  (let [ scaled-delta (* (/ (- factor 1) 2) (- (:max range) (:min range) )) ]
    {:max (+ (:max range) scaled-delta)
     :min (- (:min range) scaled-delta)}))

(defn range-scale [ range val ]
  (/ (- val (:min range))
     (- (:max range) (:min range))))

(defn draw-xlabel [ ctx text x y left? ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (if left? x (- x w)) (+ 12 y))))

(defn draw-ylabel [ ctx text x y ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (- x w) (+ y 8))))

(defn translate-point [ pt x-range y-range w h ]
  [(* w (range-scale x-range (:t pt)))
   (- h (* h (range-scale y-range (:val pt))))])

(defn clip-rect [ ctx x y w h ]
  (.beginPath ctx)
  (.rect ctx x y w h)
  (.clip ctx))

(defn draw-series-line [ ctx data x-range y-range w h ]
  (.beginPath ctx)
  (let [ data-scaled (map #(translate-point % x-range y-range w h) data) ]
    (let [ [ pt-x pt-y ] (first data-scaled) ]
      (.moveTo ctx pt-x pt-y))
    (doseq [ [ pt-x pt-y ] (rest data-scaled) ]
      (.lineTo ctx pt-x pt-y)))
  (.stroke ctx))

(defn long-to-local-date-time [ val ]
  (time/to-default-time-zone (time-coerce/from-long val)))

(defn format-xlabel [ val ]
  (time-format/unparse dtf-axis-label (long-to-local-date-time val)))

(defn format-ylabel [ val ]
  (.toFixed val 2))

(defn draw-series [ ctx w h data x-range ]
  (with-preserved-ctx ctx
    (aset ctx "lineWidth" 0)
    (aset ctx "strokeStyle" "#0000FF")
    (aset ctx "font" "12px Arial")
    (let [y-range (rescale-range (s-yrange data) 1.2)]
      (unless (empty? data)
        (with-preserved-ctx ctx
          (clip-rect ctx 0 0 w h)
          (draw-series-line ctx data x-range y-range w h))
        (draw-ylabel ctx (format-ylabel (:min y-range)) -2 (- h 8))
        (draw-ylabel ctx (format-ylabel (:max y-range)) -2 8)
        (draw-xlabel ctx (format-xlabel (:min x-range)) 0 h true)
        (draw-xlabel ctx (format-xlabel (:max x-range)) w h false)))))

(defn draw-background [ ctx w h ]
  (with-preserved-ctx ctx
    (aset ctx "fillStyle" "#e0e0e0")
    (.fillRect ctx 0 0 w h)))

(defn draw-series-background [ ctx w h ]
  (with-preserved-ctx ctx
    (aset ctx "fillStyle" "#FFFFFF")
    (.fillRect ctx 0 0 w h)))

(defn draw-frame [ ctx w h ]
  (with-preserved-ctx ctx
    (.beginPath ctx)
    (aset ctx "lineWidth" 1)
    (aset ctx "strokeStyle" "#000000")
    (.moveTo ctx 0.5 0.5)
    (.lineTo ctx 0.5 (- h 0.5))
    (.lineTo ctx (- w 0.5) (- h 0.5))
    (.stroke ctx)))

(defn draw [ ctx w h sinfo ]
  (draw-background ctx w h)
  (let [w (- w y-axis-space tsplot-right-margin)
        h (- h x-axis-space)]
    (with-preserved-ctx ctx
      (.translate ctx y-axis-space 0)
      (draw-series-background ctx w h)
      (draw-series ctx w h (:data sinfo) {:min (:begin sinfo) :max (:end sinfo)})
      (draw-frame ctx w h))))
