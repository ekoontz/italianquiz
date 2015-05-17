(ns italianverbs.repair.1)

(def moltiplicare
  [{:sql
    "DELETE FROM expression 
           WHERE language='it' 
             AND structure->'root'->'italiano'->>'italiano' = 'moltiplicare';"}
   {:sql
    "DELETE FROM expression 
           WHERE language='en' 
             AND structure->'root'->'english'->>'english' = 'multiply';"}

   {:fill-verb "moltiplicare"}])




 
	
