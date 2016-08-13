(ns metlog-client.logger)

(defmacro log [ level & exprs ]
  `(metlog-client.logger/log* ~(str *ns*) ~level ~@exprs))

(defmacro trace [ & exprs ] `(log :trace ~@exprs))
(defmacro debug [ & exprs ] `(log :debug ~@exprs))
(defmacro info  [ & exprs ] `(log :info ~@exprs))
(defmacro warn  [ & exprs ] `(log :warn ~@exprs))
(defmacro error [ & exprs ] `(log :error ~@exprs))
(defmacro fatal [ & exprs ] `(log :fatal ~@exprs))

;;;

(defmacro watch
  ([ expr ]
   `(watch :debug ~expr))
  
  ([ level expr ]
   `(log ~level "WATCH" (pr-str '~expr) "=>" ~expr)))
