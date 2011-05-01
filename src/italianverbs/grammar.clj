;; NO RESTARTING OF RING REQUIRED FOR CHANGES TO THIS FILE. (must reload browser 2x though).
(ns italianverbs.grammar
  (:use [somnium.congomongo])
  (:require
   [italianverbs.morphology :as morph]
   [clojure.string :as string]))

(defn right [head comp]
  {:english (string/join " "
                         (list 
                          (get comp :english)
                          (get head :english)))
   :italian (string/join " "
                         (list 
                          (get comp :italian)
                          (get head :italian)))
   :children (list comp head)})

(defn left [head comp]
  {:english (string/join " "
                         (list 
                          (get head :english)
                          (get comp :english)))

   :italian (string/join " "
                         (list 
                          (get head :italian)
                          (get comp :italian)))
   :children (list head comp)})

(defn combine-error [head comp]
  {:cat :error
   :notes "no function found to combine head and comp."
   :children (list head comp)})

;; head-position is 'left or 'right.
(defn combine [head comp fn]
  (merge
   (apply fn (list head comp))
   {:head head
    :comp comp
    :def (get comp :def)

    }
   ;; following is all features copied from complement to parent..
   (if (get head :det)
     {:det (get head :det)})

   ;; following is all features copied from complement to parent..
   (if (get comp :def)
     {:def (get comp :def)})))



;; TODO: use (morph/get-head) instead.
(defn gramhead [sign]
  (if (get sign :head)
    (get sign :head)
    sign))

(defn unify-np [head arg]
  (if (and
       (= (get (gramhead head) :gender)
          (get (gramhead arg) :gender))
       (= (get (gramhead head) :number)
          (get (gramhead arg) :number)))
    {
     :head head
     }
    {
     :cat :fail
     ;; TODO: rewrite as (defn diagnosis [head arg])
     :note (str (get head :gender) " != " (get arg :gender)
                " or "
                (get head :number) " != " (get arg :number))
     }))

(defn noun-fn [head arg]  ;; e.g. "il libro"
  (merge
   (unify-np head arg)
   {:english
    (morph/conjugate-en head arg)
    :italian
    (string/join " "
                 (list (get arg :italian)
                       (morph/conjugate-it head)))}))

(defn choose-lexeme [struct & [dummy]]
  "Choose a random lexeme from the set of lexemes
   that match search criteria.
   dummy: ignored for compatibility with gram/np"
  ;; do a query based on the given struct,
  ;; and choose a random element that satisfies the query.
  (let [results (fetch :lexicon :where struct)]
    (if (= (count results) 0)
      {:english "??" :italian "??"
       :cat :error :note (str "<tt>(choose-lexeme)</tt>: no results found. <p/>See <tt>:choose</tt> feature below for query.")
       :choose struct
       }
      (nth results (rand-int (count results))))))

"find a function which might really be a function, or might be a string that
 needs to be converted to a function whose name is that string."
(defn find-fn [fn]
  (cond
   (nil? fn)
   {:cat :error :note
    (str "function is null")}
   (string? fn)
   (symbol fn)
   true fn))

  
(defn np [ & [fs determiner]]
  "'fs' puts pre-conditions on noun (head of the np)"
  (let [chosen-determiner determiner
        noun (choose-lexeme (merge fs {:cat :noun
                                       }))
        determiner-search
        (if (not (= (get noun :det) nil))
          (merge
           (get noun :det)
           {:cat :det
            :gender (get noun :gender)
            :number (get noun :number)}))
        determiner (if (not (= (get noun :det) nil))
                     (choose-lexeme determiner-search))]
    (if determiner
      (merge 
       {:choose-comp determiner-search}
       (combine noun determiner right)
       {:italian (morph/italian-article determiner noun)})
      noun)))

(def np-with-common-noun-and-definite-pronoun
  (fn [candidate]
    (and (not (= (get candidate :pronoun) true)) ;; e.g. "noi (us)"
         (not (= (get candidate :det) nil)) ;; e.g. "Italia (Italy)"
         (= (get candidate :def) "def"))))

(defn np-with-post-conditions [ & [head-conditions post-conditions keep-trying]]
  ;; forgot how to pass default params, so doing this (let) instead.
  (let [default-limit 10
        keep-trying (if (not (= keep-trying nil))
                      keep-trying
                      default-limit)]
    (let [candidate (np head-conditions)]
      (if (apply post-conditions
                 (list candidate))
        candidate
        (if (= keep-trying 0)
          {:cat :error
           :note (str "gave up trying to generate after " default-limit " attempts.")
           :notefs candidate
           }
          (np-with-post-conditions head-conditions post-conditions (- keep-trying 1)))))))

(defn verb-sv [head comp]  ;; e.g. "i [sleep]","he [writes a book]"
  (cond
   ;; unfortunately we have to check
   ;; for either the :-form or the quoted-string below:
   (or (= (get (morph/get-head comp) :cat) :noun)
       (= (get (morph/get-head comp) :cat) "noun")
       (= (get (morph/get-head comp) :cat) :pronoun)
       (= (get (morph/get-head comp) :cat) "pronoun"))

   {:fn "verb-sv"
    :english
    (string/join " "
		 (list 
		  (get comp :english)
		  (morph/conjugate-english-verb (morph/get-head head) comp)
		  (get (get head :comp) :english)))
    :italian
    (string/join " "
		 (list
		  (get comp :italian)
		  (morph/conjugate-italian-verb head comp)
          (get (get head :comp) :italian)))}
   (= (get (morph/get-head comp) :cat) "prep")
   {:fn "verb-sv"
    :head head
    :comp comp
    :italian
    (str
     (get head :italian)
     " "
     (get comp :italian))
     :english
    (str
     (get head :english)
     " "
     (get comp :english))}
   true
   {:cat :error
    :note (str
           "<tt><i>error: verb does not know what to do with this argument.</i>(<b>verb-sv</b> "
           "'" (get head :english) "','" (get comp :english) "'"
           ")</i>."
           "<p>get-head comp :cat=" (get (morph/get-head comp) :cat) "</p>"
           "</tt>")}))

(defn pp [ & [fs obj]]
  "generate a prepositional phrase.
   fs adds restrictions on prep.
   obj is simply an object for the preposition."
  (let [prep (choose-lexeme (merge fs {:cat :prep}))]
    (let [np (if obj obj
                 (np (get prep :obj)))]
      (merge 
       {:choose-head prep}
       {:choose-comp np}
       {:given-an-obj (if obj true false)}
       (combine prep np left)
       {:italian (morph/conjugate-italian-prep prep np)}))))

(defn sv [head comp]
  (merge
   (right head comp)
   {:english (string/join " "
                          (list (get comp :english)
                                (morph/conjugate-english-verb head comp)))
    :italian (string/join " "
                          (list (get comp :italian)
                                (morph/conjugate-italian-verb head comp)))}))

(defn vo [head comp]
  (left head comp))

(defn vp-pp [head comp]
  (left head comp))

(defn det-n [head comp]
  (right head comp))

(defn choose-iobject [verb]
  (pp (get verb :iobj)))

(defn vp [ & [fs]]
  (let [verb-fs (merge
                 fs
                 {:cat :verb
                  :infl :infinitive})
        verb (nth (fetch :lexicon :where verb-fs)
                  (rand-int (count (fetch :lexicon :where verb-fs))))
        object
        (cond
         (= (get (get verb :obj) :cat) "noun")
         (np (merge {:case {:$ne :nom}}
                    (get verb :obj)))
         (= (get (get verb :obj) :cat) "verb")
         (vp (get verb :obj))
         true nil)
        verb-with-object (if object
                           (combine verb object vo)
                           verb)
        verb-with-iobject (if (get verb :iobj)
                            (combine verb-with-object (choose-iobject verb) vo)
                            verb-with-object)]
    verb-with-iobject))

(defn vp-with-adjunct-pp [ & [fs]]
  (let [vp (vp fs)]
    (combine vp
             (pp (get (morph/get-head vp) :adjunct))
             vp-pp)))

(defn sentence []
  (let [vp (vp-with-adjunct-pp)]
    (let [subject
          (np
           (merge
            {:case {:$ne :acc}}
            (get (morph/get-root-head vp) :subj)))]
      (if vp
        (combine vp subject sv)
        {:cat :error
         :error "vp-with-adjunct-pp returned null."}))))

