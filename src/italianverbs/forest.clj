(ns italianverbs.forest
  (:refer-clojure :exclude [get-in deref merge resolve find future parents rand-int])
  (:require
   [clojure.core :as core]
   [clojure.set :refer :all]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [italianverbs.cache :refer (build-lex-sch-cache get-comp-phrases-of get-head-phrases-of get-lex
                                                   overc overh overc-with-cache overh-with-cache)]
   [italianverbs.lexicon :refer (it)]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.unify :as unify]
   [italianverbs.unify :refer (dissoc-paths get-in fail? lazy-shuffle remove-top-values-log show-spec)]))

(def concurrent true)
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

(defn unifyc [& args]
  (if (first args) 
    (log/trace (str "forest-unify 1 " (fo-ps (first args)))))
  (if (second args) 
    (log/trace (str "forest-unify 2 " (fo-ps (second args)))))
  (apply unify/unifyc args))

(declare lightning-bolt)

(defn headed-phrase-add-comp [parents phrases lexicon & [iter cache path]]
  (if (not (empty? parents))
    (let [iter (if (nil? iter) 0 iter)
          parent (first parents)
          cache (if cache cache
                    (do
                      (log/info (str "building cache (" (.size phrases) ")"))
                      (build-lex-sch-cache phrases lexicon)))
          comp-spec
          (dissoc-paths
           (get-in parent '(:comp))
           ;; TODO: do we need to dissoc these paths from the comp spec?
           '((:english :initial)
             (:italian :initial)))

          debug (do (log/debug (str "hpac: parent: " (fo-ps parent)))
                    (log/debug (str "hpac: comp-spec: " (show-spec comp-spec))))


          comp-phrases-for-parent (filter (fn [phrase]
                                            (not (fail? phrase)))
                                          (map (fn [phrase]
                                                 (unifyc phrase comp-spec))
                                               (get-comp-phrases-of parent cache)))
          comp-phrases-for-parent (if (nil? comp-phrases-for-parent) (list)
                                      comp-phrases-for-parent)

          comps 
          (deref (future
            (lightning-bolt
             comp-spec (get-lex parent :comp cache lexicon)
             comp-phrases-for-parent
             0
             cache (conj path 
                         {:h-or-c "C"
                          :depth 0
                          :spec (show-spec comp-spec)
                          :parents comp-phrases-for-parent}))))]

      (if (not (empty? comps))
        (do
          (log/debug (str "headed-phrase-add-comp: first comp is: " (fo-ps (first comps)) " which we will add to parent: " (fo-ps parent)))
          (lazy-cat
           (overc parent comps)
           (headed-phrase-add-comp (rest parents) phrases lexicon (+ 1 iter) cache path)))
        (headed-phrase-add-comp (rest parents) phrases lexicon (+ 1 iter) cache path)))))

(def can-log-if-in-sandbox-mode false)

(defn lexical-headed-phrases [parents lexicon phrases depth cache path]
  "return a lazy seq of phrases (maps) whose heads are lexemes."
  (if (not (empty? parents))
    (let [parent (first parents)
          cache (if cache cache
                    (do (log/warn (str "lexical-headed-parents given null cache: building cache from: (" (.size phrases) ")"))
                        (build-lex-sch-cache phrases lexicon)))]
      (log/debug (str "lexical-headed-phrases: looking at parent: " (fo-ps parent)))
      (lazy-seq
       (let [result (overh parent (get-lex parent :head cache lexicon))]
         (cons {:parent parent
                :headed-phrases result}
               (lexical-headed-phrases (rest parents) lexicon phrases depth cache path)))))))

(defn phrasal-headed-phrases [parents lexicon grammar depth cache path]
  "return a lazy seq of phrases (maps) whose heads are themselves phrases."
  (if (not (empty? parents))
    (let [parent (first parents) ;; realizes possibly?
          debug (log/debug (str "phrasal-headed-phrases grammar size: " (.size grammar)))
          headed-phrases-of-parent (get-head-phrases-of parent cache)
          headed-phrases-of-parent (if (nil? headed-phrases-of-parent)
                                     (list)
                                     headed-phrases-of-parent)
          head-spec (dissoc-paths (get-in parent '(:head))
                                  '((:english :initial)
                                    (:italian :initial)))
          debug (log/debug (str "phrasal-headed-phrases: parent's head: " (show-spec head-spec)))

          ]
      (conj
       {parent (let [bolts 
                     (deref (future
                              (lightning-bolt head-spec
                                              lexicon headed-phrases-of-parent (+ 1 depth)
                                              cache
                                              path)))]
                 (overh parents bolts))}
       (phrasal-headed-phrases (rest parents) lexicon grammar depth cache path)))
    {}))

;; TODO: move this to inside lightning-bolt.
(defn decode-gen-ordering2 [rand2]
  (cond (= rand2 0)
        "hLcP + hPcP"
        true
        "hPcP + hLcP"))

;; TODO: move this to inside lightning-bolt.
(defn decode-generation-ordering [rand1 rand2]
  (cond (= rand1 0)
        (str "hLcL + " (decode-gen-ordering2 rand2) " + hPcL")
        (= rand 1)
        (str (decode-gen-ordering2 rand2) " + hLcL + hPcL")
        (= rand 2)
        (str (decode-gen-ordering2 rand2) " + hPcL + hLcL")
        true
        (str "hPcL + "  (decode-gen-ordering2 rand2) " + hLcL")))

(defn parents-at-this-depth [head-spec phrases depth]
  "subset of phrases possible at this depth where the phrase's head is the given head."
  (if (nil? phrases)
    (log/debug (str "no parents for spec: " (show-spec head-spec) " at depth: " depth)))
  (log/debug (str "parents-at-this-depth: head-spec:" (show-spec head-spec)))
  (log/debug (str "parents-at-this-depth: phrases:" (fo-ps phrases)))
  (filter (fn [each-unified-parent]
            (not (fail? each-unified-parent)))
          (map (fn [each-phrase]
                 (unifyc each-phrase head-spec))
          ;; TODO: possibly: remove-paths such as (subcat) from head: would make it easier to call with lexemes:
          ;; e.g. "generate a sentence whose head is the word 'mangiare'" (i.e. user passes the lexical entry as
          ;; head param of (lightning-bolt)".
               phrases)))

(defn parents-with-phrasal-complements [parents-with-lexical-heads parents-with-phrasal-heads
                                        rand-parent-type-order]
  (let [parents-with-lexical-heads (filter (fn [parent]
                                             (not (= false (get-in parent '(:comp :phrasal)))))
                                           parents-with-lexical-heads)
        parents-with-phrasal-heads (filter (fn [parent]
                                             (not (= false (get-in parent '(:comp :phrasal)))))
                                           parents-with-phrasal-heads)]
    (cond (= rand-parent-type-order 0)
          (lazy-cat parents-with-lexical-heads parents-with-phrasal-heads)
          true
          (lazy-cat parents-with-phrasal-heads parents-with-lexical-heads))))

(defn log-path [path log-fn & [ depth]]
  (let [depth (if depth depth 0)
        print-blank-line false]
    (if (> (.size path) 0)
      (let [h-or-c (:h-or-c (first path))
            depth (:depth (first path))
            spec (:spec (first path))
            parents (fo-ps (:parents (first path)))]
        (log-fn (str "LB@[" depth "]: " h-or-c "; spec=" spec))
        (log-fn (str "   parents: " parents))
        (log-path (rest path) log-fn (+ depth 1)))
      (if print-blank-line (log-fn (str ""))))))

(def maxdepth 3)

;; TODO: s/head/head-spec/
(defn lightning-bolt [ & [head lexicon grammar depth cache path]]
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

        (and (not (nil? lexicon))
             (not (nil? grammar)))
        (do
          (log/debug (str "lightning-bolt with lexicon size: " 
                          (.size lexicon) " and grammar size: "
                          (.size grammar) "."))
          (let [head (if head head :top)
                ;; TODO: will probably remove this or make it only turned on in special cases.
                ;; lightning-bolt should be efficient enough to handle :top as a spec
                ;; efficiently.
                too-general (if (= head :top)
                              (if true nil
                                  (throw (Exception. (str ": head-spec is too general: " head)))))

                phrases grammar;; TODO: rename all uses of phrases to grammar.
                
                remove-top-values (remove-top-values-log head)
                
                depth (if depth depth 0)
                
                rand-order (rand-int 3 0)
                
                rand-parent-type-order (rand-int 2 0)
                
                log (log/debug (str "rand-order at depth:" depth " is: "
                                    (decode-generation-ordering rand-order rand-parent-type-order)
                                    "(rand-order=" rand-order ";rand-parent-type-order=" rand-parent-type-order ")"))
            
                parents-at-this-depth (parents-at-this-depth head grammar depth)]
            (cond
               (empty? parents-at-this-depth)
               (do (log/debug "lb: no parents at depth:" depth ";returning empty list.")
                   nil)

               true
               (let [cache (if cache cache (build-lex-sch-cache phrases lexicon phrases))
                     debug (log/debug (str "about to call lexical-headed-phrases: grammar= " (fo-ps grammar) "; "
                                           "parents-at-this-depth= " (fo-ps parents-at-this-depth) "; lexicon of size: " (.size lexicon)))
                     
                     lexical-headed-phrases (lexical-headed-phrases parents-at-this-depth
                                                                    (lazy-shuffle lexicon)
                                                                    phrases depth
                                                                    cache path)
               
                     path (if path path [])
                     path (if path (conj path
                                         ;; add one element representing this call of lightning-bolt.
                                         {:h-or-c "H"
                                          :depth depth
                                          :spec remove-top-values
                                          :parents parents-at-this-depth}))
                     log (log-path path (fn [x] (log/info x)))
                     
                     parents-with-phrasal-head-map (phrasal-headed-phrases parents-at-this-depth lexicon
                                                                           phrases depth
                                                                           cache path)
               
                     parents-with-phrasal-head (vals parents-with-phrasal-head-map)
                     
                     parents-with-lexical-heads 
                     (mapcat (fn [each-kv]
                               (let [phrases (:headed-phrases each-kv)]
                                 phrases))
                             lexical-headed-phrases)
                     
                     ;; TODO: (lazy-shuffle) this.
                     ;; TODO: cache this.
                     parents-with-phrasal-heads-for-comp-phrases 
                     parents-with-phrasal-head
               
                     parents-with-lexical-heads-for-comp-phrases 
                     (fn [] (mapcat (fn [each-kv]
                                      (let [parent (:parent each-kv)]
                                        (if (not (= false (get-in parent '(:comp :phrasal))))
                                          (let [phrases (:headed-phrases each-kv)]
                                            phrases))))
                                    lexical-headed-phrases))
               
                     one-level-trees
                     (fn []
                       (let [one-level-trees
                             (if (not (empty? parents-with-lexical-heads))
                               (overc-with-cache parents-with-lexical-heads cache (lazy-shuffle lexicon)))]
                         one-level-trees))

                     with-phrasal-comps 
                     (fn []
                       (let [with-phrasal-comps
                             (headed-phrase-add-comp (parents-with-phrasal-complements
                                                      parents-with-phrasal-heads-for-comp-phrases
                                                      (parents-with-lexical-heads-for-comp-phrases)
                                                      rand-parent-type-order)
                                                     phrases (lazy-shuffle lexicon) 0 cache path)]
                         (if (empty? with-phrasal-comps)
                           (log/debug (str "cP is empty."))
                           (log/debug (str "cP is not empty; first is: " (fo-ps (first with-phrasal-comps)))))
                         with-phrasal-comps))
                     ]

           (cond (= rand-order 0) ;; hLcL + rand2 + hPcL
                 (lazy-cat
                  (one-level-trees)
                  (with-phrasal-comps)
                  (overc-with-cache parents-with-phrasal-head cache lexicon))


                 (= rand-order 1) ;; rand2 + hLcL + hPcL
                 (lazy-cat
                  (with-phrasal-comps)
                  (one-level-trees)
                  (overc-with-cache parents-with-phrasal-head cache lexicon))

                 (= rand-order 2) ;; hPcL + rand2 + hLcL
                 (lazy-cat
                  (overc-with-cache parents-with-phrasal-head cache lexicon)
                  (with-phrasal-comps)
                  (one-level-trees)))))))))


;; aliases that might be easier to use in a repl:
(defn lb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

(defn lightningb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

