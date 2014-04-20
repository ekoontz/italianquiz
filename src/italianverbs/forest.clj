(ns italianverbs.forest
  (:refer-clojure :exclude [get-in deref merge resolve find future parents rand-int])
  (:require
   [clojure.core :as core]
   ;; have to exclude partition-by because of some namespace clash, not sure how else to fix
   [clojure.core.async :as async :exclude [partition-by]]
   [clojure.set :refer :all]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [italianverbs.cache :refer (build-lex-sch-cache get-comp-phrases-of get-head-phrases-of get-lex
                                                   overc overh overh-with-cache overc-with-cache)]
   [italianverbs.lexicon :refer (it)]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.over :as over]
   [italianverbs.unify :as unify]
   [italianverbs.unify :refer (dissoc-paths get-in fail? lazy-shuffle remove-top-values-log show-spec unifyc)]))

(def concurrent false)

(def random-order true)
(defn rand-int [range & [constant]]
  (if random-order
    (core/rand-int range)
    (if constant constant 0)))

(declare lightning-bolt)

(defn add-comp-phrase-to-headed-phrase [parents phrases & [cache supplied-comp-spec]]
  (if (and (not (empty? parents))
           (first parents))
    (let [debug
          (do
            (log/debug (str "PARENTS: " (fo-ps parents)))
            (log/debug (str "TYPE PARENTS: " (type parents)))
            (log/debug (str "EMPTY? PARENTS: " (empty? parents)))
            (log/debug (str "starting add-comp-phrase-to-headed-phrase."))
            (log/debug (str "    with parent: " (fo-ps (first parents))))
            (log/trace (str "    with phrases: " (fo-ps phrases)))
            (log/trace (str "add-comp-phrase-to-headed-phrase: emptyness of parents: " (empty? parents))))

          debug (if false (throw (Exception. "GOT HERE: INSIDE MAIN PART OF add-comp-phrase-to-headed-phrase.")))
          debug (log/trace (str "add-comp-phrase-to-headed-phrase is non-empty."))
          parent (first parents)

          comp-spec
          (dissoc-paths
           (unifyc
            (get-in parent '(:comp))
            supplied-comp-spec)
           ;; TODO: do we need to dissoc these paths from the comp spec?
           '((:english :initial)
             (:italian :initial)))

          debug (do (log/debug (str "add-comp-phrase-to-headed-phrase: parent: " (fo-ps parent)))
                    (log/debug (str "   with comp-spec: " (show-spec comp-spec))))

          comp-phrases-for-parent (if (not (= false (get-in comp-spec '(:phrasal))))
                                    (filter (fn [phrase]
                                              (not (fail? phrase)))
                                            (map (fn [phrase]
                                                   (unifyc phrase comp-spec))
                                                 (get-comp-phrases-of parent cache)))
                                    (do
                                      (log/trace (str "the phrase '" (fo-ps parent) "' specifies a non-phrasal complement."))
                                      '()))

          comps 
          (lightning-bolt phrases cache comp-spec)]
      (lazy-cat
       (overc parent comps)
       (add-comp-phrase-to-headed-phrase (rest parents) phrases cache supplied-comp-spec)))))

(def can-log-if-in-sandbox-mode false)

(defn phrasal-headed-phrases [phrases grammar depth cache path]
  "return a lazy seq of phrases (maps) whose heads are themselves phrases."
  (if (not (empty? phrases))
    (let [debug (log/debug (str "phrasal-headed-phrases with phrases: " (fo-ps phrases)))
          parent (first phrases) ;; realizes possibly?
          debug (log/trace (str "phrasal-headed-phrases grammar size: " (.size grammar)))
          headed-phrases-of-parent (get-head-phrases-of parent cache)]
      (if (nil? headed-phrases-of-parent)
        (phrasal-headed-phrases (rest phrases) grammar depth cache path)
        (let [headed-phrases-of-parent (if (nil? headed-phrases-of-parent)
                                         (list)
                                         headed-phrases-of-parent)
              head-spec (dissoc-paths (get-in parent '(:head))
                                      '((:english :initial)
                                        (:italian :initial)))
              debug (log/trace (str "phrasal-headed-phrases: parent's head: " (show-spec head-spec)))]
          (lazy-cat
           (let [debug (log/debug (str "about to call lightning-bolt from phrasal-headed-phrase."))
                 debug (log/debug (str " with head-spec: " (show-spec head-spec)))
                 bolts (lightning-bolt grammar cache head-spec (+ 1 depth))]
             (overh phrases bolts))
           (phrasal-headed-phrases (rest phrases) grammar depth cache path)))))))

(defn parents-given-spec [head-spec phrases]
  "subset of phrases possible where the phrase's head is the given head."
  (if (nil? phrases)
    (log/trace (str "no parents for spec: " (show-spec head-spec))))
  (log/info (str "parents-given-spec: head-spec:" (show-spec head-spec)))
  (log/trace (str "parents-given-spec: phrases:" (fo-ps phrases)))
  (filter #(not (fail? %))
          (map (fn [each-phrase]
                 (unifyc each-phrase head-spec))
               ;; TODO: possibly: remove-paths such as (subcat) from head: would make it easier to call with lexemes:
               ;; e.g. "generate a sentence whose head is the word 'mangiare'" (i.e. user passes the lexical entry as
               ;; head param of (lightning-bolt)".
               phrases)))

(defn lazy-cats [lists & [ show-first ]]
  (if (not (empty? lists))
    (if (not (empty? (first lists)))
      (do
        (if show-first
          (log/info (str "lazy-cats: first: " (fo-ps (first (first lists))))))
        (lazy-cat (first lists)
                  (lazy-cats (rest lists))))
      (lazy-cats (rest lists)))))

(defn log-path [path log-fn & [ depth]]
  (let [depth (if depth depth 0)
        print-blank-line false]
    (if (> (.size path) 0)
      (let [h-or-c (:h-or-c (first path))
            depth (:depth (first path))
            grammar (:grammar (first path))
            lexicon-size (:lexicon-size (first path))
            spec (:spec (first path))
            phrases (fo-ps (:parents (first path)))]
        (log-fn (str "LB@[" depth "]: " h-or-c ":" spec))
        (log/trace (str "   grammar: " (fo-ps grammar) "; lexicon size: " lexicon-size))
        (log-fn (str "   applicable rules: " (fo-ps phrases)))
        (log-path (rest path) log-fn (+ depth 1)))
      (if print-blank-line (log-fn (str ""))))))

(def maxdepth 1)

(defn phrasal-spec [spec cache]
  "add additional constraints so that this spec can constrain heads and comps."
  (unifyc spec (if (:phrase-constraints cache)
                 (do
                   (log/trace (str "phrasal-spec: " (show-spec (:phrase-constraints cache))))
                   (:phrase-constraints cache))
                  :top)))

(defn hl [cache grammar & [spec depth]]
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head])]

    ;; try every possible lexeme as a candidate head for each phrase:
    ;; use (:comp (cache ..)) as the subset of the lexicon to try.
    (mapcat #(lazy-seq (overh % (filter (fn [lexeme]
                                          (not (fail? (unifyc lexeme head-spec))))
                                        (lazy-shuffle (:head (cache (:rule %)))))))
            (parents-given-spec spec grammar))))

(declare hlcl)

(defn hp [cache grammar & [spec depth]]
  (let [depth (if depth depth 0)
        debug (log/debug (str "PRE-PHRASAL-SPEC: " spec))
        spec (phrasal-spec (if spec spec :top) cache)
        debug (log/debug (str "POST-PHRASAL-SPEC: " spec))
        head-spec {:synsem (get-in spec [:head :synsem] :top)}]
    (log/info (str "HP with spec: " spec))
    (mapcat
     #(lazy-seq 
       (do
         (log/debug (str "SHOW SPEC SUBCAT: " (get-in spec [:synsem :subcat])))
         (log/debug (str "head-spec's 2nd arg pronoun is:" (get-in spec [:synsem :subcat :2])))
         (log/debug (str "hp head arg: " (fo-ps %)))
         (overh (parents-given-spec spec (lazy-shuffle grammar)) %)))
     (hlcl cache 
           (parents-given-spec spec (lazy-shuffle grammar))
           head-spec 
           (+ 1 depth)))))

(defn cp [cache grammar & [spec depth]]
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head])]
    (hlcl cache grammar
          (unifyc {:synsem (get-in spec [:comp :synsem])})
          (+ 1 depth))))

(defn hlcl [cache grammar & [spec depth]]
  "generate all the phrases where the head is a lexeme and the complement is a lexeme"
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head] :top)]
    (mapcat
     #(let [pred-of-arg (get-in % [:comp :synsem])]
        (log/trace (str "pred-of-arg: " pred-of-arg))
        (overc % (lazy-shuffle
                  (filter (fn [complement]
                            (not (fail? (unifyc (get-in complement [:synsem])
                                                pred-of-arg))))
                          (:comp (cache (:rule %)))))))
     (hl cache grammar spec))))

(defn hlcp [cache grammar & [spec depth]]
  "generate all the phrases where the head is a lexeme and the complement is a phrase."
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head])]
    (log/debug (str "hlcp with spec: " (show-spec spec)))
    (mapcat
     #(lazy-seq (overc % (hlcl cache grammar
                               {:synsem (get-in % [:comp :synsem] :top)}
                               (+ 1 depth))))
     (hl cache grammar spec))))

(defn hpcl [cache grammar & [spec depth]]
  "generate all the phrases where the head is a phrase and the complement is a lexeme."
  (log/debug (str "START HPCL.."))
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head])]
    (log/debug (str "hpcl with spec: " (show-spec spec)))
    (mapcat
     #(lazy-seq
       (do
         (log/info (str "HPCL : PERCENT IS: " (fo-ps %)))
         (let [result (overc % (lazy-shuffle (:comp (cache (:rule %)))))]
           result)))
     (hp cache grammar head-spec (+ 1 depth)))))

(defn hpcp [cache grammar & [spec depth]]
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head])]
    (log/debug (str "hpcp with spec: " (show-spec spec)))
    (mapcat

     #(lazy-seq

       (overc % ;; parent: a phrase from HEAD-PHRASE:
              ;; complement: a hlcl.
              (hlcl cache grammar {:synsem (get-in % [:comp :synsem] :top)} (+ 1 depth))))

     ;; HEAD-PHRASE:
     (mapcat

      #(lazy-seq (overh

                  ;; parent
                  (parents-given-spec spec (lazy-shuffle grammar))

                  ;; head: a hlcl.
                  %))

      (hlcl cache

            ;; grammar for this hlcl: the phrase's head must *not* be an intransitive verb.
            (parents-given-spec head-spec
                                   (lazy-shuffle grammar)
                                   (+ 1 depth))


            head-spec (+ 1 depth))))))

(defn hppcp [cache grammar & [spec depth]]
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head])]
    (log/debug (str "hpcp with spec: " (show-spec spec)))
    (mapcat

     #(lazy-seq

       (overc % ;; parent: a phrase from HEAD-PHRASE:
              ;; complement: a hlcl.
              (do
                (log/debug (str "generating a comp with: " (get-in % [:comp :synsem] :top) " given parent: "
                                (:rule %)))
                (hlcl cache grammar {:synsem (get-in % [:comp :synsem] :top)} (+ 1 depth)))))

     ;; HEAD-PHRASE:
     (mapcat
      (let [head-spec (unifyc head-spec {:head {:synsem {:subcat {:1 :top
                                                                  :2 '()}}}})]
        #(lazy-seq (overh

                    ;; parent
                    (parents-given-spec head-spec (lazy-shuffle grammar))

                    ;; head: a hpcl.
                    %))

        (log/debug (str "generating a head phrase with: " (get-in head-spec [:head] :top)))
;        (let [head-spec (unifyc head-spec {:head {:synsem {:subcat :top}}})]
        (let [head-spec (unifyc head-spec {:head :top})]
          (hpcl cache
                ;; grammar for this hlcl: the phrase's head must *not* be an intransitive verb.
                (parents-given-spec {:synsem (get-in head-spec [:head :synsem] :top)}
                                       (lazy-shuffle grammar))
                (get-in head-spec [:head]) (+ 1 depth))))))))

(defn hxcx [cache grammar & [spec depth]]
  (let [depth (if depth depth 0)
        spec (phrasal-spec (if spec spec :top) cache)
        head-spec (get-in spec [:head] :top)]
    (let [fns [hlcl]]
      (mapcat
       #(% cache grammar spec depth)
       (shuffle fns)))))

(defn lightning-bolt [grammar cache & [ spec depth]]
  "lightning-bolt lite"
  (let [depth (if depth depth 0)
        spec (if spec spec :top)
        parents (map #(unifyc % spec)
                     grammar)]
    (cond 
     (> depth maxdepth)
     nil

     true
     (let [parents-given-spec (parents-given-spec spec (lazy-shuffle grammar))

           lexical-headed-phrases
           (map #(overh % (lazy-shuffle (:head (cache (:rule %)))))
                parents-given-spec)

           debug (log/debug (str "PARENTS FOR THIS SPEC: " (fo-ps parents-given-spec)))

           phrasal-headed-phrases
           (if (not (= false (get-in spec [:head :phrasal])))
             (phrasal-headed-phrases parents-given-spec
                                     grammar depth cache nil))

           debug (log/debug (str "getting 1-level trees.."))

           hlcl
           (if (first lexical-headed-phrases)
             (mapcat #(overc % (lazy-shuffle (:comp (cache (:rule (first parents-given-spec))))))
                     lexical-headed-phrases))

           debug (log/debug (str "done getting 1-level trees - type: " (type hlcl)))

           hlcp
           (if (not (= false (get-in spec [:comp :phrasal])))
               (add-comp-phrase-to-headed-phrase lexical-headed-phrases
                                                 grammar cache
                                                 :top))

;           hpcp
;           (if (and (not (= false (get-in spec [:comp :phrasal])))
;                    (not (= false (get-in spec [:head :phrasal]))))
;             (mapcat #(add-comp-phrase-to-headed-phrase %
;                                                        grammar cache
;                                                        :top)
;                     phrasal-headed-phrases))


           debug (log/debug (str "FIRST PHRASAL-HEADED-PHRASES: "
                                 (fo-ps (first phrasal-headed-phrases))))

           hpcl
           (mapcat #(overc % (lazy-shuffle (:comp (cache (:rule %)))))
                   phrasal-headed-phrases)
           
           ]
;       hlcl))))
;       hlcl))))
       (flatten (lazy-shuffle (list hpcl hlcl)))))))
;       (lazy-shuffle (lazy-cats one-level-trees with-phrasal-complement hpcl))))))

;; aliases that might be easier to use in a repl:
(defn lb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

(defn lightningb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

