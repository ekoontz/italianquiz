(ns italianverbs.repair)

(require '[italianverbs.unify :refer [unify]])
(require '[italianverbs.italiano :refer [fill-by-spec fill-verb]])
(require '[italianverbs.english :refer :all])
(require '[italianverbs.korma :refer :all])
(require '[korma.core :refer :all])
(require '[korma.db :refer :all])

(defn process [list]
  (map (fn [elem]
         (do
           (if (:sql elem)
             (exec-raw (:sql elem)))
           (if (:fill elem)
             (fill-by-spec 1 (->> elem :fill :spec) "expression_import"))
           (if (:fill-verb)
             (fill-verb (:fill-verb elem) 1 :top "expression_import"))))
       list))



