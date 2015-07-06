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

(defmacro watch [ & exprs ]
  `(do
     ~@(map
        (fn [ expr ]
          `(.log js/console ">>" (pr-str '~expr) " => "
                 (let [ obj# ~expr ]
                   (if (satisfies? cljs.core/IPrintWithWriter obj#)
                     (pr-str obj#)
                     obj#))))
        exprs)))


