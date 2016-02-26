(ns metlog-client.tsplot
  (:require-macros [metlog-client.macros :refer [ with-preserved-ctx unless watch ]])
  (:require [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]))

(def dtf-axis-label (time-format/formatter "MM-dd HH:mm"))
(def dtf-header (time-format/formatter "yyyy-MM-dd HH:mm"))
(def x-axis-space 20)
(def tsplot-right-margin 5)
(def y-axis-space 40)

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

(defn rescale-range [ range factor ]
  (let [ scaled-delta (* (/ (- factor 1) 2) (- (:max range) (:min range) )) ]
    {:max (+ (:max range) scaled-delta)
     :min (- (:min range) scaled-delta)}))

(defn draw-xlabel [ ctx text x y left? ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (if left? x (- x w)) (+ 12 y))))

(defn draw-ylabel [ ctx text x y ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (- x w) (+ y 4))))

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

(defn clip-rect [ ctx x y w h ]
  (.beginPath ctx)
  (.rect ctx x y w h)
  (.clip ctx))

(def y-line-intervals [0.1 0.2 0.5
                       1 2 5
                       10 20 50
                       100 200 500])

(defn range-contains-zero? [ range ]
  (and (< (:min range) 0.0)
       (> (:max range) 0.0)))

(defn range-magnitude [ range ]
  (- (:max range) (:min range)))

(defn largest-y-range-magnitude [ y-range ]
  (if (range-contains-zero? y-range)
    (if (> (:max y-range) (.abs js/Math (:min y-range)))
      {:t (/ (:max y-range) (range-magnitude y-range)) :magnitude (:max y-range)}
      {:t (/ (:max y-range) (range-magnitude y-range)) :magnitude (- (:min y-range))})
    {:t 1.0 :magnitude (- (:max y-range) (:min y-range)) }))

(def pixels-per-y-label 20)

(defn first-that [ pred? xs ]
  (first (filter pred? xs)))

(defn find-y-grid-interval [ h y-range ]
  (let [{ t :t magnitude :magnitude } (largest-y-range-magnitude y-range)
        avail-pixels (* t h)]
    (first
     (first-that #(< (second %) avail-pixels)
                 (map #(vector % (* pixels-per-y-label (/ magnitude %)))
                      y-line-intervals)))))

(defn draw-y-grid-line [ ctx w y value emphasize? ]
  (with-preserved-ctx ctx
    (draw-ylabel ctx (format-ylabel value) -2 y)
    (aset ctx "lineWidth" 0)
    (if emphasize?
      (aset ctx "strokeStyle" "#000000")
      (aset ctx "strokeStyle" "#707070"))
    (unless emphasize?
       (.setLineDash ctx #js [ 2 2 ]))
    (.beginPath ctx)
    (.moveTo ctx 0 y)
    (.lineTo ctx w y)
    (.stroke ctx)))

(defn pixel-snap [ t ]
  (+ 0.5 (.floor js/Math t)))

(defn draw-y-grid [ ctx w h y-range ]
  (with-preserved-ctx ctx
    (let [ty (translate-fn-invert y-range h)
          y-interval (find-y-grid-interval h y-range)]
      (if (range-contains-zero? y-range)
        (let [positive-lines (.floor js/Math (/ (:max y-range) y-interval))
              negative-lines (.floor js/Math (/ (- (:min y-range)) y-interval))]
          (draw-y-grid-line ctx w (pixel-snap (ty 0.0)) 0.0 true)
          (doseq [ii (range positive-lines)]
            (draw-y-grid-line ctx w (pixel-snap (ty (* y-interval (+ ii 1)))) (* y-interval (+ ii 1)) false))
          (doseq [ii (range negative-lines)]
            (draw-y-grid-line ctx w (pixel-snap (ty (* (- y-interval) (+ ii 1)))) (* (- y-interval) (+ ii 1)) false)))
        (let [ lines (.floor js/Math (/ (- (:max y-range) (:min y-range)) y-interval))]
          (doseq [ii (range lines)]
            (draw-y-grid-line ctx w (pixel-snap (ty (+ (:min y-range) (* y-interval (+ ii 1)))))
                              (+ (:min y-range) (* y-interval (+ ii 1))) false)))))))

(defn draw-series [ ctx w h data x-range ]
  (with-preserved-ctx ctx
    (aset ctx "lineWidth" 0)
    (aset ctx "strokeStyle" "#0000FF")
    (aset ctx "font" "12px Arial")
    (let [y-range (rescale-range (s-yrange data) 1.2)]
      (unless (empty? data)
              (draw-y-grid ctx w h y-range)
              (with-preserved-ctx ctx
                (clip-rect ctx 0 0 w h)
                (draw-series-line ctx data x-range y-range w h))
              (draw-xlabel ctx (format-xlabel (:min x-range)) 0 h true)
              (draw-xlabel ctx (format-xlabel (:max x-range)) w h false))))) 

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
    (.stroke ctx)))

(defn draw [ ctx w h data begin-t end-t]
  (draw-series-background ctx w h)
  (let [w (- w y-axis-space tsplot-right-margin)
        h (- h x-axis-space)]
    (with-preserved-ctx ctx
      (.translate ctx y-axis-space 0)
      (draw-series-background ctx w h)
      (draw-series ctx w h data {:min begin-t :max end-t})
      (draw-frame ctx w h))))

