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
     ;; create a key-value pair, K -> V:
     {
      ;; this is the key K: a string such as "noun-phrase".
      (:comment (first phrases)) 
      
      ;; this is the value V: information about the phrase represented by the key K.
      ;; value V is itself a map with four keys: :comp, :add-all-lexemes-as-comp, :head-phrases, and :head.
      {:comp
       (future (filter (fn [lex]
                         (not (fail? (unifyc (first phrases)
                                             {:comp lex}))))
                       lexicon))

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

(defn initialize-cache [grammar lexicon]
  (get-cache grammar lexicon))

(defn get-lex [schema head-or-comp]
  "get the non-fail subset of every way of adding each lexeme as either the head
   or the comp (depending on head-or-comp) to the phrase indicated by the given schema" ;; TODO: document
  (if (not (map? schema))
    (throw (Exception. (str "get-lex was passed with the wrong type of arguments. This (schema) should be a map: " schema))))
  (if (nil? (:comment schema))
    (throw (Exception. (str "schema has no comment (no key to lookup in the cache)"))))
  (log/debug (str "get-lex for schema: " (:comment schema)))
  (let [cache-entry (get lex-cache (:comment schema))]
    (if (nil? cache-entry)
      (throw (Exception. (str "no cache entry for key: " (:comment schema)))))

    (lazy-shuffle (cond (and (= :head head-or-comp)
                             (not (nil? (:head cache-entry))))
                        (:head cache-entry)

                        (and (= :comp head-or-comp)
                             (not (nil? (:comp cache-entry))))
                        (deref (:comp cache-entry))

                        true
                        (throw (Exception. (str "get-lex called incorrectly: head-or-comp was:" head-or-comp)))))))
  
(defn over [parents child1 & [child2]]
  (over/over parents child1 child2))

(defn overh [parent head]
  (over/overh parent head))

(defn overc [parent comp]
  (over/overc parent comp))

(defn get-overc [parent]
  nil)

(defn set-overc [parent overc]
  nil)

(defn overc-complement-is-lexeme [parents lexicon]
  (log/trace (str "overc-complement-is-lexeme with parents type: " (type parents)))
  (mapcat (fn [parent]
            (mapcat (fn [lexeme]
                      (overc parent lexeme))
                    (if (future? (get-lex parent :comp))
                      (lazy-seq (deref (get-lex parent :comp)))
                      (get-lex parent :comp))))
          parents))

(defn get-head-phrases-of [parent]
  (let [cache lex-cache 
        result (:head-phrases (get cache (:comment parent)))
        result (if (nil? result) (list) result)]
    (if (empty? result)
      (log/warn (str "headed-phrases of parent: " (:comment parent) " is empty.")))
    result))

(defn get-comp-phrases-of [parent]
  (let [cache lex-cache
        result (:comp-phrases (get cache (:comment parent)))
        result (if (nil? result) (list) result)]
    (if (empty? result)
      (log/warn (str "comp-phrases of parent: " (:comment parent) " is empty.")))
    (lazy-shuffle result)))
