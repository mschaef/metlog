;; Copyright (c) 2015-2026 Michael Schaeffer (dba East Coast Toolworks)
;;
;; Licensed as below.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;       http://www.apache.org/licenses/LICENSE-2.0
;;
;; The license is also includes at the root of the project in the file
;; LICENSE.
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; You must not remove this notice, or any other, from this software.

(ns metlog.vault.hashid
  "A copy of ectcommon.site.hashid, which replaces playbook.hashid
  (gone as of playbook 0.2). Delete this in favor of ectcommon's once
  the vault is on ectcommon.

  Unlike playbook.hashid, encode doesn't prefix the type name, so new
  dashboard URLs are shorter; decode still accepts the old, prefixed
  form, so existing links keep working."
  (:require [hashids.core :as hashids]))

(def default-opts
  {:salt ""
   :min-length 8})

(defn- typed-opts [opts type]
  (assoc opts :salt (str (name type) (:salt opts))))

(defn encode
  ([type id]
   (encode default-opts type id))

  ([opts type id]
   (hashids/encode (typed-opts (merge default-opts opts) type) id)))

(defn- decode-first
  "The single id hid decodes to, or nil. The hashids library throws (an
  AssertionError) for some malformed input, such as short strings like
  \"7\"; that's an invalid hashid too, not an error."
  [opts hid]
  (let [decoded (try
                  (hashids/decode opts hid)
                  (catch AssertionError _ nil)
                  (catch Exception _ nil))]
    (when (= 1 (count decoded))
      (first decoded))))

(defn decode
  "The id that hid (a hashid made by encode for the same type and
  options) stands for, or nil if hid isn't a valid hashid for that
  type. Never throws for bad input, so a mangled URL can't cause an
  error."
  ([type hid]
   (decode default-opts type hid))

  ([opts type hid]
   (when (string? hid)
     (let [typename (name type)
           opts (typed-opts (merge default-opts opts) type)]
       (or
        (decode-first opts hid)
        ;; Some older hash ID's are prefixed with their
        ;; type-id. This catches those. The core hashid library
        ;; verifies the decode of a hash, meaning this should not
        ;; run into issues where a type prefix matches the first
        ;; few digits of a legimate hash. The possibility of the
        ;; result being a legitimately valid hashid is very low.
        (when (.startsWith ^String hid typename)
          (decode-first opts (.substring ^String hid (count typename)))))))))
