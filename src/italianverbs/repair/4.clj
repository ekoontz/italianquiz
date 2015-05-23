;; usage:
;; export POSTGRES_URL=<target database>
;; lein run -m italianverbs.repair.4/repair
(ns italianverbs.repair.4)
(require '[italianverbs.repair :refer [process]])

(defn repair []
  (process 
   [{:sql 
     "DELETE FROM expression 
             WHERE language='it' 
               AND (structure->'root'->'italiano'->>'italiano' = 'contraccombiare')"}

    {:fill-verb "contraccambiare"}]))



