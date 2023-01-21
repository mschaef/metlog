(ns metlog-client.macros)

(defmacro with-preserved-ctx [ ctx & body ]
  `(let [ ctx# ~ctx ]
     (.save ctx#)
     (let [ rc# (do ~@body) ]
       (.restore ctx#)
       rc#)))

(defmacro unless [ condition & body ]
  `(when (not ~condition)
     ~@body))
