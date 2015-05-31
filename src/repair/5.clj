;; usage:
;; export POSTGRES_URL=<target database>
;; lein run -m repair.5/repair
(ns repair.5)
(require '[italianverbs.english :as en])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.repair :refer [process]])

;; (fill-by-spec {:root {:italiano {:italiano "lavarsi"}}}
;;   1 "expression" small-plus-vp-pronoun "small-plus-vp-pronoun")
;; 

(defn repair []
  (process 
   [{:fill-verb "lavarsi"
     :target-model it/small-plus-vp-pronoun
     :source-model en/small-plus-vp-pronoun}
    ]))








