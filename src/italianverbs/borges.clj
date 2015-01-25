(ns italianverbs.borges
  [:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [korma.core :as db]
   [italianverbs.english :as en]
   [italianverbs.italiano :as it]
   [italianverbs.korma :as korma]
   [italianverbs.unify :as unify :refer [deserialize strip-refs unify]]])

;; requires Postgres 9.4 or higher for JSON operator '@>' support.

(defn foo [spec language-model]
  (unify/deserialize 
   (read-string (:serialized (first (db/exec-raw ["SELECT serialized::text FROM expression"] :results))))))

(defn generate [spec language-model]
  "generate a sentence matching 'spec' given the supplied language model."
  (let [spec (unify spec
                    {:synsem {:subcat '()}})
        language-model (if (future? language-model)
                         @language-model
                         language-model)

        debug (log/debug (str "generate: pre-enrich:" spec))

        ;; apply language-specific constraints on generation.
        spec (if (:enrich language-model)
               ((:enrich language-model) spec)
                spec)

        debug (log/debug (str "generate: post-enrich:" spec))

        language-name ;; TODO: add to API
        (cond (= language-model @en/small)
              "en"
              true
              "it")

        ;; normalize for JSON lookup
        json-input-spec (if (= :top spec)
                          {}
                          (get-in spec [:synsem]))

        json-spec (json/write-str (strip-refs json-input-spec))

               ]

;; synsem @> '{"sem": {"pred":"andare"}}';

;; italianverbs.borges> (generate :top "en")

    (log/debug (str "looking for expressions in language: " language-name))
    (log/debug (str "SQL: "
                   (str "SELECT structure FROM expression WHERE language=? AND structure->'synsem' @> "
                        "'" json-spec "'")))

    ;; e.g.
    ;; SELECT * FROM expression WHERE language='it' AND structure->'synsem' @> '{"sem": {"pred":"prendere"}}';

    (let [results (db/exec-raw [(str "SELECT structure::text FROM expression WHERE language=? AND structure->'synsem' @> "
                                     "'" json-spec "'")
                                [language-name]]
                               :results)]
      (if (empty? results) 
        nil
        (deserialize (json/read-str (:serialized (nth results (rand-int (.size results))))
                                    :key-fn keyword
                                    :value-fn (fn [k v]
                                                (cond (= k :italiano)
                                                      v
                                                      (= k :english)
                                                      v
                                                      (= k :espanol)
                                                      v
                                                      (string? v)
                                                      (keyword v)
                                                      :else v))))))))

;; thanks to http://schinckel.net/2014/05/25/querying-json-in-postgres/ for his good info.

;; SELECT count(*) FROM (SELECT DISTINCT english.surface AS en, italiano.surface AS it FROM italiano INNER JOIN english ON italiano.structure->synsem->'sem' = english.structure->synsem->'sem' ORDER BY english.surface) AS foo;
    
;; SELECT * FROM (SELECT synsem->'sem'->'pred'::text AS pred,surface FROM english) AS en WHERE en.pred='"andare"';
;; SELECT it.surface  FROM (SELECT synsem->'sem'->'pred' AS pred,surface,synsem FROM italiano) AS it WHERE it.synsem->'sem' @> '{"pred":"andare"}';

;; SELECT surface FROM italiano WHERE synsem->'sem' @> '{"pred":"andare"}';

;;SELECT italiano.surface,english.surface FROM italiano INNER JOIN english ON italiano.synsem->'sem' = english.synsem->'sem';

;; number of distinct english <-> italiano translation pairs
;; SELECT count(*) FROM (SELECT DISTINCT english.surface AS en, italiano.surface AS it FROM italiano INNER JOIN english ON italiano.synsem->'sem' = english.synsem->'sem' ORDER BY english.surface) AS foo;
