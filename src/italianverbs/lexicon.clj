(ns italianverbs.lexicon
  (:refer-clojure :exclude [get-in resolve find])
  (:require
   [clojure.set :refer (union)]
   [clojure.tools.logging :as log]
   [italianverbs.lexiconfn :refer (cache-serialization sem-impl subcat0 subcat1)]
   [italianverbs.lex.a_noi :refer :all]
   [italianverbs.lex.notizie_potere :refer :all]
   [italianverbs.lex.qualche_volta_volere :refer :all]
   [italianverbs.morphology :refer (fo)]
   [italianverbs.pos :refer :all]
   [italianverbs.unify :as unify]
   [italianverbs.unify :refer (fail? get-in isomorphic? lazy-shuffle ref? serialize unifyc)]))

;; stub that is redefined by italianverbs/mongo or interfaces to other dbs.
(defn clear! [])

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

(declare lexicon)

(defn lookup [query]
  (lookup-in query lexicon))

(defn choose-lexeme [spec]
  (first (lazy-shuffle (lookup spec))))

(defn it [italian]
  "same as it but no type conversion of singleton sets to take the first member."
  (let [result
        (union (set (lookup {:italian italian}))
               (set (lookup {:italian {:infinitive italian}}))
               (set (lookup {:italian {:infinitive {:infinitive italian}}}))
               (set (lookup {:italian {:italian italian}}))
               (set (lookup {:italian {:irregular {:passato italian}}})))]
    result))

(defn en [english]
  (lookup {:english english}))

(clear!)

(load "pos")

(load "lex/a_noi")
(load "lex/notizie_potere")
(load "lex/qualche_volta_volere")

(defn commonnoun [lexical-entry]
  ;; subcat non-empty: pronoun is false
  (cond (and (= (get-in lexical-entry '(:synsem :cat)) :noun)
             (= (not (empty? (get-in lexical-entry '(:synsem :subcat)))))
             (not (= (get-in lexical-entry '(:synsem :pronoun)) true)))
        (unifyc lexical-entry
                (unifyc agreement-noun
                        common-noun
                        {:synsem {:pronoun false
                                  :subcat {:1 {:cat :det}
                                           :2 '()}}}))
        true
        lexical-entry))

(defn semantic-implicature [lexical-entry]
  {:synsem {:sem (sem-impl (get-in lexical-entry '(:synsem :sem)))}})

(defn put-a-bird-on-it [lexical-entry]
  "example lexical entry transformer."
  (cond (map? lexical-entry)
        (conj {:bird 42}
              lexical-entry)
        true
        lexical-entry))

(defn category-to-subcat [lexical-entry]
  (cond (or (= (get-in lexical-entry '(:synsem :cat)) :det)
            (= (get-in lexical-entry '(:synsem :cat)) :adverb))
        (unifyc
         subcat0
         lexical-entry)

        (and (= (get-in lexical-entry '(:synsem :cat)) :adjective)
             (not (= (get-in lexical-entry '(:synsem :sem :comparative)) true)))
        (unifyc
         subcat1
         lexical-entry)

        (= (get-in lexical-entry '(:synsem :cat)) :sent-modifier)
        (unifyc
         {:synsem {:subcat {:1 {:cat :verb
                                :subcat '()}
                            :2 '()}}}
         lexical-entry)

        true
        lexical-entry))

(defn embed-phon [lexical-entry]
  (cond (string? (get-in lexical-entry '(:english)))
        (merge {:english {:english (get-in lexical-entry '(:english))}}
               (embed-phon (dissoc lexical-entry ':english)))
        (string? (get-in lexical-entry '(:italian)))
        (merge {:italian {:italian (get-in lexical-entry '(:italian))}}
               (embed-phon (dissoc lexical-entry ':italian)))
        true
        lexical-entry))

(defn intensifier-agreement [lexical-entry]
  (cond (= (get-in lexical-entry '(:synsem :cat)) :intensifier)
        (unifyc
         (let [agr (ref :top)]
           {:synsem {:agr agr
                     :subcat {:1 {:agr agr}
                              :2 {:agr agr}}}})
         lexical-entry)

         true lexical-entry))

(defn aux-verb-rule [lexical-entry]
  (cond (= (get-in lexical-entry '(:synsem :aux)) true)
        (unifyc lexical-entry
                verb-aux-type)
        true
        lexical-entry))

(defn verb-rule [lexical-entry]
  "every verb has at least a subject."
  (cond (= (get-in lexical-entry '(:synsem :cat)) :verb)
        (unifyc
         lexical-entry
         verb-subjective)
        true
        lexical-entry))

(defn intransitive-verb-rule [lexical-entry]
  (cond (and (= (get-in lexical-entry '(:synsem :cat))
                :verb)
             (= :none (get-in lexical-entry '(:synsem :sem :obj) :none))
             (not (= true (get-in lexical-entry '(:synsem :aux)))))
        (unifyc
         lexical-entry
         intransitive)
        true
        lexical-entry))

(defn transitive-verb-rule [lexical-entry]
  (cond (not (nil? (get-in lexical-entry '(:synsem :sem :obj))))
        (unifyc
         lexical-entry
         transitive-but-object-cat-not-set)
        true
        lexical-entry))

(defn ditransitive-verb-rule [lexical-entry]
  (cond (not (nil? (get-in lexical-entry '(:synsem :sem :iobj))))
        (unifyc
         lexical-entry
         (let [ref (ref :top)]
           {:synsem {:subcat {:3 {:sem ref}}
                     :sem {:iobj ref}}}))
        true
        lexical-entry))

;; This set of rules is monotonic and deterministic in the sense that
;; iterative application of the set of rules will result in the input
;; lexeme become more and more specific until it reaches a determinate
;; fixed point, no matter what order we apply the rules. Given enough
;; iterations, this same fixed point will be reached no matter which
;; order the rules are applied, as long as all rules are applied at
;; each iteration. This is guaranteed by using these rules below in
;; (transform) so that the rules' outputs are reduced using unifyc.
(def rules (list aux-verb-rule
                 category-to-subcat commonnoun
                 ditransitive-verb-rule
                 intensifier-agreement
                 intransitive-verb-rule
                 semantic-implicature
                 transitive-verb-rule
                 verb-rule))


;; Modifying rules: so-named because they modify the lexical entry in
;; such a way that is non-monotonic and dependent on the order of rule
;; application. Because of these complications, avoid and use
;; unifying-rules instead, where possible. Only to be used where
;; (reduce unifyc ..) would not work, as with embed-phon, where
;; {:italian <string>} needs to be turned into {:italian {:italian <string}},
;; but unifying the input and output of the rule would be :fail.
;; These rules are (reduce)d using merge rather than unifyc.
(def modifying-rules (list embed-phon))

(defn transform [lexical-entry]
  "keep transforming lexical entries until there's no changes. No changes is
   defined as: (isomorphic? input output) => true, where output is one iteration's
   applications of all of the rules."
  (log/debug (str "Transforming: " (fo lexical-entry)))
  (log/debug (str "transform: input :" lexical-entry))
  (let [result (reduce unifyc (map (fn [rule] (apply rule (list lexical-entry)))
                                   rules))
        result (reduce merge  (map (fn [rule] (apply rule (list result)))
                                   modifying-rules))]
    (if (isomorphic? result lexical-entry)
      (cache-serialization result)
      (transform result))))

(def lexicon
  ;; this filter is for debugging purposes to restrict lexicon to particular entries, if desired.
  ;; default shown is (not (nil? entry)) i.e. no restrictions except that an entry must be non-nil.
  ;;  (currently there is one nil below: "chiunque (anyone)").
  (filter (fn [entry]
            (or false
                (not (nil? entry))))

          ;; TODO: move this fn to lexiconfn: keep any code out of the lexicon proper.
          ;; this (map) adds, to each lexical entry, a copy of the serialized form of the entry.
          (map transform
               (concat
                a-noi
                notizie-potere
                qualche_volta-volere
                ))))

