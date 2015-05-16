[{:sql 
  "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'synsem'->'sem'->>'pred' = 'portare';"}
 {:clj
  [(truncate "expression_import")
   (fill-verb "indossare" 10 :top "expression_import")
   (fill-verb "portare" 10 :top "expression_import")]}]

	
