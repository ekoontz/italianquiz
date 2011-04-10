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
    :comp comp}))

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


(defn prep-fn [head arg]  ;; e.g. "[in Italia]","[a lavorare]"
  {:head head
   :comp arg
   :english
   (string/join " "
		(list 
		 (get head :english)
		 (get arg :english)))
   
   :italian
   (string/join " "
		(list 
		 (get head :italian)
		 (get arg :italian)))})

(defn noun-fn [head arg]  ;; e.g. "il libro"
  (merge
   (unify-np head arg)
   {:english
    (morph/conjugate-en head arg)
    :italian
    (string/join " "
                 (list (get arg :italian)
                       (morph/conjugate-it head)))}))

;; following 3 fns should probably be in generate.clj.
(defn np-no-det [noun]
  ;; for nouns that do not take determiners.
  nil)

(defn choose-lexeme [struct]
  ;; do a query based on the given struct,
  ;; and choose a random element that satisfies the query.
  (let [results (fetch :lexicon :where struct)]
    (if (= (count results) 0)
      {:english "??" :italian "??"
       :cat :error :note (str "(choose-lexeme): no results found for (see :choose feature).")
       :choose struct
       }
      (nth results (rand-int (count results))))))

(defn np-det [noun]
  (choose-lexeme
   {:gender (get noun :gender)
    :number (get noun :number)
    :cat :det}))

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

(defn np [ & [fs]]
  (let [noun (choose-lexeme (merge fs {:cat :noun}))
        determiner (if (get noun :comp)
                     (choose-lexeme
                      (merge {:number (get noun :number)
                              :gender (get noun :gender)}
                             (get noun :comp))))]
    (if determiner
      (combine noun determiner right)
      noun)))

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

(defn pp [ & [fs fs-obj]]
  "generate a prepositional phrase.
   fs adds restrictions on prep.
   fs-obj adds restrictions on prepositions's complement."
  (let [prep (choose-lexeme (merge fs {:cat :prep}))
        ;; (eventually) use _genfn to generate an argument (np) given _prep.
        genfn (get prep :genfn)]
    (let [np (np (merge (get prep :obj)
                        fs-obj))]
      (combine prep np left))))

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

(defn generate []
;  (np))
  (sentence))
