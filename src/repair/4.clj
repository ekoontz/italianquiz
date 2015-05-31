;; usage:
;; export DATABASE_URL=<target database>
;; lein run -m repair.4/repair
(ns repair.4)
(require '[italianverbs.repair :refer [process]])

(defn repair []
  (process 
   [{:sql 
     "DELETE FROM expression 
             WHERE language='it' 
               AND (structure->'root'->'italiano'->>'italiano' = 'contraccombiare')"}

    {:fill-verb "contraccambiare"}]))

