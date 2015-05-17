(ns italianverbs.repair.1)

(def recuperare
  [{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'root'->'italiano'->>'italiano' = 'recouperare';"}
   {:fill-verb "recuperare"}])


 
	
