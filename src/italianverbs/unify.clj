(ns italianverbs.unify
  (:refer-clojure :exclude [get-in merge resolve])
  (:require
   [italianverbs.unify :refer :all]
   [clojure.set :refer :all]
   [clojure.string :as string]
   [clojure.tools.logging :as log]))

(defn get-head [sign]
  (if (get sign :head)
    (get-head (get sign :head))
    sign))

(defn resolve [arg]
  "if arg is not a ref, return arg. if is a ref, return (resolve @arg)"
  (if (= (type arg) clojure.lang.Ref)
    (resolve @arg)
    arg))

;; TODO: need tests: many tests use (get-in), but need more dedicated tests for it alone.
(defn get-in [in-map path & [not-found]]
  "same as clojure.core (get-in), but it resolves references if need be."
  (if (seq? in-map)
    (map (fn [each]
           (get-in each path not-found))
         in-map)
    (let [result
          (if (first path)
            (let [result (get in-map (first path) not-found)]
              (if (= result not-found) not-found
                  (get-in (resolve result) (rest path) not-found)))
            in-map)]
      (if (= (type result) clojure.lang.Ref)
        @result
        result))))

;; following is deprecated in favor of just (get-in) (above).
(defn get-in-r [map keys]
  (get-in map keys))

(defn get-r [map key]
  "same as clojure.core (get), but it resolves references if need be."
  (let [result (get map key)]
    (if (= (type result) clojure.lang.Ref)
      @result
      result)))

(defn get-root-head [sign]
  (cond
   (get sign :head)
   (get-root-head (get sign :head))
   true
   sign))

;; TODO: use multi-methods.
;; TODO: keep list of already-seen references to avoid
;; cost of traversing substructures more than once.
(defn fail? [fs]
  "(fail? fs) <=> true if at least one of fs's path's value is :fail."
  (if (= :fail fs) true
      (if (seq? fs) false
          (if (= fs :fail) true
              (do
                (defn failr? [fs keys]
;                  (log/error (str "FAILR: fs: " fs))
;                  (log/error (str "FAILR: keys: " keys))
                  (if (first keys)
                    (if (fail? (get-in fs (list (first keys))))
                      true
                      (failr? fs (rest keys)))
                    false))
                (cond
                 (= fs :fail) true
                 (map? fs)
                 (failr? fs (keys fs))
                 (= (type fs) clojure.lang.Ref)
                 (fail? @fs)
                 :else false))))))

(defn nonfail [maps]
  (filter (fn [each-map]
            (not (fail? each-map)))
          maps))

(defn fail-path [fs & [ fs-keys ] ]
  "find the first failing path in a fs."
  (if (map? fs)
    (let [fs-keys (if fs-keys fs-keys (keys fs))]
      (if (> (.size fs-keys) 0)
        (if (fail? (get-in fs (list (first fs-keys))))
          (cons (first fs-keys) (fail-path (get-in fs (list (first fs-keys)))))
          (fail-path fs (rest fs-keys)))))))

(declare copy)

(defn unify [val1 val2]
  (let [val1 val1
        val2 val2]
    (log/debug (str "unify val1: " val1))
    (log/debug (str "      val2: " val2))
    (cond
     (and (= val1 '())
          (= val2 :top))
     val1

     (and (= val1 '())
          (= val2 '()))
     val1

     (and (= val1 '()))
     :fail

     (and (= val1 nil)
          (= val2 :top))
     val1

     ;; TODO: what about (unify nil nil) > nil?
     ;; TODO: what about (unify X :top) => X? (probably pretty common).

     (= val1 nil)
     :fail

     ;; <sets>
     (and (set? val1)
          (set? val2)
          (empty? val2))
     (unify val1 :top)

     (and (set? val1)
          (set? val2)
          (empty? val1))
     (unify val2 :top)

     (and (set? val1)
          (set? val2))
     (let [debug (log/debug (str "unify: val1 and val2 are both sets."))
           filtered
           (filter (fn [each]
                     (not (fail? each)))
                   (mapcat (fn [each-val1]
                             (map (fn [each-val2]
                                    (unify (copy each-val1)
                                           (copy each-val2)))
                                  val2))
                           val1))]
       (if (empty? (rest filtered))
         (first filtered)
         (set filtered)))

     (set? val1)
     (let [debug (log/debug (str "unify: val1 is a set (but not val2)"))
           mapped
           (set (remove (fn [each]
                          (fail? each))
                        (map (fn [each-member]
                               (unify (copy each-member)
                                      (copy val2)))
                             val1)))]
       (cond (empty? mapped)
             :fail
             (= (.size mapped) 1)
             (first mapped)
             true
             mapped))

     (set? val2)
     (let [debug (log/debug (str "unify: val2 is a set (but not val1)"))
           mapped
           (set (remove (fn [each]
                          (fail? each))
                        (map (fn [each-member]
                               (unify (copy each-member)
                                      (copy val1)))
                             val2)))]
       (cond (empty? mapped)
             :fail
             (= (.size mapped) 1)
             (first mapped)
             true
             mapped))

     ;; </sets>

     ;; :fail
     (or (= :fail val1)
         (= :fail val2))
     :fail

     ;; seqs
     (seq? val1)
     (map (fn [each]
            (unify each val2))
          val1)

     ;; This is the canonical unification case: unifying two DAGs
     ;; (maps with possible references within them).
     ;;
     ;; merge-with: http://clojuredocs.org/clojure_core/clojure.core/merge-with
     ;;
     ;; "Returns a map that consists of the rest of the maps conj-ed onto
     ;; the first. If a key occurs in more than one map, the mapping(s)
     ;; from the latter (left-to-right) will be combined with the mapping in
     ;; the result by calling (f val-in-result val-in-latter)."
     (and (map? val1)
          (map? val2))
     (let [xp-val1 val1
           xp-val2 val2
           debug (log/debug (str "xp val1:" xp-val1))
           debug (log/debug (str "xp val2:" xp-val2))
           merge-result (merge-with unify xp-val1 xp-val2)
           debug (log/debug (str "merge-result:" merge-result))
           result merge-result]
       (if (not (nil? (some #{:fail} (vals result))))
         :fail
         (do
           (log/debug (str val1 " ; " val2 " map/map unification result:" result))
           result)))

     (and
      (= (type val1) clojure.lang.Ref)
      (not (= (type val2) clojure.lang.Ref)))
     (do (dosync
          (alter val1
                 (fn [x] (unify @val1 val2))))
         ;; alternative to the above (not tested yet):  (fn [x] (unify (fs/copy @val1) val2))))
         ;; TODO: why is this false-disabled? (document and test) or remove
         (if (and false (fail? @val1)) :fail
         val1))

     (and
      (= (type val2) clojure.lang.Ref)
      (not (= (type val1) clojure.lang.Ref)))
     (do (dosync
          (alter val2
                 (fn [x] (unify val1 @val2))))
         ;; alternative to the above (not tested yet): (fn [x] (unify val1 (fs/copy @val2)))))
         ;; TODO: why is this false-disabled? (document and test) or remove.
         (if (and false (fail? @val2)) :fail
         val2))

     (and
      (= (type val1) clojure.lang.Ref)
      (= (type val2) clojure.lang.Ref))
     (do
       (if (or (= val1 val2) ;; same reference.
               (= val1 @val2)) ;; val1 <- val2
         val1
         (if (= @val1 val2) ;; val1 -> val2
           val2
           (do
             (log/debug (str "unifying two refs: " val1 " and " val2))
             (dosync
              (alter val1
                     (fn [x] (unify @val1 @val2))))
             (dosync
              (alter val2
                     (fn [x] val1))) ;; note that now val2 is a ref to a ref.
             (log/debug (str "returning ref: " val1))
             (if (and false (fail? @val1)) :fail
             val1)))))

     ;; convoluted way of expressing: "if val1 has the form: {:not X}, then .."
     (not (= :notfound (:not val1 :notfound)))
     (if (= val2 :top)
;       :top ;; special case: (unify :top {:not X}) => :top
       val1
       ;; else
       (let [debug1 (log/debug (str "VAL1: " val1))
             debug2 (log/debug (str "VAL2: " val2))
             result (unify (:not val1) val2)]
         (if (= result :fail)
           val2
           :fail)))

     ;; convoluted way of expressing: "if val2 has the form: {:not X}, then .."
     (not (= :notfound (:not val2 :notfound)))
     (if (= val1 :top)
;       val1 ;; special case mentioned above in comments preceding this function.
       val2
       (let [debug1 (log/debug (str "VAL1: " val1))
             debug2 (log/debug (str "VAL2: " val2))
             result (unify val1 (:not val2))]
         (if (= result :fail)
           val1
           :fail)))

     (or (= val1 :fail)
         (= val2 :fail))
     :fail

     (= val1 :top) val2
     (= val2 :top) val1

     ;; these two rules are unfortunately necessary because of mongo/clojure storage of keywords as strings.
     (= val1 "top") val2
     (= val2 "top") val1

     ;; TODO: verify that these keyword/string exceptions are necessary - otherwise remove them.
     ;; :foo,"foo" => :foo
     (and (= (type val1) clojure.lang.Keyword)
          (= (type val2) java.lang.String)
          (= (string/replace-first (str val1) ":" "") val2))
     val1

     ;; "foo",:foo => :foo
     (and (= (type val2) clojure.lang.Keyword)
          (= (type val1) java.lang.String)
          (= (string/replace-first (str val2) ":" "") val1))
     val2

     (= val1 val2) val1

     ;; The follow two 2 rules allow values of :english and :italian that
     ;; are strings to over-ride values that are maps (in which
     ;; case they are specs of how to compute a string: agreement
     ;; information such as gender and number.
     ;; TODO: verify that these map/string exceptions are necessary - otherwise remove them.
     (and
      (map? val1)
      (string? val2))
     (do
       (log/debug "unifying a map and a string: ignoring the former and returning the latter: 'val2'")
       val2)

     (and
      (string? val1)
      (map? val2))
     (do
       (log/debug (str "unifying a string and a map: ignoring the latter and returning the former: '" val1 "'"))
       val1)

     :else ;; fail.
     (do
       (log/debug (str "(" val1 ", " val2 ") => :fail"))
       :fail))))

;; unify vs. merge:
;;
;; case 1:
;; (fs/unify {:a 42} {:a 42 :b 43})
;; => {:a 42 :b 43}
;;  and:
;; (fs/match {:a 42} {:a 42 :b 43})
;; => {:b 43, :a 42} ; ok: val2 specializes val1.
;;
;; case 2:
;; (fs/unify {:a 42 :b 43} {:a 42})
;; => {:a 42 :b 43}
;;  but:
;; (fs/match {:a 42 :b 43} {:a 42})
;; => :fail          ; fail: val2 does not specialize val1.

;; special cases:
;; (fs/match {:not X} :top)
;; => :top

(defn match [val1 val2]
  "match: like unify, but requires that every path in val1 must be in val2: in other words, val2 matches, or is a specialization, of val1."
  (let [keys1 (if (map? val1) (set (keys val1)))
        keys2 (if (map? val2) (set (keys val2)))]
    (log/debug (str "vals1: " val1))
    (log/debug (str "keys1: " keys1))
    (log/debug (str "vals2: " val2))
    (log/debug (str "keys2: " keys2))
    (log/debug (str "map? val1 " (map? val1)))
    (log/debug (str "map? val2 " (map? val2)))
    (log/debug (str "subset? keys1 keys2 " (subset? keys1 keys2)))
    (cond

     (= :fail val1)
     :fail

     (= :fail val2)
     :fail

     ;; if keys(val1) is not a subset of keys(val2), then fail.
     ;; same set is ok, since a set is a subset of itself.
     (and (map? val1)
          (map? val2)
          (not (subset? keys1 keys2)))
     (do
       (log/debug (str "SUBSET FAIL: " keys1 " is not a subset of:" keys2))
       :fail)

     (and (map? val1)
          (map? val2))
     (let [result (merge-with match val1 val2)]
       (if (not (nil? (some #{:fail} (vals result))))
         :fail
         result))
     (and
      (= (type val1) clojure.lang.Ref)
      (not (= (type val2) clojure.lang.Ref)))
     (do (dosync
          (alter val1
                 (fn [x] (match @val1 val2))))
         (if (and false (fail? @val1)) :fail
             val1))
     (and
      (= (type val2) clojure.lang.Ref)
      (not (= (type val1) clojure.lang.Ref)))
     (do (dosync
          (alter val2
                 (fn [x] (match val1 @val2))))
         (if (and false (fail? @val2)) :fail
             val2))
     (and
      (= (type val1) clojure.lang.Ref)
      (= (type val2) clojure.lang.Ref))
     (do
       (if (or (= val1 val2) ;; same reference.
               (= val1 @val2)) ;; val1 <- val2
         val1
         (if (= @val1 val2) ;; val1 -> val2
           val2
           (do
             (log/debug (str "unifying two refs: " val1 " and " val2))
             (dosync
              (alter val1
                     (fn [x] (match @val1 @val2))))
             (dosync
              (alter val2
                     (fn [x] val1))) ;; note that now val2 is a ref to a ref.
             (log/debug (str "returning ref: " val1))
             (if (and false (fail? @val1)) :fail
                 val1)))))

     ;; convoluted way of expressing: "if val1 has the form: {:not X}, then .."
     (not (= :notfound (:not val1 :notfound)))
     (if (= val2 :top)
       :top ;; special case mentioned above in comments preceding this function.
       ;; else, (if val2 is not :top)..
       (let [result (match (:not val1) val2)]
         (if (= result :fail)
           val2
           :fail)))

     ;; convoluted way of expressing: "if val2 has the form: {:not X}, then .."
     (not (= :notfound (:not val2 :notfound)))
     (if (= val1 :top) val1 ;; another special case: (match :top {:not X}) => :top
         (let [result (match val1 (:not val2))]
           (if (= result :fail)
             val1
             :fail)))

     (or (= val1 :fail)
         (= val2 :fail))
     :fail
     (= val1 :top) val2
     (= val2 :top) val1

     ;; these two rules are unfortunately necessary because of mongo/clojure storage of keywords as strings.
     (= val1 "top") val2
     (= val2 "top") val1

     ;; :foo,"foo" => :foo
     (and (= (type val1) clojure.lang.Keyword)
          (= (type val2) java.lang.String)
          (= (string/replace-first (str val1) ":" "") val2))
     val1

     ;; "foo",:foo => :foo
     (and (= (type val2) clojure.lang.Keyword)
          (= (type val1) java.lang.String)
          (= (string/replace-first (str val2) ":" "") val1))
     val2

     (= val1 val2) val1

     :else :fail)))

(defn ref? [val]
  (= (type val) clojure.lang.Ref))

(defn set-cross-product [input]
  (cond (or (not (map? input))
            (empty? input))
        input

        (map? input)
        (let [key (first (first input))
              val (set-cross-product (second (first input)))
              rest-of (set-cross-product (dissoc input key))]
          (cond (set? val)
                (unify (set (map (fn [each-val]
                                   {key each-val})
                                 val))
                       rest-of)

                (and (not (set? val))
                     (set? rest-of))
                (set (map (fn [member]
                            (unify {key val}
                                   member))
                        rest-of))

                (and (not (set? val))
                     (not (set? rest-of)))
                (conj {key val}
                      rest-of)

                true (throw (Exception. "set-cross-product: don't know what to do with this input (which is a map): " input))))

        true (throw (Exception. "set-cross-product: don't know what to do with this input: " input))))

;; TODO: as with (unify), use [val1 val2] as signature, not [& args].
(defn merge [& args]
  "warning: {} is the identity value, not nil; that is: (merge X {}) => X, but (merge X nil) => nil, (not X)."
  (let [val1 (first args)
        val2 (second args)]
    (cond

     (= (.count args) 1)
     (first args)

     (and (map? val1)
          (map? val2))
     (reduce #(merge-with merge %1 %2) args)

     (and
      (= (type val1) clojure.lang.Ref)
      (not (= (type val2) clojure.lang.Ref)))
     (do (dosync
          (alter val1
                 (fn [x] (merge @val1 val2))))
         val1)

     (and
      (= (type val2) clojure.lang.Ref)
      (not (= (type val1) clojure.lang.Ref)))
     (do (dosync
          (alter val2
                 (fn [x] (merge val1 @val2))))
         val2)

     (and
      (= (type val1) clojure.lang.Ref)
      (= (type val2) clojure.lang.Ref))
     (do (dosync
          (alter val1
                 (fn [x] (merge @val1 @val2))))
         val1)

     (and (= val2 :top)
          (not (= :notfound (:not val1 :notfound))))
     val1

     (not (= :notfound (:not val1 :notfound)))
     (let [result (unify (:not val1) val2)]
       (if (= result :fail)
         val2
         :fail))

     (and (= val1 :top)
          (not (= :notfound (:not val2 :notfound))))
     val2

     (not (= :notfound (:not val2 :notfound)))
     (let [result (unify val1 (:not val2))]
        (if (= result :fail)
          val1
          :fail))

     (or (= val1 :fail)
         (= val2 :fail))
     :fail

     (= val1 :top) val2
     (= val2 :top) val1
     (= val1 nil) val2

     ;; note difference in behavior between nil and :nil!:
     ;; (nil is ignored, while :nil! is not).
     ;; (merge 42 nil) => 42
     ;; (merge 42 :nil!) => :nil!
     (= val2 nil) val1
     (= val2 :nil!) val2
     (= val2 "nil!") val2 ;; needed because of translation error from mongod to clojure.

     (= val1 val2) val1

     :else ;override with remainder of arguments, like core/merge.
     (apply merge (rest args)))))

(defn merge-debug [& args]
  (log/debug (str "mergeD  v1:" (first args)))
  (log/debug (str "mergeD: v2:" (second args)))
  (let [retval (apply merge args)]
    (log/debug (str "retvalD: " retval))
    retval))

(def ^:dynamic *exclude-keys* #{:_id})

(defn deref-map [input]
  input)

;; TODO: remove *exclude-keys*,(pathify-r) and (pathify) in favor of fs's versions.
(def ^:dynamic *exclude-keys* (set #{:_id :ref :refmap}))

(defn pathify-r [fs & [prefix]]
"Transform a map into a map of paths/value pairs,
 where paths are lists of keywords, and values are atomic values.
 e.g.:
 {:foo {:bar 42, :baz 99}} =>  { { (:foo :bar) 42}, {(:foo :baz) 99} }
The idea is to map the key :foo to the (recursive) result of pathify on :foo's value."
  (mapcat (fn [kv]
            (let [key (first kv)
                  val (second kv)]
;              (println (str "K:" key))
              (if (not (contains? *exclude-keys* key))
                (if (or (= (type val) clojure.lang.PersistentArrayMap)
                        (= (type val) clojure.lang.PersistentHashMap))
                  (do
;                    (println (str "PAM"))
                    (pathify-r val (concat prefix (list key))))
                  (if (and (= (type val) clojure.lang.Ref)
                           (let [val @val]
                             (or (= (type val) clojure.lang.PersistentArrayMap)
                                 (= (type val) clojure.lang.PersistentHashMap))))
                    (pathify-r @val (concat prefix (list key)))
                  (do
;                    (println (str "not PAM" (type val)))
                    (list {(concat prefix (list key))
                           (if (= (type val) clojure.lang.Ref) @val ;; simply resolve references rather than trying to search for graph isomorphism.
                               val)})))))))
          fs))

(defn pathify [fs]
  (pathify-r fs))


(def uniq-using-recur
  (fn [sorted-vals]
    (loop [sv sorted-vals result nil]
      (let [first-val (first sv)]
        (if (nil? (first sv))
          result
          (let [second-val (second sv)]
            (if (= first-val second-val)
              (recur (rest sv)
                     result)
              (recur (rest sv)
                     (cons first-val result)))))))))

(defn uniq [sorted-vals]
  (reverse (uniq-using-recur sorted-vals)))

;; by comparison (causes stack overflow on large lists):
(defn uniq-recursive [sorted-vals]
  (let [first-val (first sorted-vals)]
    (if first-val
      (let [second-val (second sorted-vals)]
        (if second-val
          (if (= first-val second-val)
            (uniq-recursive (rest sorted-vals))
            (cons first-val
                  (uniq-recursive (rest sorted-vals))))
          (list first-val))))))

(defn paths-to-value [map value path]
  (log/debug (str "paths-to-value path: " path))
  (if (= map value) (list path)
      (if (= (type map) clojure.lang.Ref)
        (paths-to-value @map value path)
        (if (map? map)
          (mapcat (fn [key]
                    (paths-to-value (get map key) value (conj path key)))
                  (keys map))))))

(defn all-refs [input]
  (if input
    (if (= (type input) clojure.lang.Ref)
      (cons
       (if (= (type @input) clojure.lang.Ref)
         ;; dereference double-references (references to another reference) :
         @input
           ;; a simple reference: reference to a non-reference (e.g. a map, boolean, etc):
         input)
       (all-refs @input))
      (if (map? input)
        ;; TODO: fix bug here: vals resolves @'s
        (concat
         (mapcat (fn [key]
                   (let [val (get input key)]
                     (if (= (type input) clojure.lang.Ref)
                       (if (= (type @val) clojure.lang.Ref)
                         (list @val)
                         (list val)))))
                 input)
         (all-refs
          (map (fn [val]
                 ;; dereference double-references (references to another reference) :
                 (if (and (= (type val) clojure.lang.Ref)
                          (= (type @val) clojure.lang.Ref))
                   @val
                   ;; a simple reference: reference to a non-reference (e.g. a map, boolean, etc):
                   val))
               (vals input))))
        (if (and (seq? input)
                 (> (.size input) 0))
          (concat
           (all-refs (first input))
           (all-refs (rest input))))))))

(defn skeletize [input-val]
  (if (map? input-val)
    (let [exclude-serialized (dissoc input-val :serialized)]
      (zipmap (keys exclude-serialized)
              (map (fn [val]
                     (if (= (type val) clojure.lang.Ref)
                       :top
                       (if (map? input-val)
                       (skeletize val)
                       val)))
                   (vals exclude-serialized))))
    input-val))

;; TODO s/map/input-map/
;; TODO: merge or distinguish from all-refs (above)
(defn get-refs [input-map]
  (uniq (sort (all-refs input-map))))

;; TODO s/map/input-map/
(defn skels [input-map refs]
  "create map from reference to their skeletons."
  (let [refs (get-refs input-map)]
    (zipmap
     refs
     (map (fn [ref]
            (skeletize @ref))
          refs))))

(defn ref-skel-map [input-map]
  "associate each reference in _input-map_ with:
   1. its skeleton
   2. all paths to point to it."
  (let [refs (get-refs input-map)
        ;; skels returns a map from a reference to its skeleton.
        skels (skels input-map refs)]
    (zipmap
     ;; associate each ref with its skeleton.
     (map (fn [ref]
            {:ref ref
             :skel (get skels ref)})
          refs)

     ;; list of all paths that point to each ref in _input-map_.
     (map (fn [eachref]
            (paths-to-value input-map eachref []))
          refs))))

;; only used for testing/debugging: TODO: consider moving to test.unify.
(defn ser [input-map]
  (let [refs (get-refs input-map)
        skels (skels input-map refs)]
    (ref-skel-map input-map)))

(defn ref-skel-map-with-sets [input]
  "like ref-skel-map but can deal with sets as inputs."
  (let [refs (get-refs input)
        ;; skels returns a map from a reference to its skeleton.
        vals (skels input refs)]
    (zipmap
     ;; associate each ref with its skeleton.
     (map (fn [each-ref]
            {:ref (ref @each-ref)
             :skel (get vals each-ref)})
          refs)

     ;; list of all paths that point to each ref in _input-map_.
     (map (fn [each-ref]
            (paths-to-value input each-ref []))
          refs))))

;; start with (ser) (above): TODO: actually expand set values.
(defn externalize-set-values [input]
  (let [refs (get-refs input)
        skels (skels input refs)]
    (set (ref-skel-map input))))

(defn esv [input]
  (externalize-set-values input))

;; (((:a :c) (:b :c) (:d))
;;  ((:a) (:b))
;;  nil)
;;     =>
;; {((:a :c) (:b :c) (:d)) => 2
;;  ((:a)    (:b))         => 1
;;  nil                    => 0
;; }
(defn max-lengths [serialization]
  ;; check type (TODO: use multimethods instead)
  (if (= (first (first serialization)) ())
    (throw (Exception. (str "Serialization was badly formed. This is known to happen when a key's value is a sequence: for now, only maps and atoms are supported as values of keys.")))
    (let [keys (keys serialization)]
      (zipmap
       keys
       (map (fn [paths]
              (if (nil? paths) 0
                  (apply max (map (fn [path] (if (nil? path) 0 (.size path))) paths))))
            keys)))))

(defn sort-by-max-lengths [serialization]
  (let [max-lengths (max-lengths serialization)]
    (sort (fn [x y] (< (second x) (second y)))
          max-lengths)))

(defn sort-shortest-path-ascending-r [serialization path-length-pairs]
  (if (first path-length-pairs)
    (let [path-length-pair (first path-length-pairs)
          paths (first path-length-pair)
          max-length (second path-length-pair)]
      (cons
       (list paths
             (get serialization paths))
       (sort-shortest-path-ascending-r serialization (rest path-length-pairs))))))

(defn ser-intermed [input-map]
  (let [top-level (skeletize input-map)
        rsk (ref-skel-map input-map)
        sk (map (fn [ref-skel]
                  (:skel ref-skel))
                (keys rsk))]
    (merge
     {nil top-level}
     (zipmap
      (vals rsk)
      sk))))

(defn create-shared-values [serialized]
  (map (fn [paths-vals]
         (let [val (second paths-vals)]
           ;; TODO: why/why not do copy val rather than just val(?)
           (ref val)))
       serialized))

(defn create-path-in [path value]
  "create a path starting at map through all keys in map:
   (create-path-in '(a b c d e) value) => {:a {:b {:c {:d {:e value}}}}})"
  (if (first path)
    (if (rest path)
      (let [assigned (create-path-in (rest path) value)]
        {(keyword (first path)) assigned})
      {(first path) value})
    value))

;; Serialization format is a sequence:
;; (
;;  paths1 => map1 <= 'base'
;;  paths2 => map2
;;  ..
;; )
;; 'base' is the outermost map 'skeleton' (
;; a 'skeleton' is a map with the dummy placeholder
;; value :top).
;;
;; Note that (deserialize) should be able to cope with
;; both lists and arrays (i.e. just assume a sequence).
;; (see more docs about serialization above the (serialize) function definitiion.
(defn deserialize [serialized]
  (cond (set? serialized)
        (set (map (fn [each]
                    (deserialize each))
                  serialized))
        true
        (let [base (second (first serialized))]
          (apply merge-debug
                 (let [all
                       (cons base
                             (flatten
                              (map (fn [paths-val]
                                     (let [paths (first paths-val)
                                           val (ref (second paths-val))]
                                       (map (fn [path]
                                              (create-path-in path val))
                                            paths)))
                                   (rest serialized))))]
                   all)))))

(defn serialize [input-map]
  (let [memoized (get input-map :serialized :none)]
    (if (not (= memoized :none))
      (let [debug (log/debug "using cached serialization.")]
        memoized)
      (let [debug (log/debug "no cached value: serializing at runtime.")
            ser (set-cross-product (ser-intermed input-map))]
        ;;
        ;; ser is a intermediate (but fully-serialized) representation
        ;; of the input map, as a map from pathsets to reference-free maps
        ;; (maps which have no references within them).

        ;; In place of the references in the original map, the reference-free
        ;; maps have simply a dummy value (the value :top) stored where the
        ;; the reference is in the input-map.
        ;;
        ;; ser:
        ;;
        ;;   pathset    |  value
        ;; -------------+---------
        ;;   pathset1   => value1
        ;;   pathset2   => value2
        ;;      ..         ..
        ;;   nil        => outermost_map
        ;;
        ;; Each pathset is a set of paths to a shared value, the value
        ;; shared by all paths in that pathset.
        ;;
        ;; The last row shown is for the outermost_map that represents
        ;; the entire input, which is why its pathset is nil.
        ;;
        ;; However, ser is not sorted by path length: it needs to be
        ;; sorted so that, when deserialization is done, assignment
        ;; will occur in the correct order: shortest path first.

        ;; Thefore, we now sort _ser_ in a shortest-path-first order, so that
        ;; during de-serialization, all assignments will happen in this
        ;; same correct order.
        (if (not (set? ser))
          (sort-shortest-path-ascending-r ser (sort-by-max-lengths ser))
          (set (map (fn [each-ser]
                      (sort-shortest-path-ascending-r each-ser (sort-by-max-lengths each-ser)))
                    ser)))))))

(defn optimized-ser [input-map]
  "generate a better serialized form that removes intermediate refs (refs to other refs)"
  (let [serialized (serialize input-map)]
    serialized))

(defn copy [input]
  (log/debug (str "copy: " input))
  (cond (set? input)
        (set (map (fn [each]
                    (copy each))
                  input))
        (seq? input)
        (map (fn [each]
               (copy each))
             input)
        true
        (deserialize (serialize input))))

(defn trunc [serialized]
  "create a new serialized map with all paths removed that are non-immediate."
  (map (fn [paths-skel-pair]
         (let [paths (first paths-skel-pair)
               skel (second paths-skel-pair)]
           (let [filtered-paths
                 (filter (fn [path]
                           (let [take-2 (take 2 path)]
                             (and (not (= take-2
                                          '(:head :head)))
                                  (not (= take-2
                                          '(:comp :comp)))
                                  (not (= take-2
                                          '(:head :comp)))
                                  (not (= take-2
                                          '(:comp :2)))
                                  (not (= take-2
                                          '(:head :1)))
                                  (not (= take-2
                                          '(:head :2)))
                                  (not (= take-2
                                          '(:1 :1)))
                                  (not (= take-2
                                          '(:1 :comp)))
                                  (not (= take-2
                                          '(:1 :head)))
                                  (not (= take-2
                                          '(:1 :2)))
                                  (not (= take-2
                                          '(:2 :2)))
                                  (not (= take-2
                                          '(:2 :1)))
                                  (not (= take-2
                                          '(:2 :head)))
                                  (not (= take-2
                                          '(:2 :comp))))))
                         paths)]
           (list filtered-paths skel))))
       serialized))

(defn copy-trunc [map]
  (log/debug (str "copy: " map))
  (deserialize (trunc (serialize map))))

(defn unifyc [& args]
  "like fs/unify, but fs/copy each argument before unifying."
  (do
    (log/debug (str "unifyc: args: " (str args)))
    (if (not (empty? args))
      (if (empty? (rest args))
        (first args)
        (unify (copy (first args))
               (apply unifyc (rest args))))
      :top)))

(defn has-path [path paths]
  (if (first paths)
    (if (= (first paths) path)
      true
      (has-path path (rest paths)))))

(defn path-to-ref-index [serialized path n]
  "given serialized form of a map, find the index for _path_. Start with 0."
  (if (first serialized)
    (let [paths (butlast (first serialized))
          has-path (has-path path (first paths))]
      (if (not (nil? has-path))
        n
        (path-to-ref-index (rest serialized) path (+ n 1))))))

(defn compare-bytewise [a b index]
  "compare two byte by casting each byte to short."
  (if (> (alength a) index)
    (if (> (alength b) index)
      (if (= (nth a index)
             (nth b index))
        (compare-bytewise a b (+ 1 index))
        (< (nth a index)
           (nth b index)))
      true)
    false))

(defn sorted-paths-1 [paths]
  (sort (fn [x y]
          (if (< (.size x) (.size y))
            true
            (compare-bytewise (.getBytes (str x)) (.getBytes (str y)) 0)))
        paths))

(defn sorted-paths [serialized path n index]
  (let [lookup (nth serialized index)
        allpaths (seq (first (butlast lookup)))]
    (sorted-paths-1 allpaths)))

(defn is-first-path [serialized path n index]
  (if (nil? index)
    (throw (Exception. (str "Index was null in serialized feature structure: " serialized)))
    (let [lookup (nth serialized index)
          firstpath (seq (first (sorted-paths serialized path n index)))]
      (if (or true (= (seq path) firstpath))
        (do ;(println (str "path: " (seq path) " is first of " (sorted-paths serialized path n index)))
            true)
        (do ;(println (str "path: " (seq path) " is NOT first of " (sorted-paths serialized path n index)))
            false)))))


(defn first-path [serialized path n index]
  (let [lookup (nth serialized index)
        firstpath (seq (first (sorted-paths serialized path n index)))]
    firstpath))

(defn ref= [map path1 path2]
  "return true iff path1 and path2 point to the same object."
  ;; TODO: add error checking.
  (let [butlast-val1 (get-in map (butlast path1))
        butlast-val2 (get-in map (butlast path2))]
    (= (get butlast-val1 (last path1))
       (get butlast-val2 (last path2)))))

(defn strip-refs [map-with-refs]
  "return a map like map-with-refs, but without refs - (e.g. {:foo (ref 42)} => {:foo 42}) - used for printing maps in plain (i.e. non html) format"
  (cond
   (= map-with-refs {})
   {}
   (map? map-with-refs)
   (let [map-keys (sort (keys map-with-refs))]
     (let [first-key (first (keys map-with-refs))
           val (get map-with-refs first-key)]
       (conj
        {first-key (strip-refs val)}
        (strip-refs (dissoc map-with-refs first-key)))))
   (= (type map-with-refs) clojure.lang.Ref)
   (strip-refs (deref map-with-refs))

   ;; TODO: print a quote mark (') before lists.
   ;; e.g. (list :foo) becomes "'(:foo)" when printed out.
;   (seq? map-with-refs)
;   (cons #\' map-with-refs)

   :else
   map-with-refs))

(defn take-powerset [set1 set2]
  "unify set1 by set2."
  set1)

(defn jump-in [fs]
  (let [graph-sets {} ;; the power-set of graphs from fs.
        tree-sets {} ;; the power set of values from fs, without reentraces
        ]
    (let [result (take-powerset graph-sets tree-sets)]
      result)))

