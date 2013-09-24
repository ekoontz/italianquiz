(ns italianverbs.grammar
  (:refer-clojure :exclude [get-in resolve])
  (:use [clojure.set :only (union intersection)]
        [clojure.core :exclude (get-in resolve merge)]
        [italianverbs.generate :only (generate moreover-head moreover-comp gen14)]
        [italianverbs.lexicon :only (it1)]
        [italianverbs.lexiconfn :only (unify sem-impl)]
        [italianverbs.morphology :only (finalize fo italian-article get-italian-1 get-italian)]
        [italianverbs.ug]
        [italianverbs.unify :only (copy fail? serialize get-in resolve)]
        )

  (:require [clojure.tools.logging :as log]
            [italianverbs.lexicon :as lex]
            [italianverbs.unify :as unify]
            [clojure.string :as string])
)

(def tinylex
  (union ;(it1 "aiutare")
         (it1 "andare")
         (it1 "dormire")
         (it1 "la")
         (it1 "il")
         (it1 "io")
         (it1 "ragazzo")
         (it1 "ragazza")
         (it1 "un")
         (it1 "vedere")
        ))

(log/info "begin italian-english specifics.")

 ;; note that order of arguments to mycc10 is reverse of s-to-np-vp, because
;; (gen-cc10) and other generic functions always have their arguments head, then comp.

(defn s-to-np-vp-inner [np vps]
  (if (first vps)
    (lazy-cat (gen-cc10 (first vps) np)
              (s-to-np-vp-inner np (rest vps)))))

(defn s-to-np-vp [nps vps]
  (if (first nps)
    (lazy-cat (s-to-np-vp-inner (first nps) vps)
              (s-to-np-vp (rest nps) vps))))

(defn vp-to-v-np [v nps]
  (if (first nps)
    (lazy-cat (gen-hh21 v (first nps))
              (vp-to-v-np v (rest nps)))))

(defn vp-to-pronoun-v [pronouns v]
  (gen-ch21 v pronouns))

(def np-to-det-n
  (fn [filter]
    (do
      (log/info "looking for nouns..")
      (lazy-seq (base-cc10-random (merge filter))))))

(def propernouns-and-pronouns
  ;; TODO: more compile-time filtering
  (filter (fn [lexeme]
            (and (= (unify/get-in lexeme '(:synsem :cat)) :noun)
                 (= (unify/get-in lexeme '(:synsem :subcat)) '())))
          cc10-comps))

(def intransitive-verbs
  (filter (fn [candidate]
            (and (= :notfound (unify/get-in candidate '(:synsem :subcat :2 :cat) :notfound))
                 (= (unify/get-in candidate '(:synsem :cat)) :verb)))
          lex/lexicon))

(def transitive-verbs
  (filter (fn [candidate]
            ;; filter Vs to reduce number of candidates we need to filter:
            ;; (only transitive verbs)
            (and (not (= :notfound (unify/get-in candidate '(:synsem :subcat :2 :cat) :notfound)))
                 (= (unify/get-in candidate '(:synsem :cat)) :verb)))
          (lazy-shuffle hh21-heads)))

(def pronouns
  ;; TODO: more compile-time filtering
  (filter (fn [lexeme]
            (and (= (get-in lexeme '(:synsem :cat)) :noun)
                 (= (get-in lexeme '(:synsem :subcat)) '())))
          lex/lexicon))

(def intransitive-verbs
  (filter (fn [lexeme]
            (and (= (get-in lexeme '(:synsem :cat)) :verb)
                 (= (get-in lexeme '(:synsem :subcat :2)) '())))
          lex/lexicon))

(defn np-expansions []
  (lazy-shuffle (list np-to-det-n
                      (lazy-shuffle propernouns-and-pronouns))))

(defn sentences []
  (lazy-seq
   ;; parent: S -> NP VP
   (s-to-np-vp

    (np-expansions) ;; Subject NP.

    ;; VP.
    (shuffle
     (list

      ;; 1. VP -> V
      (fn [] (lazy-shuffle intransitive-verbs))

      ;; 2. VP -> V NP
      (fn []
        (vp-to-v-np
         (lazy-shuffle transitive-verbs)
         (np-expansions))) ;; Object NP

      ;; 3. VP -> Pronoun V
      (fn []
        (vp-to-pronoun-v
         ;; Object Pronoun
         (lazy-shuffle pronouns)

         (filter (fn [candidate]
                   ;; filter Vs to reduce number of candidates we need to filter:
                   ;; (only transitive verbs)
                   (and (not (= :notfound (unify/get-in candidate '(:synsem :subcat :2 :cat) :notfound)))
                        (= (unify/get-in candidate '(:synsem :cat)) :verb)))
                 (lazy-shuffle hh21-heads)))))))))

;; TODO: move to somewhere else that uses both grammar and lexicon (e.g. quiz or workbook): grammar itself should not depend on lexicon (lex/lexicon).
(defn random-sentence []
  (let [result
        (first (take 1 (sentences)))]
    (log/info "FO SAYS: " (fo result))
    result))

(defn random-sentences [n]
  (repeatedly n (fn [] (random-sentence))))

(defn speed-test [ & times]
  "TODO: show benchmark results and statistics (min,max,95%tile,stddev,etc)"
  (take 3 (repeatedly #(time (fo (random-sentence))))))

