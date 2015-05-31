;; usage:
;; export DATABASE_URL=<target database>
;; lein run -m repair.divertirsi/fix
(ns repair.divertirsi)
(require '[italianverbs.repair :refer :all])
(require '[italianverbs.english :as en])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.repair :refer [process]])

(defn fix []
  (process 
   [
    {:fill-verb "alzarsi"
     :source-model en/small-plus-vp-pronoun
     :target-model it/small-plus-vp-pronoun}
    {:fill-verb "divertirsi"
     :source-model en/small-plus-vp-pronoun
     :target-model it/small-plus-vp-pronoun}
    {:fill-verb "preparsi"
     :source-model en/small-plus-vp-pronoun
     :target-model it/small-plus-vp-pronoun}
    {:fill-verb "svegliarsi"
     :source-model en/small-plus-vp-pronoun
     :target-model it/small-plus-vp-pronoun}
    ]))








