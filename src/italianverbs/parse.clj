(ns italianverbs.parse)

(require '[clojure.string :as str])
(require '[italianverbs.generate :as gen])

(require '[italianverbs.grammar.english :as en-g])
(require '[italianverbs.grammar.italiano :as it-g])

(require '[italianverbs.lexicon.english :as en-l])
(require '[italianverbs.lexicon.italiano :as it-l])
(require '[italianverbs.lexiconfn :as lexfn])

(require '[italianverbs.morphology :refer (fo fo-ps)])
(require '[italianverbs.over :as over])

(declare lookup)

(def en-grammar en-g/grammar)
(def it-grammar it-g/grammar)

(def en-lexicon en-l/lexicon)
(def it-lexicon it-l/lexicon)

(def en-phonize en-l/phonize)
(def it-phonize it-l/phonize)
         
(defn parse [input-string & [ lexicon grammar ] ]
  "return a list of all possible parse trees for the given list of tokens, given the lexicon and grammar."
  (if (or (nil? lexicon)
          (nil? grammar))
    {:english (parse input-string en-lexicon en-grammar)
     :italiano (parse input-string it-lexicon it-grammar)}
    ;; else, grammar and lexicon supplied:
    (let [tokens (str/split input-string #"[ ']")
          looked-up (map #(lookup lexicon %)
                         tokens)]
      (cond (and (not (empty? (nth looked-up 0)))
                 (not (empty? (nth looked-up 1))))
            (over/over grammar
                       (nth looked-up 0)
                       (nth looked-up 1))

            (not (empty? (nth looked-up 0)))
            (over/over grammar
                       (nth looked-up 0))

            (not (empty? (nth looked-up 1)))
            (over/over grammar
                       :top
                       (nth looked-up 1))
            
            true
            nil))))

(defn lookup [lexicon token]
  "return the subset set of lexemes that match this token from the lexicon."
  (let [phonize-fn
        (cond (= lexicon en-lexicon)
              en-phonize
              true
              it-phonize)]
    (phonize-fn (get lexicon token) token)))


