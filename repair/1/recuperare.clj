[{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'root'->'italiano'->>'italiano' = 'recouperare';"}
 {:clj
  [(truncate "expression_import")
   (fill-verb "recuperare" 10 :top "expression_import")]}]


 
	
