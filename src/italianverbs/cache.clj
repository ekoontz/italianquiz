(ns italianverbs.cache
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:require
   [clojure.core :exclude [get-in]]

   ;; TODO: comment is misleading in that we never call core/get-in from this file.
   [clojure.core :as core] ;; This allows us to use core's get-in by doing "(core/get-in ..)"

   [clojure.set :refer :all]
   [clojure.string :as string]

   [clojure.tools.logging :as log]

   [italianverbs.lexicon :refer :all]
   [italianverbs.lexiconfn :refer :all]
   [italianverbs.morphology :refer [finalize fo fo-ps]]
   [italianverbs.over :exclude [overc overh]]
   [italianverbs.over :as over]
   [italianverbs.unify :refer :all :exclude [unify]]))

;; For now, this cache is just a stub; no actual caching is done; it simply calls 
;; the over/ equivalents of each of the defined functions.

(def head-cache {})
(def comp-cache {})
(def lex-cache nil)

(defn build-lex-sch-cache [phrases lexicon all-phrases]
  "Build a mapping of phrases onto subsets of the lexicon. The two values (subsets of the lexicon) to be
   generated for each key (phrase) are: 
   1. the subset of the lexicon that can be the head of this phrase.
   2. the subset of the lexicon that can be the complement of this phrase.

   End result is a set of phrase => {:comp subset-of-lexicon 
                                     :head subset-of-lexicon}."
  (if (not (empty? phrases))
    (conj
     {(:comment (first phrases))
      {:comp
       (filter (fn [lex]
                 (not (fail? (unifyc (first phrases)
                                     {:comp lex}))))
               lexicon)

       :comp-phrases
       (filter (fn [comp-phrase]
                 (not (fail? (unifyc (first phrases)
                                     {:comp comp-phrase}))))
               all-phrases)

       :head-phrases
       (filter (fn [head-phrase]
                 (not (fail? (unifyc (first phrases)
                                     {:head head-phrase}))))
               all-phrases)

       :head
       (filter (fn [lex]
                 (not (fail? (unifyc (first phrases)
                                     {:head lex}))))
               lexicon)}}
     (build-lex-sch-cache (rest phrases) lexicon all-phrases))
    {}))

(defn get-cache [phrases lexicon]
  (if (nil? lex-cache)
    (do (def lex-cache (build-lex-sch-cache phrases lexicon phrases))
        lex-cache)
    lex-cache))

(defn get-lex [schema head-or-comp lexicon]
  "get the non-fail subset of every way of adding each lexeme as either the head
   or the comp (depending on head-or-comp) to the phrase indicated by the given schema" ;; TODO: document
  (if (not (map? schema))
    (throw (Exception. (str "get-lex was passed with the wrong type of arguments. This (schema) should be a map: " schema))))
  (log/debug (str "get-lex for schema: " (:comment schema)))
  (if (nil? (:comment schema))
    (log/error (str "no schema for: " schema)))
  (let [cache lex-cache
        result (cond (and (= :head head-or-comp)
                          (not (nil? (:head (get cache (:comment schema))))))
                     (:head (get cache (:comment schema)))

                     (and (= :comp head-or-comp)
                          (not (nil? (:comp (get cache (:comment schema))))))
                     (:comp (get cache (:comment schema)))

                     true
                     (throw (Exception. (str "get-lex called incorrectly or cache is empty."))))]
    (lazy-shuffle result)))

(defn over [parents child1 & [child2]]
  (over/over parents child1 child2))

(defn overh [parent head]
  (over/overh parent head))

(defn overc [parent comp]
  (over/overc parent comp))

(defn overc-complement-is-lexeme-1 [parent lex]
  (log/trace (str "overc-complement-is-lexeme-1 with parent: " (fo-ps parent)))
  (if (not (empty? lex))
    (lazy-cat (overc parent (first lex))
              (overc-complement-is-lexeme-1 parent (rest lex)))))

(defn overc-complement-is-lexeme [parents lexicon]
  (log/trace (str "overc-complement-is-lexeme with parents type: " (type parents)))
  (if (not (empty? parents))
    (let [parent (first parents)]
      (lazy-cat (overc-complement-is-lexeme-1 parent (get-lex parent :comp lexicon))
                (overc-complement-is-lexeme (rest parents) lexicon)))))

(defn get-head-phrases-of [parent]
  (let [cache lex-cache 
        result (:head-phrases (get cache (:comment parent)))
        result (if (nil? result) (list) result)]
    (if (empty? result)
      (log/warn (str "headed-phrases of parent: " (:comment parent) " is empty.")))
    (lazy-shuffle result)))

(defn get-comp-phrases-of [parent]
  (let [cache lex-cache
        result (:comp-phrases (get cache (:comment parent)))
        result (if (nil? result) (list) result)]
    (if (empty? result)
      (log/warn (str "comp-phrases of parent: " (:comment parent) " is empty.")))
    (lazy-shuffle result)))
