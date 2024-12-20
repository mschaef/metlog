(ns metlog.vault.util
  (:use compojure.core)
  (:require [cognitect.transit :as transit]
            [clojure.data.json :as json]
            [ring.util.response :as ring]))

(defn respond-success
  ([ message details ]
   (ring/response
    (merge details {:message message :success true})))

  ([ message ]
   (respond-success message {}))

  ([]
   (respond-success "ok")))

(defn respond-bad-request
  ([ message details ]
   (ring/bad-request
    (merge details {:message message :success false})))

  ([ message ]
   (respond-bad-request message {})))

