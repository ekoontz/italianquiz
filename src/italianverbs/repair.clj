(ns italianverbs.repair)

(require '[italianverbs.borges.writer :refer [fill-by-spec fill-verb]])
(require '[italianverbs.english :refer :all])
(require '[italianverbs.korma :refer :all])
(require '[dag-unify.core :refer [unify]])
(require '[korma.core :refer :all])
(require '[korma.db :refer :all])

(defn process [ & units]
  (do
    (exec-raw "TRUNCATE expression_import")
    (.size (map (fn [unit]
           (.size (map (fn [member-of-unit]
                         (do
                           (if (:sql member-of-unit)
                             (do
                               (println (str "doing sql: " (:sql member-of-unit)))
                               (exec-raw (:sql member-of-unit))))

                           (if (:fill member-of-unit)
                             (do
                               (println (str "doing fill-by-spec: " (->> member-of-unit :fill :spec)))
                               (fill-by-spec (->> member-of-unit :fill :spec)
                                             "expression_import"
                                             (:source-model member-of-unit)
                                             (:target-model member-of-unit))))

                           (if (:fill-verb member-of-unit)
                             (do
                               (println (str "doing fill-verb: " (:fill-verb member-of-unit)))
                               (let [verb (:fill-verb member-of-unit)]
                                 (.size (fill-verb
                                         (:fill-verb member-of-unit)
                                         (if (:count member-of-unit)
                                           (:count member-of-unit)
                                           1)
                                         (:source-model member-of-unit)
                                         (:target-model member-of-unit))))))))
                       unit)))
         units))
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

;; (process accompany care fornire indossare moltiplicare recuperare riconoscere riscaldare)
