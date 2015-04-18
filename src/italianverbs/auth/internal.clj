(ns italianverbs.auth.internal)

;; Internal authentication - not third-party (e.g. Google, Facebook, Twitter, LinkedIn etc...)
(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[italianverbs.auth.internal :as internal])
(require '[italianverbs.korma :as db])
(require '[cemerick.friend 
           [workflows :as workflows]
           [credentials :as creds]])
(require '[cemerick.friend :as friend])

(derive ::admin ::user)

;; <TEST AUTHENTICATION/AUTHORIZATION>
(def users (atom {"franco" {:username "franco"
                            :password (creds/hash-bcrypt "franco")
                            :roles #{::user ::admin}}

                  "michael" {:username "michael"
                             :password (creds/hash-bcrypt "marcheschi")
                             :roles #{::user ::admin}}


                  "gino" {:username "gino"
                          :password (creds/hash-bcrypt "gino")
                          :roles #{::user}}}))
;; </TEST AUTHENTICATION/AUTHORIZATION>
