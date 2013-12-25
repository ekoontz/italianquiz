(ns italianverbs.over
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:require
   [clojure.core :exclude [get-in]]
   [clojure.core :as core] ;; This allows us to use core's get-in by doing "(core/get-in ..)"

   [clojure.set :refer :all]
   [clojure.string :as string]

   [clojure.tools.logging :as log]

   [italianverbs.lexicon :refer :all]
   [italianverbs.lexiconfn :refer :all]
   [italianverbs.morphology :refer [finalize fo]]
   [italianverbs.unify :refer :all :exclude [unify]]))

;; tree-building functions: useful for developing grammars.

(defn into-list-of-maps [arg]
  (cond

   (seq? arg)
   arg

   (set? arg)
   (seq arg)

   (map? arg)
   (list arg)

   (string? arg)
   (seq (it arg))

   (nil? arg)
   (list :top)

   (keyword? arg)
   (list arg)

   true (throw (Exception. (str "into-map: don't know what to do with a " (type arg) ".")))))

(defn mylazycat [one two]
  (lazy-cat one two))

(declare overh)
(declare overc)

(defn moreover-head [parent child lexfn-sem-impl]
  (do
    (log/debug (str "moreover-head (candidate) parent: " (fo parent)))
    (log/debug (str "moreover-head (candidate) parent sem: " (get-in parent '(:synsem :sem) :no-semantics)))
    (log/debug (str "moreover-head (candidate) head child sem:" (get-in child '(:synsem :sem) :top)))
    (log/debug (str "moreover-head (candidate) head:" (fo child)))
    (let [result
          (unifyc parent
                  (unifyc {:head child}
                          {:head {:synsem {:sem (lexfn-sem-impl (get-in child '(:synsem :sem) :top))}}}))]
      (if (not (fail? result))
        (let [debug (log/debug (str "moreover-head " (get-in parent '(:comment)) " (SUCCESS) result sem: " (get-in result '(:synsem :sem))))
              debug (log/debug (str "moreover-head (SUCCESS) parent (2x) sem: " (get-in parent '(:synsem :sem))))]
          (merge {:head-filled true}
                 result))
        (let [debug (log/debug (str "moreover-head " (fo child) "/" (get-in parent '(:comment)) "," (fo child) "/" (get-in child '(:comment))))
              fail-path (fail-path result)
              debug (log/debug (str " fail-path: " fail-path))
              debug (log/debug (str " path to head-value-at-fail:" (rest fail-path)))
              debug (log/debug (str " head: " (fo child)))
              debug (log/debug (str " head-value-at-fail: " (get-in child (rest fail-path))))
              debug (log/debug (str " parent-value-at-fail: " (get-in parent fail-path)))]
          (do
            (log/debug (str "FAIL: " fail-path))
            :fail))))))

;; Might be useful to set the following variable to true,
;; if doing grammar development and it would be unexpected
;; to have a failing result from calling (moreove-comp)
;; with certain arguments.
(def ^:dynamic *throw-exception-if-failed-to-add-complement* false)

;; TODO: move to (over)
(defn moreover-comp [parent child lexfn-sem-impl]
  (log/debug (str "moreover-comp parent: " (fo parent)))
  (log/debug (str "moreover-comp comp:" (fo child)))
  (log/debug (str "moreover-comp type parent: " (type parent)))
  (log/debug (str "moreover-comp type comp:" (type child)))
  (let [result
        (unifyc parent
                (unifyc {:comp child}
                        {:comp {:synsem {:sem (lexfn-sem-impl (get-in child '(:synsem :sem) :top))}}}))]
    (if (not (fail? result))
      (let [debug (log/trace (str "moreover-comp (SUCCESS) parent (2x) sem: " (get-in parent '(:synsem :sem))))]
        (let [result
              (merge {:comp-filled true}
                     result)]
          (log/debug (str "moreover-comp (SUCCESS) merged result:(fo) " (fo result)))
          )
          result)
      (do
        (log/debug "moreover-comp: fail at: " (fail-path result))
        (if (and
             *throw-exception-if-failed-to-add-complement*
             (get-in child '(:head)))
          (throw (Exception. (str "failed to add complement: " (fo child) "  to: phrase: " (fo parent)
                                  ". Failed path was: " (fail-path result)
                                  ". Value of parent at path is: "
                                  (get-in parent (fail-path result))
                                  "; Synsem of child is: "
                                  (get-in child '(:synsem) :top)))))
        (log/debug "moreover-comp: complement synsem: " (get-in child '(:synsem) :top))
        (log/debug "moreover-comp:  parent value: " (get-in parent (fail-path result)))
        :fail))))

(defn overh [parent head]
  "add given head as the head child of the phrase: parent."
  (log/debug (str "overh parent type: " (type parent)))
  (log/debug (str "overh head  type: " (type head)))

  (log/debug (str "set? parent:" (set? parent)))
  (log/debug (str "seq? parent:" (seq? parent)))
  (log/debug (str "seq? head:" (seq? head)))

  (if (map? parent)
    (if (get-in parent '(:comment))
      (log/debug (str "parent:" (get-in parent '(:comment)))))
    (log/debug (str "parent:" (fo parent))))
  (if (map? head)
    (log/debug (str "head: " (fo head))))

  (cond

   (or
    (seq? parent)
    (set? parent))
   (let [parents (lazy-seq parent)]
     (filter (fn [result]
               (not (= result '(:fail))))
             (map (fn [each-parent]
                    (overh each-parent head))
                  parents)))

   (string? head)
   (overh parent (it head))

   (set? head)
   (do (log/debug "head is a set: converting to a seq.")
       (overh parent (lazy-seq head)))

   (seq? head)
   (let [head-children head]
     (log/debug (str "head is a seq - actual type is " (type head)))
     (filter (fn [result]
               (not (= result '(:fail))))
             (map (fn [each-child]
                    (overh parent each-child))
                  head-children)))

   true
   (do
     (log/debug (str "overh: parent and head are both maps: put head under parent. Parent=" (:comment parent) "; head=" (fo head)))
     (list
      (moreover-head parent head sem-impl)))))

;; Haskell-looking signature:
;; (parent:map) X (child:{set,seq,fs}) => list:map
;; TODO: verify that the above commentn about the signature
;; is still true.
(defn overc [parent comp]
  "add given child as the comp child of the phrase: parent."
  (log/debug (str "overc parent type: " (type parent)))
  (log/debug (str "overc comp  type: " (type comp)))

  (log/debug (str "set? parent:" (set? parent)))
  (log/debug (str "seq? parent:" (seq? parent)))
  (log/debug (str "seq? comp:" (seq? comp)))

  (if (map? parent)
    (if (get-in parent '(:comment))
      (log/debug (str "parent:" (get-in parent '(:comment)))))
    (log/debug (str "parent:" (fo parent))))
  (if (map? comp)
    (log/debug (str "comp: " (fo comp))))

  (cond

   (or
    (seq? parent)
    (set? parent))
   (let [parents (lazy-seq parent)]
     (filter (fn [result]
               (not (= result '(:fail))))
             (mapcat (fn [each-parent]
                       (overc each-parent comp))
                     parent)))

   (string? comp)
   (overc parent (it comp))

   (set? comp)
   (do (log/debug "comp is a set: converting to a seq.")
       (overc parent (lazy-seq comp)))

   (seq? comp)
   (let [comp-children comp]
     (log/debug (str "comp is a seq - actual type is " (type comp)))
     (filter (fn [result]
               (not (= result '(:fail))))
             (mapcat (fn [each-child]
                       (overc parent each-child))
                     comp-children)))

   true
   (do
     (log/debug (str "overh: parent and comp are both maps: put comp under parent. Parent=" (:comment parent) "; comp=" (fo comp)))
     (list
      (moreover-comp parent comp sem-impl)))))

(defn overhc [parent head comp]
  (overc (overh parent head) comp))

(defn over [parents child1 & [child2]]
  (cond (vector? child1)
        (over parents (set child1) child2)
        (vector? child2)
        (over parents child1 (set child2))
        true
  (if (nil? child2) (over parents child1 :top)
      (if (map? parents)
        (over (list parents) child1 child2)
        (if (not (empty? parents))
          (let [parent (first parents)]
            (log/debug (str "over: parent: " parent))
            (concat
             (cond (and (map? parent)
                        (not (nil? (:serialized parent))))
                   ;; In this case, supposed 'parent' is really a lexical item: for now, definition of 'lexical item' is,
                   ;; it has a non-nil value for :serialized - just return nil, nothing else to do.

                   (throw (Exception. (str "Don't know what to do with this parent: " parent)))

                   (and (map? parent)
                        (not (nil? (:schema parent))))
                   ;; figure out whether head is child1 or child2:
                   (let [head
                         (cond
                          (= \c (nth (str (:schema parent)) 0))
                          child2

                          (= \h (nth (str (:schema parent)) 0))
                          child1

                          true
                          (throw (Exception. (str "Don't know what the head-vs-complement ordering is for parent: " parent))))
                         comp
                         (if (= head child1)
                           child2 child1)]
                     (filter (fn [each] (not (fail? each)))
                             (overhc parent head comp)))

                   ;; if parent is a symbol, evaluate it; should evaluate to a list of expansions (which might also be symbols, etc).
                   (symbol? parent)
                   (over (eval parent) child1 child2)

                   ;; if parent is map, do introspection: figure out the schema from the :schema-symbol attribute,
                   ;; and figure out head-comp ordering from :first attribute.
                   (and (map? parent)
                        (not (nil? (:schema-symbol parent))))
                   (filter (fn [each]
                             (not (fail? each)))
                           (overhc parent
                                   (if (= (:first parent) :head)
                                     child1 child2)
                                   (if (= (:first parent) :head)
                                     child2 child1)))
                   true
                   (throw (Exception. (str "Don't know what to do with parent: " parent))))

             (over (rest parents) child1 child2))))))))


