(ns italianverbs.repair)

(require '[italianverbs.unify :refer [unify]])
(require '[italianverbs.italiano :refer [fill-verb]])
(require '[italianverbs.english :refer :all])

(defmacro defrepair [name comment operations]
  "_operations_ is a vector. defrepair should evaluate each element *in order*. Each element could be either a string or a s-expression. If the former, it should be evaluated as SQL; if the latter, it should be evaluated as Clojure code."
  (ref 
  42))

(defrepair "care" 
  "Future tense of verbs whose infinitive form ends in -care or -gare. Previously was incorrectly generating things like 'io cercarò' rather than correct 'io cercharò'."
  ["DELETE FROM expression 
          WHERE language='it' 
            AND (structure->'root'->'italiano'->>'italiano' ILIKE '%care'
             OR  structure->'root'->'italiano'->>'italiano' ILIKE '%gare')
            AND structure->'synsem'->'sem'->>'tense' = 'futuro';
    TRUNCATE expression_import"
   (fill-verb "cercare" 10 :top "expression_import")
   (fill-verb "caricare" 10 :top "expression_import")
   {:sql
    "DROP TABLE expression_distinct;
     CREATE TABLE expression_distinct (
       language text,
       model text,
       surface text,
       structure jsonb,
       serialized text
     );
    INSERT INTO expression_distinct (language,model,surface,structure,serialized) 
 SELECT DISTINCT language,model,surface,structure,serialized 
            FROM expression_import;
    TRUNCATE expression_import;
    INSERT INTO expression_import (language,model,surface,structure,serialized) 
          SELECT language,model,surface,structure,serialized 
            FROM expression_distinct;
    INSERT INTO expression (language,model,surface,structure,serialized) 
 SELECT DISTINCT language,model,surface,structure,serialized 
            FROM expression_import;"}])





