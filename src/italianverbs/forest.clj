(ns italianverbs.forest
  (:refer-clojure :exclude [get-in deref merge resolve find future parents rand-int])
  (:require
   [clojure.core :as core]
   [clojure.set :refer :all]
   [clojure.tools.logging :as log]
   [italianverbs.cache :refer (get-cache get-comp-phrases-of get-head-phrases-of get-lex
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

(defn headed-phrase-add-comp [parents phrases lexicon & [iter path]]
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
                    comp-spec (get-lex parent :comp lexicon)
                    comp-phrases-for-parent
                    0
                    (conj path (str "C " " " (show-spec comp-spec))))))]

      (if (not (empty? comps))
        (do
          (log/debug (str "headed-phrase-add-comp: first comp is: " (fo-ps (first comps)) " which we will add to parent: " (fo-ps parent)))
          (lazy-cat
           (overc parent comps)
           (headed-phrase-add-comp (rest parents) phrases lexicon (+ 1 iter) path)))
        (headed-phrase-add-comp (rest parents) phrases lexicon (+ 1 iter) path)))))

(def can-log-if-in-sandbox-mode false)

(defn lexical-headed-phrases [parents lexicon phrases depth]
  "return a lazy seq of phrases (maps) whose heads are lexemes."
  (if (not (empty? parents))
    (let [parent (first parents)]
      (lazy-seq
       (let [result (overh parent (get-lex parent :head lexicon))]
         (cons {:parent parent
                :headed-phrases result}
               (lexical-headed-phrases (rest parents) lexicon phrases depth)))))))

(defn phrasal-headed-phrases [parents lexicon phrases depth path]
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
                                                             lexicon headed-phrases-of-parent (+ 1 depth)
                                                             path)))]
                                (overh parents bolts))}
             (phrasal-headed-phrases (rest parents) lexicon phrases depth path))))))

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

(defn parents-at-this-depth [head phrases depth]
  "subset of phrases possible at this depth where the phrase's head is the given head."
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
                                   phrases)
                           (= depth 1)
                           (filter (fn [phrase]
                                     (or true
                                     (and (not (empty? (get-in phrase '(:synsem :subcat))))
                                          (empty? (get-in phrase '(:synsem :subcat :2))))))
                                   phrases)
                           true
                           '())))]
    ;; REALIZES:
;    (log/trace (str "parents-at-this-depth (depth=" depth ") for head: " (show-spec head) " returning result with size: " (.size result)))
    result))

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

(defn log-path [path & [ depth ]]
  (let [depth (if depth depth 0)
        print-blank-line false]
    (if (> (.size path) 0)
      (do
        (log/info (str "LB@[" depth "]: " (first path)))
        (log-path (rest path) (+ depth 1)))
      (if print-blank-line (log/info (str ""))))))

;; TODO: s/head/head-spec/
(defn lightning-bolt [ & [head lexicon phrases depth path]]
  (let [maxdepth 5
        head (if head head :top)
        remove-top-values (remove-top-values-log head)
        debug (log/debug "")
        debug (log/debug "===start===")
        path (if path (conj path
                            (str "H" depth " " remove-top-values))
                 ;; first element of path:
                 [ (str "H" depth " " remove-top-values) ])
        log (log-path path)

        depth (if depth depth 0)

        rand-order (rand-int 3 0)
        rand-parent-type-order (rand-int 2 0)

        log (log/debug (str "rand-order at depth:" depth " is: "
                            (decode-generation-ordering rand-order rand-parent-type-order)
                            "(rand-order=" rand-order ";rand-parent-type-order=" rand-parent-type-order ")"))

        parents-at-this-depth (lazy-shuffle (parents-at-this-depth head phrases depth))

        cache (get-cache phrases lexicon)]
    (cond

     (empty? parents-at-this-depth)
     (do (log/debug "lb: no parents with depth:" depth ";returning empty list.")
         nil)

     true
     (let [debug (log/debug (str "lightning-bolt first parent at this depth: "
                                 (fo-ps (first parents-at-this-depth))))
           parents-with-phrasal-head-map (phrasal-headed-phrases parents-at-this-depth
                                                                 lexicon
                                                                 phrases
                                                                 depth
                                                                 path)

           lexical-headed-phrases 
           (fn []
             (let [lexical-headed-phrases (lexical-headed-phrases parents-at-this-depth
                                                                  (lazy-shuffle lexicon)
                                                                  phrases
                                                                  depth)]
               (if (empty? lexical-headed-phrases)
                 (log/debug (str "no lexical-headed-phrases."))
                 (log/debug (str "lexical-headed-phrases is non-empty; the first is: " (fo (first lexical-headed-phrases)))))
               lexical-headed-phrases))

           parents-with-phrasal-head 
           (fn []
             (let [parents-with-phrasal-head (mapcat (fn [each-kv]
                                                       (let [phrases (:headed-phrases each-kv)]
                                                         phrases))
                                                     parents-with-phrasal-head-map)]
               (if (empty? parents-with-phrasal-head)
                 (log/debug (str "hP: empty."))
                 (log/debug (str "hP: nonempty; first:"
                                 (fo-ps (first parents-with-phrasal-head)))))
               parents-with-phrasal-head))

           parents-with-lexical-heads 
           (fn []
             (let [parents-with-lexical-heads (mapcat (fn [each-kv]
                                                        (let [phrases (:headed-phrases each-kv)]
                                                          phrases))
                                                      (lexical-headed-phrases))]
               (if (empty? parents-with-lexical-heads)
                 (log/debug (str "hL: empty."))
                 (log/debug (str "hL: nonempty; first: " (fo-ps (first parents-with-lexical-heads)))))
               parents-with-lexical-heads))

           ;; TODO: (lazy-shuffle) this.
           ;; TODO: cache this.
           parents-with-phrasal-heads-for-comp-phrases 
           (fn [] 
             (let [parents-with-phrasal-heads-for-comp-phrases 
                   (mapcat (fn [each-kv]
                             (let [parent (:parent each-kv)]
                               (if (not (= false (get-in parent '(:comp :phrasal))))
                                 (let [phrases (:headed-phrases each-kv)]
                                   phrases))))
                           parents-with-phrasal-head-map)]
               (if (empty? parents-with-phrasal-heads-for-comp-phrases)
                 (log/debug (str "cP is empty."))
                 (log/debug (str "cP is nonempty; first: " (fo-ps (first parents-with-phrasal-heads-for-comp-phrases)))))
               parents-with-phrasal-heads-for-comp-phrases))

           parents-with-lexical-heads-for-comp-phrases 
           (fn [] (mapcat (fn [each-kv]
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
                     (overc-with-cache parents-with-lexical-heads cache (lazy-shuffle lexicon)))]
               (if (empty? one-level-trees)
                 (log/debug (str "one-level-trees is empty."))
                 (log/debug (str "one-level-trees is not empty; first is: " (fo (first one-level-trees)))))
               one-level-trees))

           with-phrasal-comps 
           (fn []
             (let [with-phrasal-comps
                   (headed-phrase-add-comp (parents-with-phrasal-complements
                                            (parents-with-phrasal-heads-for-comp-phrases)
                                            (parents-with-lexical-heads-for-comp-phrases)
                                            rand-parent-type-order)
                                           phrases (lazy-shuffle lexicon) 0 path)]
               (if (empty? with-phrasal-comps)
                 (log/debug (str "cP is empty."))
                 (log/debug (str "cP is not empty; first is: " (fo-ps (first with-phrasal-comps)))))
               with-phrasal-comps))

          ]

       (cond (= rand-order 0) ;; hLcL + rand2 + hPcL
             (lazy-cat
              (one-level-trees)
              (with-phrasal-comps)
              (overc-with-cache (parents-with-phrasal-head) cache lexicon))


             (= rand-order 1) ;; rand2 + hLcL + hPcL
             (lazy-cat
              (with-phrasal-comps)
              (one-level-trees)
              (overc-with-cache (parents-with-phrasal-head) cache lexicon))

             (= rand-order 2) ;; hPcL + rand2 + hLcL
             (lazy-cat
              (overc-with-cache (parents-with-phrasal-head) cache lexicon)
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

