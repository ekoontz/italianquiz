(ns italianverbs.lexiconfn
  (:use [hiccup core page-helpers]
        [somnium.congomongo])
  (:require
   [clojure.contrib.string :as stringc]
   [italianverbs.morphology :as morph]))

; global initializations go here, i guess..
(mongo! :db "mydb")
(make-connection "mydb" :host "localhost")

(defn italian [lexeme]
  (get (nth lexeme 1) :lexicon))

(defn synsem [lexeme]
  (nth lexeme 1))

(defn english [lexeme]
  (get (nth lexeme 1) :english))

;; CRUD-like functions:
(defn add-fs [fs]
  (let [function-to-symbol fs]
    (insert! :lexicon fs)
    fs))

;; italian and english are strings, featuremap is a map of key->values.
(defn add [italian english & [featuremap types result]]
  (if (first types)
    (add
     italian
     english
     featuremap
     (rest types)
     (merge (first types) result))
    (let [featuremap
          (merge featuremap
                 (merge result
                        (if english
                          (assoc {} :italian italian :english english)
                          (assoc {} :italian italian))))]
      (add-fs featuremap))))

;; _italian is a string; _types is a list of symbols (each of which is a map of key-values);
;; _result is an accumulator which contains the merge of all of the maps
;; in _types.
;; no _english param needed; _result should be assumed to contain a :root key-value.
(defn add-infl [italian & [types result]]
  (if (first types)
    (add-infl
     italian
     (rest types)
     (merge (first types) result))
    (add italian nil result)))

(def firstp
  {:person :1st})
(def secondp
  {:person :2nd})
(def thirdp
  {:person :3rd})
(def sing
  {:number :singular})
(def plural
  {:number :plural})
(def present
  {:cat :verb :infl :present})

(defn add-with-pass-pross [italian-infinitive italian-pass-pross english-infinitive english-past avere-o-assere & [ fs present-indicative-list ]  ]
  "add an infinitive form of a verb and the participio passato form. _fs_ contains additional lexical info." 
  (let [inf
        (add italian-infinitive english-infinitive
             (merge
              {:cat :verb
               :infl :infinitive}
              fs))
        first-sing (nth present-indicative-list 0)
        second-sing (nth present-indicative-list 1)
        third-sing (nth present-indicative-list 2)
        first-plur (nth present-indicative-list 3)
        second-plur (nth present-indicative-list 4)
        third-plur (nth present-indicative-list 5)]
    (add-infl
     (if first-sing first-sing
       (morph/conjugate-italian-verb-regular
        inf
        (merge firstp sing present)))
     (list (merge firstp sing present))
     {:root inf})

    (add-infl
     (if second-sing second-sing
         (morph/conjugate-italian-verb-regular
          inf
          (merge secondp sing present)))
     (list (merge secondp sing present))
     {:root inf})

    (add-infl
     (if third-sing third-sing
         (morph/conjugate-italian-verb-regular
          inf
          (merge thirdp sing present)))
     (list (merge thirdp sing present))
     {:root inf})
    
    (add-infl
     (if first-plur first-plur
         (morph/conjugate-italian-verb-regular
          inf
          (merge firstp plural present)))
     (list (merge firstp plural present))
     {:root inf})

    (add-infl
     (if second-plur second-plur
         (morph/conjugate-italian-verb-regular
          inf
          (merge secondp plural present)))
     (list (merge secondp plural present))
     {:root inf})

    (add-infl
     (if third-plur third-plur
         (morph/conjugate-italian-verb-regular
          inf
          (merge thirdp plural present)))
     (list (merge thirdp plural present))
     {:root inf})

    (add italian-pass-pross english-past
         {:cat :verb
          :root inf
          :infl :passato-prossimo
          :aux avere-o-assere})))


(defn italian-pluralize [singular gender]
  (cond
   (= gender :masc)
   (stringc/replace-re #"([oe])$" "i" singular)
   (= gender :fem)
   (stringc/replace-re #"([a])$" "e" singular)
   true (str "error: gender: " gender  " unknown.")))

(defn english-pluralize [singular]
  (str (stringc/replace-re #"([sxz])$" "$1e" singular) "s"))

(defn add-plural [fs types & [italian-plural english-plural]]
  (add
   (if italian-plural italian-plural
       (italian-pluralize (get fs :italian)
                          (get fs :gender)))
   (if english-plural english-plural
     (english-pluralize (get fs :english)))
   (merge
    fs 
    {:number :plural})
   types))

(defn add-with-plural [italian english featuremap types & [italian-plural english-plural]]
  (add-plural
   (add italian english
        (merge featuremap
               {:number :singular})
               types)
   types
   italian-plural english-plural))

;; _italian and _english are strings; _types is a list of symbols (each of which is a map of key-values);
;; _result is an accumulator which is the merge of all of the maps in _types.
;; Key-values in earlier types have precedence over those in later types
;; (i.e. the later key-value pair do NOT override original value for that key).
(defn add-as [italian english & [types result]]
  (if (first types)
    (add-as
     italian
     english
     (rest types)
     (merge (first types) result))
    (add italian nil (merge {:english english} result))))

(defn add-infl-reg [infinitive & [passato-prossimo past aux]]
  (let [firstp {:person :1st}
        secondp {:person :2nd}
        thirdp {:person :3rd}
        sing {:number :singular}
        plural {:number :plural}
        present {:cat :verb :infl :present}]
    (add-infl (morph/conjugate-italian-verb infinitive (merge firstp sing))
              (list firstp sing present
                    {:root infinitive}))

    (add-infl (morph/conjugate-italian-verb infinitive (merge secondp sing))
              (list secondp sing present
                    {:root infinitive}))

    (add-infl (morph/conjugate-italian-verb infinitive (merge thirdp sing))
              (list thirdp sing present
                    {:root infinitive}))

    (add-infl (morph/conjugate-italian-verb infinitive (merge firstp plural))
              (list firstp plural present
                    {:root infinitive}))

    (add-infl (morph/conjugate-italian-verb infinitive (merge secondp plural))
              (list secondp plural present
                    {:root infinitive}))

    (add-infl (morph/conjugate-italian-verb infinitive (merge thirdp plural))
              (list thirdp plural present
                    {:root infinitive}))

    (if (and passato-prossimo past aux)
      (add passato-prossimo past
           {:cat :verb
            :root infinitive
            :infl :passato-prossimo
            :aux aux}))
    ))


(defn lookup [italian & [where ]]
  (fetch-one :lexicon :where (merge where {:italian italian})))

(defn clear []
  (destroy! :lexicon {}))

(defn regular-future [infinitive]
  "_infinitive_ should be a lexical entry."
  (add
   (morph/conjugate-future-italian
    infinitive
    {:person :1st
     :number :singular})
   (str "(i) will " (get (morph/remove-to infinitive) :remove-to))
   {:cat :verb
    :subj {:person :1st
           :number :singular}
    :infl :futuro-semplice
    :root infinitive})

  (add
   (morph/conjugate-future-italian
    infinitive
    {:person :2nd
     :number :singular})
   (str "(you) will " (get (morph/remove-to infinitive) :remove-to))
   {:cat :verb
    :subj {:person :2nd
           :number :singular}
    :infl :futuro-semplice
    :root infinitive})
  
  (add
   (morph/conjugate-future-italian
    infinitive
    {:person :1st
     :number :singular})
   (str "(he/she) will " (get (morph/remove-to infinitive) :remove-to))
   {:cat :verb
    :subj {:person :3rd
           :number :singular}
    :infl :futuro-semplice
    :root infinitive})

  (add
   (morph/conjugate-future-italian
    infinitive
    {:person :1st
     :number :plural})
   (str "(we) will " (get (morph/remove-to infinitive) :remove-to))
   {:cat :verb
    :subj {:person :1st
           :number :plural}
    :infl :futuro-semplice
    :root infinitive})


  (add
   (morph/conjugate-future-italian
    infinitive
    {:person :1st
     :number :singular})
   (str "(you all) will " (get (morph/remove-to infinitive) :remove-to))
   {:cat :verb
    :subj {:person :2nd
           :number :plural}
    :infl :futuro-semplice
    :root infinitive})

  
  (add
   (morph/conjugate-future-italian
    infinitive
    {:person :3rd
     :number :plural})
   (str "(they) will " (get (morph/remove-to infinitive) :remove-to))
   {:cat :verb
    :subj {:person :3rd
           :number :plural}
    :infl :futuro-semplice
    :root infinitive}))



  