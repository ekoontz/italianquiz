[{:sql 
  "DELETE FROM expression WHERE language='it' AND structure->'root'->'italiano'->>'italiano' ILIKE '%gare' AND structure->'synsem'->'sem'->>'tense' = 'futuro';"}
 {:clj
  [(truncate "expression_import")
   (fill-verb "cercare" 10 :top "expression_import")
   (fill-verb "caricare" 10 :top "expression_import")]}]

 
	
