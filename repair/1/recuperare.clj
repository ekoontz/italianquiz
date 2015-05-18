(ns italianverbs.repair)

(def recuperare
  [{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'root'->'italiano'->>'italiano' = 'recouperare';"}
   {:fill-verb "recuperare"}])


 
	
