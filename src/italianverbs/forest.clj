(ns italianverbs.forest
  (:refer-clojure :exclude [get-in deref merge resolve find future parents rand-int])
  (:require
   [clojure.core :as core]
   [clojure.set :refer :all]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [italianverbs.cache :refer (initialize-cache get-comp-phrases-of 
                               get-head-phrases-of get-lex
                               overc overh overc-complement-is-lexeme)]
   [italianverbs.lexicon :refer (it)]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.unify :as unify]
   [italianverbs.unify :refer (dissoc-paths fail?
                               get-in lazy-shuffle 
                               remove-top-values-log show-spec)]))

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

(defn headed-phrase-add-comp [parents grammar lexicon & [iter path]]
  (if (not (empty? parents))
    (let [iter (if (nil? iter) 0 iter)
          parent (first parents)
          comp-spec
          (dissoc-paths
           (get-in parent '(:comp))
           ;; TODO: do we need to dissoc these paths from the comp spec?
           '((:english :initial)
             (:italian :initial)))

          comp-phrases-for-parent (get-comp-phrases-of parent)
          comp-phrases-for-parent (if (nil? comp-phrases-for-parent) (list)
                                      comp-phrases-for-parent)

;;          debug (log/trace (str "SIZE OF COMP-PHRASES-FOR-PARENT:" (:comment parent) " IS " (.size comp-phrases-for-parent)))

          comps 
          (deref (future
                   (lightning-bolt
                    comp-spec (get-lex parent :comp)
                    grammar
                    0
                    (conj path (str "C " " " (show-spec comp-spec))))))]

      (if (not (empty? comps))
        (do
          (log/debug (str "headed-phrase-add-comp: first comp is: " (fo-ps (first comps)) " which we will add to parent: " (fo-ps parent)))
          (lazy-cat
           (overc parent comps)
           (headed-phrase-add-comp (rest parents) grammar lexicon (+ 1 iter) path)))
        (headed-phrase-add-comp (rest parents) grammar lexicon (+ 1 iter) path)))))

(def can-log-if-in-sandbox-mode false)

(defn lexical-headed-phrases [parents lexicon depth]
  "return a lazy seq of phrases (maps) whose heads are lexemes."
  (if (not (empty? parents))
    (let [parent (first parents)]
      (lazy-seq
       (let [result (overh parent (get-lex parent :head))]
         (cons {:parent parent
                :headed-phrases result}
               (lexical-headed-phrases (rest parents) lexicon depth)))))))

(defn phrasal-headed-phrases [parents lexicon grammar depth path]
  "return a lazy seq of phrases (maps) whose heads are themselves phrases."
  (if (not (empty? parents))
    (let [parent (first parents)
          headed-phrases-of-parent (get-head-phrases-of parent)
          headed-phrases-of-parent (if (nil? headed-phrases-of-parent)
                                     (list)
                                     headed-phrases-of-parent)]
      (lazy-seq
       (cons {:parent parent
              :headed-phrases (let [bolts 
                                    (deref (future
                                             (lightning-bolt (get-in parent '(:head))
                                                             lexicon 
                                                             grammar
                                                             (+ 1 depth)
                                                             path)))]
                                (overh parents bolts))}
             (phrasal-headed-phrases (rest parents) lexicon grammar depth path))))))

;; TODO: move this to inside lightning-bolt.
(defn decode-gen-ordering2 [rand2]
  (cond (= rand2 0)
        "hLcP + hPcP"
        true
        "hPcP + hLcP"))

;; TODO: make option to just call (lazy-cat seq1 seq2 seq3) for efficiency:
;; this is simply a diagnostic tool.
(defn try-all-debug [order seq1 seq2 seq3 seq1-label seq2-label seq3-label path]
  (log/debug (str "order:" order))
  (log/debug (str "seq1 type:" (type seq1)))
  (log/debug (str "seq2 type:" (type seq2)))
  (log/debug (str "seq3 type:" (type seq3)))
  (if (not (empty? seq1))
    (lazy-seq
     (cons
      (let [first-of-seq1 (first seq1)]
        (do
          (log/debug (str "try-all ("seq1-label"@" path ") candidate:" (fo-ps first-of-seq1)))
          first-of-seq1))
      (try-all-debug order (rest seq1) seq2 seq3 seq1-label seq2-label seq3-label path)))
    (if (not (empty? seq2))
      (do (log/debug (str "try-all-debug: doing seq2."))
          (try-all-debug order
                         seq2 seq3 nil seq2-label seq3-label nil path))
      (if (not (empty? seq3))
        (do (log/debug (str "try-all-debug: doing seq3."))
            (try-all-debug
             order seq3 nil nil seq3-label nil nil path))))))

(defn try-all [order seq1 seq2 seq3 seq1-label seq2-label seq3-label path]
  (if true
    (try-all-debug order seq1 seq2 seq3 seq1-label seq2-label seq3-label path)
    (lazy-cat seq1 seq2 seq3)))

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

(defn parents-at-this-depth [head grammar depth]
  "subset of grammar possible at this depth where the phrase's head is the given head."
  (let [result
        (filter (fn [each]
                  (not (fail? each)))
                (map (fn [phrase]
                       ;; TODO: possibly: remove-paths such as (subcat) from head: would make it easier to call with lexemes:
                       ;; e.g. "generate a sentence whose head is the word 'mangiare'" (i.e. user passes the lexical entry as
                       ;; head param of (lightning-bolt)".
                       (unifyc phrase head))
                     (cond (= depth 0) ;; if depth is 0 (top-level), only allow phrases with empty subcat.
                           (filter (fn [phrase]
                                     (or true
                                     (empty? (get-in phrase '(:synsem :subcat)))))
                                   grammar)
                           (= depth 1)
                           (filter (fn [phrase]
                                     (or true
                                     (and (not (empty? (get-in phrase '(:synsem :subcat))))
                                          (empty? (get-in phrase '(:synsem :subcat :2))))))
                                   grammar)
                           true
                           '())))]
    ;; REALIZES:
;    (log/trace (str "parents-at-this-depth (depth=" depth ") for head: " (show-spec head) " returning result with size: " (.size result)))
    result))

(defn parents-with-phrasal-complements [parents-with-lexical-heads parents-with-phrasal-heads
                                        rand-parent-type-order]
  (log/info (str "starting parents-with-phrasal-complements.."))
  (let [parents-with-lexical-heads (filter (fn [parent]
                                             (not (= true (get-in parent '(:comp :phrasal)))))
                                           parents-with-lexical-heads)
        parents-with-phrasal-heads (filter (fn [parent]
                                             (not (= false (get-in parent '(:comp :phrasal)))))
                                           parents-with-phrasal-heads)]
    (cond (= rand-parent-type-order 0)
          (lazy-cat parents-with-lexical-heads parents-with-phrasal-heads)
          true
          (lazy-cat parents-with-phrasal-heads parents-with-lexical-heads))))

(defn log-path [path & [ depth ]]
  (let [depth (if depth depth 0)
        print-blank-line false]
    (if (> (.size path) 0)
      (do
        (log/info (str "LB@[" depth "]: " (first path)))
        (log-path (rest path) (+ depth 1)))
      (if print-blank-line (log/info (str ""))))))

(defn lightning-bolt [ & [spec lexicon grammar depth path]]
  (let [maxdepth 5
        spec (if spec spec :top)

        ;; parents are the subset of all phrases that unify with the spec.
        parents (filter (fn [phrase]
                          (not (fail? (unifyc phrase
                                              spec))))
                        grammar)

        log (log/info (str " subset of grammar that matches spec: " 
                           (string/join " " (map (fn [rule]
                                           (str (:comment rule)))
                                         parents))))


        remove-top-values (remove-top-values-log spec)
        debug (log/debug "")
        debug (log/debug "===start===")

        depth (if depth depth 0)

        rand-order (rand-int 3 0)
        rand-parent-type-order (rand-int 2 0)

        ;; short-circuits for dev/testing
        rand-order 1
        rand-parent-type-order 0

        path (if path (conj path
                            (str (decode-generation-ordering rand-order rand-parent-type-order) ": "
                                 remove-top-values))
                 ;; first element of path:
                 [ (str (decode-generation-ordering rand-order rand-parent-type-order) ": "
                        remove-top-values)])

        log (log-path path)

        ]

    (cond

     (= (.size matching-rules) 0)
     nil

;     (> depth maxdepth)
;     nil

;     (> (.size path) (* 2 maxdepth))
;     nil



     true
     (let [debug (log/debug (str "lightning-bolt first parent at this depth: "
                                 (fo-ps (first parents))))
           parents-with-phrasal-head-map 
           (fn [] (phrasal-headed-phrases (lazy-shuffle parents)
                                          (lazy-shuffle lexicon)
                                          grammar
                                          depth
                                          path))

           lexical-headed-phrases 
           (fn []
             (lexical-headed-phrases (lazy-shuffle parents)
                                     (lazy-shuffle lexicon)
                                     depth))

           parents-with-phrasal-head 
           (fn []
             (mapcat (fn [each-kv]
                       (let [phrases (:headed-phrases each-kv)]
                         parents))
                     parents-with-phrasal-head-map))

           parents-with-lexical-heads 
           (fn []
             (mapcat (fn [each-kv]
                       (let [headed-phrases (:headed-phrases each-kv)]
                         headed-phrases))
                     (lexical-headed-phrases)))

           ;; TODO: (lazy-shuffle) this.
           ;; TODO: cache this.
           parents-with-phrasal-heads-for-comp-phrases 
           (fn [] 
             (mapcat (fn [each-kv]
                       (let [parent (:parent each-kv)]
                         (if (not (= false (get-in parent '(:comp :phrasal))))
                           (let [phrases (:headed-phrases each-kv)]
                             phrases))))
                     (parents-with-phrasal-head-map)))

           parents-with-lexical-heads-for-comp-phrases 
           (fn [] 
             (mapcat (fn [each-kv]
                       (let [parent (:parent each-kv)]
                         (if (not (= false (get-in parent '(:comp :phrasal))))
                           (let [phrases (:headed-phrases each-kv)]
                             phrases))))
                     (lexical-headed-phrases)))

           one-level-trees
           (fn []
             (let [parents-with-lexical-heads (parents-with-lexical-heads)
                   one-level-trees
                   (if (not (empty? parents-with-lexical-heads))
                     (overc-complement-is-lexeme parents-with-lexical-heads (lazy-shuffle lexicon)))]
               (if (empty? one-level-trees)
                 (log/debug (str "one-level-trees is empty."))
                 (log/debug (str "one-level-trees is not empty; first is: " (fo (first one-level-trees)))))
               one-level-trees))

           with-phrasal-comps 
           (fn []
             (log/info (str "parents-with-phrasal-complements@" depth))
             (headed-phrase-add-comp (parents-with-phrasal-complements
                                      (parents-with-phrasal-heads-for-comp-phrases)
                                      (parents-with-lexical-heads-for-comp-phrases)
                                      rand-parent-type-order)
                                     grammar (lazy-shuffle lexicon) 0 path))

           debug (log/info (str "rand-order:" rand-order))

          ]

       (cond (= rand-order 0) ;; hLcL + rand2 + hPcL
             (lazy-cat
              (one-level-trees)
              (with-phrasal-comps)
              (overc-complement-is-lexeme (parents-with-phrasal-head) lexicon))


             (= rand-order 1) ;; rand2 + hLcL + hPcL
             (lazy-cat
              (log/info (str "RO1: doing with-phrasal-comps @" depth))
              (with-phrasal-comps)
              (log/info (str "RO1: doing one-level-trees @" depth))
;              (one-level-trees)
;              (overc-complement-is-lexeme (parents-with-phrasal-head) lexicon)
              )

             (= rand-order 2) ;; hPcL + rand2 + hLcL
             (lazy-cat
              (overc-complement-is-lexeme (parents-with-phrasal-head) lexicon)
              (with-phrasal-comps)
              (one-level-trees)))))))


;; aliases that might be easier to use in a repl:
(defn lb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

(defn lightningb [ & [head lexicon phrases depth]]
  (let [depth (if depth depth 0)
        head (if head head :top)]
    (lightning-bolt head lexicon phrases depth)))

