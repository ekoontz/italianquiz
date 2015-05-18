(ns italianverbs.repair)

(require '[italianverbs.unify :refer [unify]])
(require '[italianverbs.italiano :refer [fill-by-spec fill-verb]])
(require '[italianverbs.english :refer :all])
(require '[italianverbs.korma :refer :all])
(require '[korma.core :refer :all])
(require '[korma.db :refer :all])

;; actually works: (dostuff {:fill-verb "accompagnare"})
(defn dostuff [& lists]
  (do
    (exec-raw "TRUNCATE expression_import")
    (.size (map (fn [list]
                  (do
                    (if (:fill-verb list)
                      (let [verb (:fill-verb list)]
                        (.size (fill-verb verb 1 :top "expression_import"))))))
                lists))
    (exec-raw ["SELECT count(*) FROM expression_import"] :results)

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

    (exec-raw "INSERT INTO expression (language,model,surface,structure,serialized)
                    SELECT language,model,surface,structure,serialized
                      FROM expression_distinct")
    ))


(defn process [& lists]
  (do
    ;; 1. clear staging table
    (exec-raw "TRUNCATE expression_import")

    ;; 2. do the actions specified by each list.
    (.size (map (fn [list]
           (map (fn [elem]
                  (do
                    (if (:sql elem)
                      (exec-raw (:sql elem)))
                    (if (:fill elem)
                      (.size (fill-by-spec 1 (->> elem :fill :spec) "expression_import")))
                    (if (:fill-verb elem)
                      (.size (fill-verb (:fill-verb elem) 1 :top "expression_import")))))
                 list))
          lists))

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
  (exec-raw "INSERT INTO expression (language,model,surface,structure,serialized)
                    SELECT language,model,surface,structure,serialized
                      FROM expression_import")))

;; (process accompany care fornire indossare moltiplicare recuperare riconoscere riscaldare)
