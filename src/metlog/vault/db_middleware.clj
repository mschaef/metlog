(ns metlog.vault.db-middleware
  "Ring middleware that binds a database connection for each request,
  but only takes a connection from the pool when the request first
  uses it. Requests that never touch the database (static resources,
  agent ingest, healthchecks) never hold a pooled connection.

  Intended to move into sql-file.middleware once it has proven itself."
  (:require [taoensso.timbre :as log]
            [sql-file.middleware :as sql-file-middleware])
  (:import [java.lang.reflect InvocationHandler InvocationTargetException Proxy]
           [java.sql Connection]))

(defn- lazy-connection
  "Return [proxy close-fn]. proxy is a java.sql.Connection that takes a
  real connection from datasource the first time any method is called
  on it, and delegates to it from then on. close-fn returns that real
  connection to the pool, if one was ever taken. If taking a
  connection fails (e.g. pool timeout), nothing is cached, and the
  next use tries again."
  [datasource]
  (let [conn (atom nil)
        realize! (fn []
                   (locking conn
                     (or @conn
                         (reset! conn (.getConnection datasource)))))
        close! (fn []
                 (locking conn
                   (when-let [c @conn]
                     (reset! conn nil)
                     (.close c))))
        handler (reify InvocationHandler
                  (invoke [_ proxy method args]
                    (case (.getName method)
                      ;; Object methods: answer for the proxy itself,
                      ;; so logging or hashing it never takes a connection.
                      "toString" (str "LazyConnection[" (or @conn "unopened") "]")
                      "hashCode" (System/identityHashCode proxy)
                      "equals" (identical? proxy (aget args 0))
                      ;; Closing or asking about an unopened connection
                      ;; shouldn't open one.
                      "close" (close!)
                      "isClosed" (if-let [c @conn] (.isClosed c) false)
                      (try
                        (.invoke method (realize!) args)
                        (catch InvocationTargetException ex
                          (throw (.getCause ex)))))))]
    [(Proxy/newProxyInstance (.getClassLoader Connection)
                             (into-array Class [Connection])
                             handler)
     close!]))

(defn call-with-lazy-db-connection
  "Call f with sql-file's *db* bound to a db-spec whose connection is
  taken from db-pool only on first use, and returned to the pool when
  f returns or throws."
  [db-pool f]
  (let [[conn close!] (lazy-connection (:datasource db-pool))]
    (try
      (binding [sql-file-middleware/*db* (assoc db-pool :connection conn)]
        (f))
      (finally
        (close!)))))

(defn wrap-lazy-db-connection [app db-pool]
  (fn [req]
    (call-with-lazy-db-connection db-pool #(app req))))
