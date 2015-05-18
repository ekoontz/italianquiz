;; usage:
;; lein run -m italianverbs.repair.1/repair
(ns italianverbs.repair.1)
(require '[italianverbs.repair :refer [process]])

(defn repair []
   (process 
    [{:sql 
      "DELETE FROM expression 
             WHERE language='en' 
               AND (structure->'root'->'english'->>'english' = 'accompany')"}

     {:fill-verb "accompagnare"}]

;; "Future tense of verbs whose infinitive form ends in -care or -gare. 
;; Previously was incorrectly generating things like 'io cercarò' rather than correct 'io cercharò'."
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
              :synsem {:sem {:tense :futuro}}}}}]


    [{:fill-verb "fornire"}]

    [{:sql 
      "DELETE FROM expression 
         WHERE language='it' 
           AND structure->'synsem'->'sem'->>'pred' = 'portare';"}
     {:fill-verb "indossare"}
     {:fill-verb "portare"}]

    [{:sql
      "DELETE FROM expression 
           WHERE language='it' 
             AND structure->'root'->'italiano'->>'italiano' = 'moltiplicare';"}
     {:sql
      "DELETE FROM expression 
           WHERE language='en' 
             AND structure->'root'->'english'->>'english' = 'multiply';"}
     
     {:fill-verb "moltiplicare"}]

    [{:sql 
      "DELETE FROM expression 
             WHERE language='it' 
               AND structure->'root'->'italiano'->>'italiano' = 'recouperare';"}
     {:fill-verb "recuperare"}]

    [{:sql 
      "DELETE FROM expression 
             WHERE language='it' 
               AND structure->'root'->'italiano'->>'italiano' = 'riconoscere';"}
     {:fill-verb "riconoscere"}]

    [{:fill-verb "riscaldare"}]

))

