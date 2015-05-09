(ns italianverbs.session
    (:use
    [hiccup core])
    (:require [clojure.string :as string])
    (:import (java.security ;; TOODO: what are these imports doing here?
              NoSuchAlgorithmException
              MessageDigest)
             (java.math BigInteger)))

(defn get-session-key [request]
  (let [cookies (get request :cookies)]
    (if cookies
      (let [ring-session (get cookies "ring-session")]
        (if ring-session
          (get ring-session :value))))))

(defn request-to-session [request]
  (get (get (get request :cookies) "ring-session") :value))
