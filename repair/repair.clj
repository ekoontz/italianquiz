(ns italianverbs.repair)

(require '[italianverbs.unify :refer [unify]])
(require '[italianverbs.italiano :refer [fill-by-spec fill-verb]])
(require '[italianverbs.english :refer :all])
(require '[italianverbs.korma :refer :all])
(require '[korma.core :refer :all])
(require '[korma.db :refer :all])

(defn process [& lists]
  (doall
   [
    ;; 1. clear staging table
    (exec-raw "TRUNCATE expression_import")

    ;; 2. do the actions specified by each list.
    (map (fn [list]
           (map (fn [elem]
                  (do
                    (if (:sql elem)
                      (exec-raw (:sql elem)))
                    (if (:fill elem)
                      (fill-by-spec 1 (->> elem :fill :spec) "expression_import"))
                    (if (:fill-verb elem)
                      (fill-verb (:fill-verb elem) 1 :top "expression_import"))))
                list))
         lists)

  ;; 3. dedup staging table.
  (exec-raw "DROP TABLE IF EXISTS expression_distinct")
  (exec-raw "CREATE TABLE expression_distinct (
    language text,
    model text,
    surface text,
    structure jsonb,
    serialized text)")
  (exec-raw "INSERT INTO expression_distinct (language,model,surface,structure,serialized) 
         SELECT DISTINCT language,model,surface,structure,serialized 
                    FROM expression_import")
  (exec-raw "TRUNCATE expression_import")
  (exec-raw "INSERT INTO expression_import (language,model,surface,structure,serialized) 
                  SELECT language,model,surface,structure,serialized 
                    FROM expression_distinct")

  ;; 4. import from staging to production table.
  ;; <todo>
]))


;; (process care fornire indossare moltiplicare recuperare riconoscere riscaldare)





