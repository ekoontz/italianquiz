[{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'root'->'italiano'->>'italiano' = 'moltiplicare';"}
 {:clj
  [(truncate "expression_import")
   (fill-verb "moltiplicare" 10 :top "expression_import")]}]


 
	
