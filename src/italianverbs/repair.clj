(ns italianverbs.repair)

(require '[clojure.tools.logging :as log])
(require '[italianverbs.unify :refer [unify]])
(require '[italianverbs.italiano :refer [fill-by-spec fill-verb]])
(require '[italianverbs.english :refer :all])
(require '[italianverbs.korma :refer :all])
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
                               (log/debug (str "doing sql: " (:sql member-of-unit)))
                               (exec-raw (:sql member-of-unit))))
                           (if (:fill member-of-unit)
                             (do
                               (log/debug (str "doing fill-by-spec: " (->> member-of-unit :fill :spec)))
                               (fill-by-spec (->> member-of-unit :fill :spec)
                                             10
                                             "expression_import")))

                           (if (:fill-verb member-of-unit)
                             (do
                               (log/debug (str "doing fill-verb: " (:fill-verb member-of-unit)))
                               (let [verb (:fill-verb member-of-unit)]
                                 (.size (fill-verb verb 10 :top "expression_import")))))))

                       unit)))
         units))
    
    (log/info (str "expression_import count:" 
                   (:count (first (exec-raw ["SELECT count(*) FROM expression_import"]
                                            :results)))))

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

    (log/info (str "expression_distinct count:" 
                   (:count (first (exec-raw ["SELECT count(*) FROM expression_distinct"]
                                            :results)))))

    (exec-raw "INSERT INTO expression (language,model,surface,structure,serialized)
                    SELECT language,model,surface,structure,serialized
                      FROM expression_distinct")
    ))
