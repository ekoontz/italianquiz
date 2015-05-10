(ns italianverbs.auth.internal)

;; Internal authentication - not third-party (e.g. Google, Facebook, Twitter, LinkedIn etc...)
(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[compojure.core :as compojure :refer [context GET PUT POST DELETE ANY]])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[italianverbs.korma :as db])
(require '[cemerick.friend 
           [workflows :as workflows]
           [credentials :as creds]])
(require '[cemerick.friend :as friend])
(require '[ring.util.response :as resp])

(derive ::admin ::user)

;; internal authentication database - for testing only; not production, 
;; as passwords are plaintext. TODO: keep passwords elsewhere.
(def users (atom {"franco" {:username "franco"
                            :password (creds/hash-bcrypt "franco")
                            :roles #{::user ::admin}}

                  "michael" {:username "michael"
                             :password (creds/hash-bcrypt "marcheschi")
                             :roles #{::user ::admin}}
                  
                  "gino" {:username "gino"
                          :password (creds/hash-bcrypt "gino")
                          :roles #{::user}}}))

(def routes
  (compojure/routes
   (GET "/login" request
        (resp/redirect "/"))
   (GET "/login/" request
        (resp/redirect "/"))
   (POST "/login" request
         (resp/redirect "/"))))


