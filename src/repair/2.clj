;; usage:
;; export DATABASE_URL=<target database>
;; lein run -m repair.2/repair
(ns repair.2)
(require '[italianverbs.repair :refer [process]])

(defn repair []
   (process 
    [{:fill-verb "trasferire"}]
))

