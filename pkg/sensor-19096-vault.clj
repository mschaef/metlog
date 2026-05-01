;; sensor-19096-vault.clj
;;
;; Custom sensor definitions go here

(defn measure-http-get [ url ]
  (let [begin-t (System/currentTimeMillis)
        body (http-request-text url)]
    (and body
         {:bytes (.length body)
          :duration-msec (- (System/currentTimeMillis) begin-t)})))

(defsensor "mschaef-main-19096-vault" {:poll-interval (minutes 1)}
  (measure-http-get "https://mschaef.com"))

(defsensor "mschaef-rss-19096-vault" {:poll-interval (minutes 1)}
  (measure-http-get "https://mschaef.com/feed/rss"))

(defsensor "bandwidth-test-19096-vault" {:poll-interval (minutes 30)}
  (measure-http-get "https://s3.amazonaws.com/bandwidth-test.mschaef.com/10-mib"))

;;; Vault Status

(defn directory-space-used [ dir ]
  (apply + (map #(.length %) (file-seq (clojure.java.io/file dir)))))

(defsensor shvaultphl-cpu {:poll-interval (minutes 1)}
  (.getSystemCpuLoad (java.lang.management.ManagementFactory/getOperatingSystemMXBean)))

(defsensor shvaultphl-free-disk-root {:poll-interval (minutes 1)}
  (.getFreeSpace (java.io.File. "/")))

(defsensor shvaultphl-free-disk-data {:poll-interval (minutes 1)}
  (.getFreeSpace (java.io.File. "/data")))

(defsensor shvaultphl-free-disk-data2 {:poll-interval (minutes 1)}
  (.getFreeSpace (java.io.File. "/data2")))
