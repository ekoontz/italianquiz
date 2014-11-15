(ns italianverbs.parse
 (:refer-clojure :exclude [get-in merge resolve find]))

(require '[clojure.string :as str])
(require '[italianverbs.grammar.italiano :as it-g])
(require '[italianverbs.lexicon.italiano :as it-l])
(require '[italianverbs.morphology :refer (fo fo-ps)])
(require '[italianverbs.morphology.italiano :refer (analyze get-string)])
(require '[italianverbs.over :as over])
(require '[italianverbs.unify :refer (get-in)])

(def it-grammar it-g/grammar)
(def it-lexicon it-l/lexicon)

(defn lookup [token & [lexicon]]
  "return the subset of lexemes that match this token from the lexicon."
  (let [lexicon (if lexicon lexicon it-lexicon)]
    (analyze token (fn [k]
                     (get lexicon k)))))

(defn toks [s]
  (map #(lookup %)
       (str/split s #"[ ']")))

(declare parse)

(defn parse [arg]
  "return a list of all possible parse trees for a string or a list of lists of maps (a result of looking up in a dictionary a list of tokens from the input string)"
  (cond (string? arg)
        (parse (toks arg))
        
        (and (seq? arg)
             (empty? (rest arg)))
        (first arg)

        (and (seq? arg)
             (= (.size arg) 2))
        (over/over it-grammar
                   (first arg)
                   (second arg))
        
        (seq? arg)
        ;; TODO: figure out how to do concurrency and memoization.
        (concat ;; consider using lazy-cat here
         (over/over it-grammar
                    (subvec (vec arg) 0 (/ (.size arg) 2))
                    (subvec (vec arg) (/ (.size arg) 2) (.size arg)))
         (over/over it-grammar
                    (first arg)
                    (parse (rest arg)))
         (over/over it-grammar
                    (parse (butlast arg))
                    (last arg)))

        true
        :error))
