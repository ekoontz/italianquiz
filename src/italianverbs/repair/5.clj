;; usage:
;; export POSTGRES_URL=<target database>
;; lein run -m italianverbs.repair.5/repair
(ns italianverbs.repair.5)
(require '[italianverbs.english :as en])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.repair :refer [process]])

;; (fill-by-spec {:root {:italiano {:italiano "lavarsi"}}}
;;   1 "expression" small-plus-vp-pronoun "small-plus-vp-pronoun")
;; 

(defn repair []
  (process 
   [

    {:fill-verb "vestirsi"
     :source-model it/small-plus-vp-pronoun
     :target-model en/small-plus-vp-pronoun}
    
    {:fill-verb "lavarsi"
     :source-model it/small-plus-vp-pronoun
     :target-model en/small-plus-vp-pronoun}
    ]))








