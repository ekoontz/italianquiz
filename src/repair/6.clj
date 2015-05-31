;; usage:
;; export POSTGRES_URL=<target database>
;; lein run -m repair.6/repair
(ns repair.6)
(require '[italianverbs.english :as en])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.repair :refer [process]])

(defn repair []
  (process 
   [{:fill-verb "vestirsi"
     :source-model en/small-plus-vp-pronoun
     :target-model it/small-plus-vp-pronoun}
    ]))







