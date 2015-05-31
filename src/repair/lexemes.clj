;; Usage:
;; export DATABASE_URL=<target database>
;; lein run -m repair.lexemes/repair
(ns repair.lexemes)
(require '[italianverbs.borges.writer :refer [write-lexicon]])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.english :as en])
(require '[italianverbs.espanol :as es])
(require '[korma.core :as k])

(defn repair []
  (write-lexicon "en" @en/lexicon)
  (write-lexicon "es" @es/lexicon)
  (write-lexicon "it" @it/lexicon))
