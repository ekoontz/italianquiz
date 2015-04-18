(ns italianverbs.auth.google)

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

(def users (atom {}))

