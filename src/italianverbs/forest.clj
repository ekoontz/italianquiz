(ns italianverbs.forest
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:use [clojure.set]
        [clojure.stacktrace]
        [italianverbs.generate]
        [italianverbs.grammar]
        [italianverbs.lexicon]
        [italianverbs.morphology :only (fo)]
        [italianverbs.ug])
  (:require
   [clojure.core :as core]
   [clojure.tools.logging :as log]

   [italianverbs.lev :as lev]
   [italianverbs.unify :refer :all]
   [italianverbs.unify :as unify]
   [italianverbs.config :as config]
   [italianverbs.html :as html]
   [italianverbs.search :as search]
   [clojure.string :as string]))

(defn choose-at-random [set & [distrib]]
  "choose one from amongst set using a probability distribution."
  (if (nil? distrib)
    (first (shuffle set))
    (first (shuffle set))))

;(def parents (list hh10 hh21))
;(def parents (set (list 'hh10 'hh21)))

;(def parents (set (list 'cc10)))
;(def lex (set (list 'io 'tu 'dormire)))

(def parents (set (list (unify cc10
                               {:synsem {:infl :present
                                         :sem {:tense :present}}}))))

(def lex (union (it "il") (it "cane") (it "i")
                ;(it "io") (it "tu")
; (it "lui") (it "lei")
 (it "dormire") (it "sognare")))
;(def lex (union (it "io") (it "dormire")))

(defn in? [member of-set]
  (not (empty? (intersection (set (list member)) of-set))))

(defn one-tree [set]
  (let [choice (choose-at-random set)]
    (cond (in? choice parents)
          {:sch choice
           :h (one-tree set)
           :c (one-tree set)}
          true
          choice)))

(defn choose-head [parent set]
  {:h (choose-at-random set)})

(defn all-trees [tree-with-head set]
  (if (not (empty? set))
    (lazy-seq
     (cons
      (unifyc tree-with-head
              {:c (first set)})
      (all-trees tree-with-head (rest set))))))

(defn all-heads-and-parents [parent set]
  "generate all possible head-parent combinations."
  (lazy-seq
   (cons
    (unifyc parent
            {:h (first set)})
    (all-heads-and-parents parent set))))

(def upl (union parents lex))

(defn forest [set]
  "generate a lazy sequence of trees"
  (lazy-seq
   (cons
    (one-tree set)
    (forest set))))

(def pl (union parents lex))

(defn choose-at-random-with-depth [parents lex depth]
  (let [rand (rand-int 10)]
    (cond (= depth 0) ;; depth 0: branch with 80% probability
          (cond (> rand 0)
                (first (shuffle parents))
                true
                (first (shuffle lex)))

          (= depth 1) ;; depth 1: branch with 40% probabilty
          (cond (> rand 5)
                (first (shuffle parents))
                true
                (first (shuffle lex)))

          (= depth 2) ;; depth 2: branch with 20% probability
          (cond (> rand 7)
                (first (shuffle parents))
                true
                (first (shuffle lex)))

          true ;; greater depth: branch with 10% probability
          (cond (> rand 8)
                (first (shuffle parents))
                true
                (first (shuffle lex))))))

(defn h1d1 [parents lex & [depth head-spec]]
  "head-first,depth-first generation"
  (let [depth (if depth depth 0)
        head-spec (if head-spec head-spec :top)
        choice (choose-at-random-with-depth parents lex depth)]
    (log/debug (str "h1d1 at depth: " depth))
    (cond (in? choice parents)
          ;; this is a sub-tree: generate its head.
          (let [chosen-phrase choice
                debug (log/debug (str "h1d1: phrase: " (get-in chosen-phrase '(:comment))))
                head (h1d1 parents lex (+ depth 1) head-spec)
                debug (log/debug (str "h1d1: head: " (fo head)))
                head (if (fail? head) :fail head)
                phrase-with-head
                (unifyc chosen-phrase
                        {:head head})
                debug (log/debug (str "h1d1: phrase-with-head: " (fo phrase-with-head)))
                comp (if (fail? phrase-with-head)
                       :fail
                       (h1d1 parents lex 0 (get-in phrase-with-head '(:comp))))]
            (if (fail? comp)
              :fail
              (unifyc phrase-with-head
                      {:comp comp})))
          true
          ;; not a subtree: done.
          (unifyc choice head-spec))))

(defn do-a-bunch []
  (take 5 (forest (union parents lex))))

;(h1d1 parents lex 0)
;(fo (remove fail? (take 100 (repeatedly #(h1d1 parents lex 0)))))

;(fo (remove (fn [x] (= :notfound (get-in x '(:head) :notfound)))
;     (take 100 (repeatedly #(h1d1 parents lex 0))))))))
