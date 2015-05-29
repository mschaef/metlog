(ns metlog.macros)

(defmacro with-preserved-ctx [ ctx & body ]
  `(let [ ctx# ~ctx ]
     (.save ctx#)
     (let [ rc# (do ~@body) ]
       (.restore ctx#)
       rc#)))
