;; usage:
;; export POSTGRES_URL=<target database>
;; lein run -m repair.3/repair
(ns repair.3)
(require '[italianverbs.repair :refer [process]])

(defn repair []
  (process 
   [{:sql 
     "DELETE FROM expression 
             WHERE language='en' 
               AND (structure->'root'->'english'->>'english' = 'steal')"}

    {:fill-verb "rubare"}]))


