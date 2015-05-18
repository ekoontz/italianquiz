(ns italianverbs.repair)

(def riconoscere
  [{:sql 
    "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'root'->'italiano'->>'italiano' = 'riconoscere';"}
   {:fill-verb "riconoscere"}])



 
	
