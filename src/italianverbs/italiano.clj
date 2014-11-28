(ns italianverbs.italiano
  (:refer-clojure :exclude [get-in])
  (:require 
   [italianverbs.grammar.italiano :as gram]
   [italianverbs.lexicon.italiano :as lex]
   [italianverbs.morphology.italiano :as morph]))

(require '[clojure.tools.logging :as log])
(require '[italianverbs.cache :refer (build-lex-sch-cache create-index over spec-to-phrases)])
(require '[italianverbs.generate :as generate])
(require '[italianverbs.lexiconfn :refer (compile-lex map-function-on-map-vals unify)])
(require '[italianverbs.parse :as parse])
(require '[italianverbs.ug :refer :all])
(require '[italianverbs.unify :refer (get-in)])
(require '[italianverbs.unify :as unify])

(def get-string morph/get-string)
(def grammar gram/grammar)

(def lexicon
  (-> (compile-lex lex/lexicon-source morph/exception-generator morph/phonize morph/italian-specific-rules)

      ;; Cleanup functions can go here. Number them for ease of reading.
      ;; 1. this filters out any verbs without an inflection: infinitive verbs should have inflection ':infinitive', 
      ;; rather than not having any inflection.
      (map-function-on-map-vals 
       (fn [k vals]
         (filter #(or (not (= :verb (get-in % [:synsem :cat])))
                      (not (= :none (get-in % [:synsem :infl] :none))))
                 vals)))))

(defn lookup [token]
  "return the subset of lexemes that match this token from the lexicon."
  (morph/analyze token (fn [k]
                         (get lexicon k))))

(defn parse [string]
  (parse/parse string lexicon lookup grammar))

(def begin (System/currentTimeMillis))
(log/debug "building grammatical and lexical cache..")
(def cache nil)
;; TODO: trying to print cache takes forever and blows up emacs buffer:
;; figure out how to change printable version to (keys cache).
(def cache (create-index grammar (flatten (vals lexicon)) head-principle))

(def end (System/currentTimeMillis))
(log/info "Built grammatical and lexical cache in " (- end begin) " msec.")

(defn sentence [ & [spec]]
  (let [spec (if spec spec :top)]
    (generate/sentence spec grammar cache (flatten (vals lexicon)))))

(defn generate [ & [spec]]
  (let [spec (if spec spec :top)]
    (if (seq? spec)
      (map generate spec)
      (generate/generate spec
                         grammar
                         lexicon
                         cache))))

;; TODO: move the following 2 to lexicon.clj:
(def lookup-in
  "find all members of the collection that matches with query successfully."
  (fn [query collection]
    (loop [coll collection matches nil]
      (if (not (empty? coll))
        (let [first-val (first coll)
              result (unify/match (unify/copy query) (unify/copy first-val))]
          (if (not (unify/fail? result))
            (recur (rest coll)
                   (cons first-val matches))
            (recur (rest coll)
                   matches)))
        matches))))

(defn choose-lexeme [spec]
  (first (unify/lazy-shuffle (lookup-in spec (vals lexicon)))))

