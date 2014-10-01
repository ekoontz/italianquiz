(ns italianverbs.mail
  (:require [clojurewerkz.mailer.core :refer [delivery-mode! with-settings with-defaults 
                                              with-settings build-email deliver-email render
                                              with-delivery-mode]]
            [environ.core :refer [env]]
            [postmark.core :refer [postmark]]))

(require '[environ.core :refer [env]])
(require '[clojure.string :as str])
(require 'digest)

(def api-key (env :postmark-api-key))

(def hash-secret (env :hash-secret))

;; sender email: ekoontz@verbcoach.com
(def send-message (postmark api-key "ekoontz@verbcoach.com"))

(def postmark-api-key (env :postmark-api-key))

(defn welcome-message [recipient]
  ;; TODO: validate recipient email; just something simple like X@Y, no domain checking; etc.
  (let [hash-code (digest/md5 (str hash-secret recipient))]
    (send-message {:to recipient
                   :subject "Welcome to Verbcoach!"
                   :text (str/join " " [ "Hello and welcome to Verbcoach! Click here to confirm your registration:"
                                         (str "http://verbcoach.com/confirm?id=" hash-code) ] )
                   :tag "welcome"})))


(defn forgot-password-message []
  (send-message {:to "ekoontz@hiro-tan.org"
                 :subject "Instructions for Password reset"
                 :text "Click here to reset your password."
                 :tag "support"}))


