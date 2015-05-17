(ns italianverbs.repair.1)

;; "Future tense of verbs whose infinitive form ends in -care or -gare. 
;; Previously was incorrectly generating things like 'io cercarò' rather than correct 'io cercharò'."
(def care
  [{:sql 
   "DELETE FROM expression 
          WHERE language='it' 
            AND (structure->'root'->'italiano'->>'italiano' ILIKE '%care'
             OR  structure->'root'->'italiano'->>'italiano' ILIKE '%gare')
            AND structure->'synsem'->'sem'->>'tense' = 'futuro';"}

   {:fill
    {:spec {:root {:italiano {:italiano "caricare"}}
            :synsem {:sem {:tense :futuro}}}}}

   {:fill
    {:spec {:root {:italiano {:italiano "cercare"}}
            :synsem {:sem {:tense :futuro}}}}}])

