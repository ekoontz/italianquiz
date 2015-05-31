;; usage:
;; export DATABASE_URL=<target database>
;; lein run -m italianverbs.repair.2/repair
(ns italianverbs.repair.2)
(require '[italianverbs.repair :refer [process]])

(defn repair []
   (process 
    [{:fill-verb "trasferire"}]
))

