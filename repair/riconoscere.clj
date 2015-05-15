[{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'root'->'italiano'->>'italiano' = 'riconoscere';"}
 {:clj
  [(truncate "expression_import")
   (fill-verb "riconoscere" 10 :top "expression_import")]}]


 
	
