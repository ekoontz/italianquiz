(ns italianverbs.repair.1)

(require '[italianverbs.repair :refer [process]])

;; "Future tense of verbs whose infinitive form ends in -care or -gare. 
;; Previously was incorrectly generating things like 'io cercarò' rather than correct 'io cercharò'."
(def care
  [{:sql 
   "DELETE FROM expression 
          WHERE language='it' 
            AND (structure->'root'->'italiano'->>'italiano' ILIKE '%care'
             OR  structure->'root'->'italiano'->>'italiano' ILIKE '%gare')
            AND structure->'synsem'->'sem'->>'tense' = 'futuro';"}
   {:sql "TRUNCATE expression_import"}

   {:fill
    {:spec {:root {:italiano {:italiano "caricare"}}
            :synsem {:sem {:tense :futuro}}}}}

   {:fill
    {:spec {:root {:italiano {:italiano "cercare"}}
            :synsem {:sem {:tense :futuro}}}}}

   {:sql "DROP TABLE expression_distinct;"}
   {:sql "CREATE TABLE 
                expression_distinct (
       language text,
          model text,
        surface text,
      structure jsonb,
     serialized text
     );"}

   {:sql
    "INSERT INTO expression_distinct (language,model,surface,structure,serialized)
 SELECT DISTINCT language,model,surface,structure,serialized 
            FROM expression_import;"}

   {:sql 
    "TRUNCATE expression_import;"}
   
   {:sql
    "INSERT INTO expression_import (language,model,surface,structure,serialized) 
         SELECT language,model,surface,structure,serialized 
           FROM expression_distinct;"}

   {:sql
    "INSERT INTO expression (language,model,surface,structure,serialized) 
SELECT DISTINCT language,model,surface,structure,serialized 
           FROM expression_import;"}])
