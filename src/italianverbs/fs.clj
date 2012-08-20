(ns italianverbs.fs
  (:use [clojure.set])
  (:require
   [italianverbs.fs :as fs] ;; needed maybe by the (eval fs/..) stuff below.
   [clojure.string :as string]
   [clojure.core :as core]
   [clojure.contrib.string :as stringc]
   [clojure.contrib.str-utils2 :as str-utils]))

(defn get-head [sign]
  (if (get sign :head)
    (get-head (get sign :head))
    sign))

;; TODO: need tests: some tests use (get-in), but need more dedicated tests for it alone.
(defn get-in [map keys]
  "same as clojure.core (get-in), but it resolves references if need be."
  (let [result 
        (if (first keys)
          (let [result (get map (first keys))]
            (if (= (type result) clojure.lang.Ref)
              (get-in @result (rest keys))
              (get-in result (rest keys))))
          map)]
    (if (= (type result) clojure.lang.Ref)
      @result
      result)))

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

(defn unify [& args]
  (let [val1 (first args)
        val2 (second args)]
    (cond

     (= (.count args) 1)
     (first args)
     
     (and (or (= (type val1) clojure.lang.PersistentArrayMap)
              (= (type val1) clojure.lang.PersistentHashMap))
          (or (= (type val2) clojure.lang.PersistentArrayMap)
              (= (type val2) clojure.lang.PersistentHashMap)))
     (reduce #(merge-with unify %1 %2) args)

     (and 
      (= (type val1) clojure.lang.Ref)
      (not (= (type val2) clojure.lang.Ref)))
     (do (dosync
          (alter val1
                 (fn [x] (unify @val1 val2))))
         val1)

     (and 
      (= (type val2) clojure.lang.Ref)
      (not (= (type val1) clojure.lang.Ref)))
     (do (dosync
          (alter val2
                 (fn [x] (unify val1 @val2))))
         val2)

     (and 
      (= (type val1) clojure.lang.Ref)
      (= (type val2) clojure.lang.Ref))
      (do (dosync
           (alter val1
                  (fn [x] (unify @val1 @val2))))
          (dosync
           (alter val2
                  (fn [x] @val1)))
       val1)
     
     (not (= :notfound (:not val1 :notfound)))
     (let [result (unify (:not val1) val2)]
       (if (= result :fail)
         val2
         :fail))

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

(defn merge [& args]
  (let [val1 (first args)
        val2 (second args)]
    (cond

     (= (.count args) 1)
     (first args)

     (and (or (= (type val1) clojure.lang.PersistentArrayMap)
              (= (type val1) clojure.lang.PersistentHashMap))
          (or (= (type val2) clojure.lang.PersistentArrayMap)
              (= (type val2) clojure.lang.PersistentHashMap)))
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

     (not (= :notfound (:not val1 :notfound)))
     (let [result (unify (:not val1) val2)]
       (if (= result :fail)
         val2
         :fail))

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

(defn unify-and-apply [maps]
  "merge maps, and then apply the function (:fn merged) to the merged map."
  (let [merged
        (eval `(fs/unify ~@maps))
        fn (:fn merged)
        eval-fn (if (and fn (= (type fn) java.lang.String))
                  (eval (symbol fn)) ;; string->fn (since a fn cannot (yet) be 
                  fn)] ;; otherwise, assume it's a function.
    (if (:fn merged)
      (fs/unify merged
            (apply eval-fn
                   (list merged)))
      maps)))

(defn set-paths [fs paths val]
  (let [path (first paths)]
    (if path
      (set-paths (assoc-in fs path val) (rest paths) val)
      fs)))

(defn encode-refs [fs inv-fs]
  (if (first inv-fs)
    (let [ref-pair (first inv-fs)]
      ;; ref-pair: <value, set-of-pairs-that-point-to-this-reference> >
      (let [value (first ref-pair)
            paths (second ref-pair)]
       (encode-refs
        (set-paths fs paths value)
        (rest inv-fs))))
     fs))

(def *exclude-keys* #{:_id})

(defn deref-map [input]
  input)

(defn pathify [fs & [prefix]]
"Transform a map into a map of paths/value pairs,
 where paths are lists of keywords, and values are atomic values.
 e.g.:
 {:foo {:bar 42, :baz 99}} =>  { { (:foo :bar) 42}, {(:foo :baz) 99} }
The idea is to map the key :foo to the (recursive) result of pathify on :foo's value."
(println (str "pathify with: " fs)))

;; TODO: very inefficient due to recursive uniq call:
;; instead, sort first and then remove (adjacent) dups.
;; most know how to order references.
(defn uniq [vals]
  "remove duplicate values from vals."
  (let [val (first vals)]
    (if val
      (cons val
            (filter (fn [otherval] (not (= otherval val)))
                    (uniq (rest vals)))))))

(defn paths-to-value [map value path]
  (if (= map value) (list path)
      (if (= (type map) clojure.lang.Ref)
        (paths-to-value @map value path)
        (if (or (= (type map) clojure.lang.PersistentArrayMap)
                (= (type map) clojure.lang.PersistentHashMap))
          (mapcat (fn [key]
                    (paths-to-value (get map key) value (concat path (list key))))
                  (keys map))))))

(defn all-refs [input]
  (if input
    (if (= (type input) clojure.lang.Ref)
      (cons input
            (all-refs @input))
      (if (or (= (type input) clojure.lang.PersistentArrayMap)
              (= (type input) clojure.lang.PersistentHashMap))
        ;; TODO: fix bug here: vals resolves @'s
        (concat
         (mapcat (fn [key]
                   (let [val (get input key)]
                     (if (= (type input) clojure.lang.Ref)
                       (list val))))
                 input)
         (all-refs (vals input)))
        (if (and (seq? input)
                 (first input))
          (concat
           (all-refs (first input))
           (all-refs (rest input))))))))
  
(defn skeletize [input-val]
  (if (or (= (type input-val) clojure.lang.PersistentArrayMap)
          (= (type input-val) clojure.lang.PersistentHashMap))
    (zipmap (keys input-val)
            (map (fn [val]
                   (if (= (type val) clojure.lang.Ref)
                     :top
                     (if (or (= (type val) clojure.lang.PersistentArrayMap)
                             (= (type val) clojure.lang.PersistentHashMap))
                       (skeletize val)
                       val)))
                 (vals input-val)))
    input-val))

;; TODO s/map/input-map/
;; TODO: merge or distinguish from all-refs (above)
(defn get-refs [input-map]
  (uniq (all-refs input-map)))

;; TODO s/map/input-map/
(defn skels [input-map]
  "create map from reference to their skeletons."
  (let [refs (get-refs input-map)]
    (zipmap
     refs
     (map (fn [ref]
            (skeletize @ref))
          refs))))
          
(defn ref-skel-map [input-map]
  "create map from (ref=>skel) to paths to that ref."
  (let [refs (get-refs input-map)
        skels (skels input-map)]
    (zipmap
     (map (fn [ref]
            {:ref ref
             :skel (get skels ref)})
          refs)
     (map (fn [eachref]
            (paths-to-value input-map eachref nil))
          refs))))

(defn ser-db [input-map]
  (let [refs (get-refs input-map)
        skels (skels input-map)]
    (ref-skel-map input-map)))

;; (((:a :c) (:b :c) (:d))
;;  ((:a) (:b))
;;  nil)
;;     =>
;; {((:a :c) (:b :c) (:d)) => 2
;;  ((:a)    (:b))         => 1
;;  nil                    => 0
;; }
(defn max-lengths [serialization]
  (let [keys (keys serialization)]
    (zipmap
     keys
     (map (fn [paths]
            (if (nil? paths) 0
                (apply max (map (fn [path] (if (nil? path) 0 (.size path))) paths))))
          keys))))

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
     {nil (skeletize input-map)}
     (zipmap
      (vals rsk)
      sk))))     

(defn ser-intermed-2 [input-map]
  (let [top-level (skeletize input-map)
        rsk (ref-skel-map input-map)
        sk (map (fn [ref-skel]
                  (:skel ref-skel))
                (keys rsk))]
    (merge
     (skeletize input-map)
     {:ref-paths (vals rsk)
      :ref-vals sk})))

;(defn deser-1 [pathset value]
;  "for all paths in pathset, set them to the value."
;  {}
;  )

;(defn deser-r [serialized]
;  (if (first serialized)
;    (merge
;     (deser-1 (first serialized))
;     (deser-r (rest serialized)))))

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
;; value :PH).
;;
;; Note that (deserialize) should be able to cope with
;; both lists and arrays (i.e. just assume a sequence).
(defn deserialize [serialized]
  (let [base (second (first serialized))]
    (apply merge
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
             all))))

(defn serialize [input-map]
  (let [ser (ser-intermed input-map)]
    ;; ser is a intermediate (but fully-serialized) representation
    ;; as a map:
    ;; { path1 => value1
    ;;   path2 => value2
    ;;   nil   => skeleton}
    ;;
    ;; The skeleton immediately above is the input map but with the
    ;; dummy placeholder value :PH substituted for each occurance of a
    ;; reference in the input map.
    (sort-shortest-path-ascending-r ser (sort-by-max-lengths ser))))

(defn copy [map]
  (deserialize (serialize map)))

(defn print [map]
  "print a map in a user-friendly way that shows references as bracketed indexes e.g. [1] for readability, instead of e.g. #<Ref@7d582674>"
  "writeme")
