(ns italianverbs.repair.1)

(def indossare
  [{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'synsem'->'sem'->>'pred' = 'portare';"}
   {:fill-verb "indossare"}
   {:fill-verb "portare"}])

	
