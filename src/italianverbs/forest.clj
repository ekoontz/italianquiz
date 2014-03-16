(ns italianverbs.forest
  (:refer-clojure :exclude [get-in deref merge resolve find future parents rand-int])
  (:require
   [clojure.core :as core]
   [clojure.set :refer :all]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [italianverbs.cache :refer (build-lex-sch-cache get-comp-phrases-of get-head-phrases-of get-lex
                                                   overc overh overh-with-cache overc-with-cache)]
   [italianverbs.lexicon :refer (it)]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.unify :as unify]
   [italianverbs.unify :refer (dissoc-paths get-in fail? lazy-shuffle remove-top-values-log show-spec unifyc)]))

(def concurrent false)
(defn deref [thing]
  (if concurrent
    (core/deref thing)
    thing))
(defn future [thing]
  (if concurrent
    (core/future thing)
    thing))

(def random-order true)
(defn rand-int [range constant]
  (if random-order
    (core/rand-int range)
    constant))

(declare lightning-bolt)

(defn add-comp-phrase-to-headed-phrase [parents phrases lexicon & [iter cache path supplied-comp-spec]]
  (if (not (empty? parents))
    (let [debug
          (do
            (log/debug (str "starting add-comp-phrase-to-headed-phrase."))
            (log/debug (str "    with parent: " (fo-ps (first parents))))
            (log/debug (str "    with phrases: " (fo-ps phrases)))
            (log/trace (str "    with lexicon size: " (.size lexicon)))
            (log/trace (str "add-comp-phrase-to-headed-phrase: emptyness of parents: " (empty? parents))))

          debug (if false (throw (Exception. "GOT HERE: INSIDE MAIN PART OF add-comp-phrase-to-headed-phrase.")))
          debug (log/trace (str "add-comp-phrase-to-headed-phrase is non-empty."))
          iter (if (nil? iter) 0 iter)
          parent (first parents)

          cache (if cache cache
                    (do
                      (log/info (str "building cache (" (.size phrases) ")"))
                      (build-lex-sch-cache phrases lexicon)))

          debug (log/debug (str "supplied comp-spec: " supplied-comp-spec))

          debug (log/debug (str "parent's comp-spec: " (show-spec (get-in parent '(:comp)))))

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
          (if (not (empty? comp-phrases-for-parent))
            (let [lexicon-for-comp (get-lex parent :comp cache lexicon)]
              (log/debug (str "about to call lightning-bolt from add-comp-phrase-to-headed-phrase."))
              (log/debug (str "  with head-spec: " (show-spec comp-spec)))
              (log/debug (str "  with grammar: " (fo-ps comp-phrases-for-parent)))
              (log/trace (str "  with lexicon size: " (.size lexicon-for-comp)))
              (deref
              (future
                (lightning-bolt
                 comp-spec lexicon-for-comp
                 comp-phrases-for-parent
                 0
                 cache (conj path 
                             {:h-or-c "C"
                              :depth 0
                              :grammar comp-phrases-for-parent
                              :lexicon-size (.size lexicon-for-comp)
                              :spec (show-spec comp-spec)
                              :parents comp-phrases-for-parent})))))
            (list))]

      (lazy-cat
       (overc parent comps)
       (add-comp-phrase-to-headed-phrase (rest parents) phrases lexicon (+ 1 iter) cache path supplied-comp-spec)))))

(def can-log-if-in-sandbox-mode false)

(defn lexical-headed-phrases [parents lexicon phrases depth cache path]
  "return a lazy seq of phrases (maps) whose heads are lexemes."
  (if (not (empty? parents))
    (let [parent (first parents)
          cache (if cache cache
                    (do (log/warn (str "lexical-headed-parents given null cache: building cache from: (" (.size phrases) ")"))
                        (build-lex-sch-cache phrases 
                                             (map (fn [lexeme]
                                                    (unifyc lexeme
                                                            {:phrasal false}))
                                                  lexicon))))]
      (log/trace (str "lexical-headed-phrases: looking at parent: " (fo-ps parent)))
      
      (lazy-seq
       (let [result (overh-with-cache parent cache lexicon)]
         (cons {:parent parent
                :headed-phrases result}
               (lexical-headed-phrases (rest parents) lexicon phrases depth cache path)))))))

(defn phrasal-headed-phrases [parents lexicon grammar depth cache path]
  "return a lazy seq of phrases (maps) whose heads are themselves phrases."
  (if (not (empty? parents))
    (let [parent (first parents) ;; realizes possibly?
          debug (log/trace (str "phrasal-headed-phrases grammar size: " (.size grammar)))
          headed-phrases-of-parent (get-head-phrases-of parent cache)]
      (if (nil? headed-phrases-of-parent)
        (phrasal-headed-phrases (rest parents) lexicon grammar depth cache path)        
        (let [headed-phrases-of-parent (if (nil? headed-phrases-of-parent)
                                         (list)
                                         headed-phrases-of-parent)
              head-spec (dissoc-paths (get-in parent '(:head))
                                      '((:english :initial)
                                        (:italian :initial)))
              debug (log/trace (str "phrasal-headed-phrases: parent's head: " (show-spec head-spec)))]
          (lazy-seq
           (let [debug (log/debug (str "about to call lightning-bolt from phrasal-headed-phrase."))
                 debug (log/debug (str "  head-spec: " (show-spec head-spec)))
                 debug (log/trace (str "  with grammar: " (fo-ps parents)))
                 debug (log/trace (str "  with lexicon size: " (.size lexicon)))
                 bolts 
                 (deref (future
                   (lightning-bolt head-spec
                                   lexicon headed-phrases-of-parent (+ 1 depth)
                                   cache
                                   path)))]
             (cons {:parent parent
                    :headed-phrases (overh parents bolts)}
                   (phrasal-headed-phrases (rest parents) lexicon grammar depth cache path)))))))))

(defn parents-at-this-depth [head-spec phrases depth]
  "subset of phrases possible at this depth where the phrase's head is the given head."
  (if (nil? phrases)
    (log/trace (str "no parents for spec: " (show-spec head-spec) " at depth: " depth)))
  (log/trace (str "parents-at-this-depth: head-spec:" (show-spec head-spec)))
  (log/trace (str "parents-at-this-depth: phrases:" (fo-ps phrases)))
  (filter (fn [each-unified-parent]
            (not (fail? each-unified-parent)))
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

(defn headed-phrases [parents-with-lexical-heads parents-with-phrasal-heads]
  (let [parents-with-lexical-heads (filter (fn [parent]
                                             (do (log/trace "checking parent (1)")
                                                 (not (= false (get-in parent '(:comp :phrasal))))))
                                           parents-with-lexical-heads)
        parents-with-phrasal-heads (filter (fn [parent]
                                             (do (log/trace "checking parent (2)")
                                                 (not (= false (get-in parent '(:comp :phrasal))))))
                                           parents-with-phrasal-heads)
        cats
        (lazy-cat
         parents-with-lexical-heads parents-with-phrasal-heads)]
    (do
      (if (not (empty? cats))
        (log/debug (str "first headed-phrases: " (fo-ps (first cats))))
        (log/debug (str " no headed-phrases.")))
      cats)))

(defn log-path [path log-fn & [ depth]]
  (let [depth (if depth depth 0)
        print-blank-line false]
    (if (> (.size path) 0)
      (let [h-or-c (:h-or-c (first path))
            depth (:depth (first path))
            grammar (:grammar (first path))
            lexicon-size (:lexicon-size (first path))
            spec (:spec (first path))
            parents (fo-ps (:parents (first path)))]
        (log-fn (str "LB@[" depth "]: " h-or-c ":" spec))
        (log/trace (str "   grammar: " (fo-ps grammar) "; lexicon size: " lexicon-size))
        (log-fn (str "   applicable rules: " (fo-ps parents)))
        (log-path (rest path) log-fn (+ depth 1)))
      (if print-blank-line (log-fn (str ""))))))

(def maxdepth 4)

;; TODO: s/head/head-spec/
(defn lightning-bolt [ & [head-spec lexicon grammar depth cache path]]
  (let [depth (if depth depth 0)
        parents-at-this-depth (parents-at-this-depth head-spec
                                                     (lazy-shuffle grammar) depth)]
  (cond (nil? lexicon)
        (do
          (log/warn "lightning-bolt: lexicon was nil.")
          nil)

        (nil? grammar)
        (do
          (log/warn "lightning-bolt: grammar was nil.")
          nil)
        
        (> depth maxdepth)
        nil

        (= (.size parents-at-this-depth) 0)
        nil

        true
        (do

          (let [
                head head-spec
                path (if path path [])
                path (if path (conj path
                                    ;; add one element representing this call of lightning-bolt.
                                    {:depth (+ 1 depth)
                                     :grammar grammar
                                     :h-or-c "H"
                                     :lexicon-size (.size lexicon)
                                     :spec (show-spec head)
                                     :parents parents-at-this-depth}))]
            (log-path path (fn [x] (log/trace x)))

            (let [head (if head head :top)
                  ;; TODO: will probably remove this or make it only turned on in special cases.
                  ;; lightning-bolt should be efficient enough to handle :top as a spec
                  ;; efficiently.
                  too-general (if (= head-spec :top)
                                (if true nil
                                    (throw (Exception. (str ": head-spec is too general: " head-spec)))))

                  phrases grammar;; TODO: rename all uses of phrases to grammar.
                
                  depth (if depth depth 0)
                            
                  ]
              (cond
               (empty? parents-at-this-depth)
               (do (log/trace "lb: no parents at depth:" depth ";returning empty list.")
                   nil)

               true
               (let [hs (unify/strip-refs head-spec)

                     debug (log/debug (str "startLB 0@" depth ":" hs "; grammar: " (fo-ps grammar)))

                     cache (if cache cache (build-lex-sch-cache phrases lexicon phrases))

                     phrasal-headed-phrases (phrasal-headed-phrases parents-at-this-depth (lazy-shuffle lexicon)
                                                                    phrases depth cache path)


                     parents-with-phrasal-heads-for-phasal-comps
                     (mapcat (fn [each-kv]
                               (let [parent (:parent each-kv)]
                                 (if (not (= false (get-in parent '(:comp :phrasal))))
                                   (let [phrases (:headed-phrases each-kv)]
                                     phrases))))
                             phrasal-headed-phrases)


                     phrasal-headed-phrases
                     (mapcat (fn [each-kv]
                               (let [phrases (:headed-phrases each-kv)]
                                 phrases))
                             phrasal-headed-phrases)

                     debug (log/trace "started looking for lexical-headed-phrases given grammar: " 
                                      (string/join ";" (map #(:rule %)
                                           grammar)))


                     debug (log/debug (str "startLB 1@" depth ":" hs))

                     lexical-headed-phrases (lexical-headed-phrases parents-at-this-depth 
                                                                    (lazy-shuffle lexicon)
                                                                    grammar depth cache path)

                     lexical-headed-phrases
                     (mapcat (fn [each-kv]
                               (let [phrases (:headed-phrases each-kv)]
                                 phrases))
                             lexical-headed-phrases)
                     debug (log/trace "done looking for lexical-headed-phrases; found: " (.size lexical-headed-phrases))

                     ;; trees where both the head and comp are lexemes.
                     one-level-trees
                     (overc-with-cache lexical-headed-phrases cache (lazy-shuffle lexicon))

                     debug (log/debug (str "startLB 2@" depth ":" hs))

                     debug
                     (if (not (empty? parents-with-phrasal-heads-for-phasal-comps))
                       (log/debug (str "looking for headed-phrases with first parents-with-phrasal-heads-for-phasal-comps being: " (fo-ps (first parents-with-phrasal-heads-for-phasal-comps))))
                       (log/debug (str "empty phrasal comps....!!")))

                     debug
                     (if (not (empty? lexical-headed-phrases))
                       (log/debug (str "looking for headed-phrases with lexical-headed-phrases:" (fo-ps (first lexical-headed-phrases))))
                       (log/debug (str "empty lexical phrases...!!")))

                     headed-phrases
                     (headed-phrases
                      parents-with-phrasal-heads-for-phasal-comps
                      lexical-headed-phrases)

                     debug
                     (if (empty? headed-phrases)
                       (log/debug (str "headed-phrases is all empty for grammar: " 
                                       (string/join ";" (map #(:rule %)
                                                             parents-at-this-depth)))))

                     debug 
                     (if (get-in head-spec '(:comp))
                       (log/info (str "lb: head spec's comp-spec: " (get-in head-spec '(:comp)) " to add-comp-phrase-to-headed-phrase.")))

                     hpcl (overc-with-cache phrasal-headed-phrases cache lexicon)

                     debug (if (not (empty? one-level-trees)) 
                             (log/debug (str "first one-level-tree: " (fo-ps (first one-level-trees)) " for grammar: " 
                                            (string/join ";" (map #(:rule %)
                                                           parents-at-this-depth))))
                             (log/debug (str "no one-level trees for grammar: " 
                                            (string/join ";" (map #(:rule %)
                                                           parents-at-this-depth)))))


                     with-phrasal-complement
                     (add-comp-phrase-to-headed-phrase headed-phrases
                                                       phrases lexicon (+ 1 depth) cache path
                                                       (if (not (= :notfound (get-in head-spec '(:comp) :notfound)))
                                                         (get-in head-spec '(:comp))
                                                         :top))

                     debug (if (not (nil? with-phrasal-complement))
                             (log/debug (str "realized? with-phrasal-complement (initial):"
                                            (realized? with-phrasal-complement))))



                     debug (if (not (nil? with-phrasal-complement))
                             (log/debug (str "realized? with-phrasal-complement (1):"
                                            (realized? with-phrasal-complement))))

                     debug (log/debug (str "type of with-phrasal-complement:"
                                           (type with-phrasal-complement)))

                     debug (log/debug (str "type of hpcl: "
                                           (type hpcl)))

                     debug (if (not (nil? with-phrasal-complement))
                             (log/debug (str "realized? with-phrasal-complement (final):"
                                            (realized? with-phrasal-complement))))

                     debug (if (not (nil? hpcl))
                             (log/debug (str "realized? hpcl:"
                                            (realized? hpcl))))

                     ]

                 (lazy-cats (shuffle (list one-level-trees with-phrasal-complement hpcl))
                            (and false (not (fail? (unifyc head-spec
                                                           {:synsem {:subcat '()}})))))))))))))

;; aliases that might be easier to use in a repl:
(defn lb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

(defn lightningb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

