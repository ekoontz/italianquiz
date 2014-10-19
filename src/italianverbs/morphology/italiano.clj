(ns italianverbs.morphology.italiano
  (:refer-clojure :exclude [get-in])
  (:use [italianverbs.unify :only (ref? get-in fail?)])
  (:require
   [clojure.core :as core]
   [clojure.tools.logging :as log]))

(require '[italianverbs.morphology :refer :all])

(defn suffix-of [word]
  "compute the final character given a lexical entry and agreement info in :agr."
  (let [suffix (cond

                (and (= (get-in word '(:obj-agr :gender)) :fem)
                     (= (get-in word '(:obj-agr :number)) :sing))
                "a"

                (and (= (get-in word '(:obj-agr :gender)) :fem)
                     (= (get-in word '(:obj-agr :number)) :plur))
                "e"

                (= (get-in word '(:obj-agr :number)) :plur)
                "i"

                (and (= (get-in word '(:agr :gender)) :fem)
                     (= (get-in word '(:agr :number)) :sing)
                     (= (get-in word '(:essere)) true))
                "a"

                (and (= (get-in word '(:agr :gender)) :fem)
                     (= (get-in word '(:agr :number)) :plur)
                     (= (get-in word '(:essere)) true))
                "e"

                (and (= (get-in word '(:agr :number)) :plur)
                     (= (get-in word '(:essere)) true))
                "i"

                true
                "o"

                )]
    suffix))





(declare get-italian)

(defn stem-per-futuro [infinitive drop-e]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (cond
   (re-find #"giare$" infinitive)
   (string/replace infinitive #"giare$" "ger")

   (re-find #"ciare$" infinitive)
   (string/replace infinitive #"ciare$" "cer")

   (and
    (= true drop-e)
    (re-find #"are$" infinitive))
   (string/replace infinitive #"are$" "r")

   (re-find #"are$" infinitive)
   (string/replace infinitive #"are$" "er")

   (and
    (= true drop-e)
    (re-find #"ere$" infinitive))
   (string/replace infinitive #"ere$" "r")

   (re-find #"ere$" infinitive)
   (string/replace infinitive #"ere$" "er")

   (re-find #"ire$" infinitive)
   (string/replace infinitive #"ire$" "ir")

   true
   infinitive))

(defn stem-per-imperfetto [infinitive]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (cond
   (re-find #"re$" infinitive)
   (string/replace infinitive #"re$" "")
   true
   infinitive))

(defn analyze-italian-1 [word]
  (let [person (get-in word '(:agr :person))
        number (get-in word '(:agr :number))]
    {:person person
     :number number
     :infinitive?    (and (= :infinitive (get-in word '(:infl)))
                          (string? (get-in word '(:infinitive))))

     :irregular-futuro?    (and
                            (= (get-in word '(:infl)) :futuro)
                            (map? (get-in word '(:irregular :futuro))))

     :regular-futuro?    (and (= (get-in word '(:infl)) :futuro)
                              (get-in word '(:infinitive)))

     :regular-imperfetto?    (and (= (get-in word '(:infl)) :imperfetto)
                                  (get-in word '(:infinitive)))

     :irregular-past?    (and
                          (= :past (get-in word '(:infl)))
                          (string? (get-in word '(:irregular :past))))

     ;;nei: not enough information to conjugate.
     :past-irregular-essere-type-nei
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:irregular :passato))
          (get-in word '(:essere) true)
          (or (= :notfound (get-in word '(:agr :number) :notfound))
              (= :top (get-in word '(:agr :number)))))

     ;;nei: not enough information to conjugate.
     :past-esseri-but-nei?
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:essere) true)
          (or (= :notfound (get-in word '(:agr :number) :notfound))
              (= :top (get-in word '(:agr :number)))))

   :irregular-passato?
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:irregular :passato)))

   :regular-passato
     (= :past (get-in word '(:infl)))

     :present
     (= (get-in word '(:infl)) :present)}))

(defn get-italian-1 [word]
  (if (seq? word)
    (map get-italian-1
         word)
  (let [analysis (analyze-italian-1 word)
        person (get-in word '(:agr :person))
        number (get-in word '(:agr :number))
        info (log/debug "get-italian-1: input word: " word)
        ]

    (if (and false get-in word '(:a))
      (do (log/info (string/str "a? " (get-in word '(:a))))
          (log/info (str "b? " (get-in word '(:b))))
          (log/info (str "analysis: " analysis))))

    (log/debug (str "word's a is a string? " (get-in word '(:a)) " => " (string? (get-in word '(:a)))))
    (log/debug (str "word's b is a map? " (get-in word '(:b)) " => " (map? (get-in word '(:b)))))

    (log/debug (str "word's a italian is a string? " (get-in word '(:a :italian)) " => " (string? (get-in word '(:a :italian)))))


    ;; throw exception if contradictory facts are found:
;    (if (= (get-in word '(:a :initial) false))
;      (throw (Exception. (str ":a's initial is false: (:a should always be initial=true)."))))

    (cond

     (= word :top) ".."

     (ref? word)
     (get-italian-1 @word)

     ;; TODO: this is a special case that should be handled below instead
     ;; of forcing every input to go through this check.
     (= word {:initial false})
     ".."
     (= word {:initial true})
     ".."

     (and (string? (get-in word '(:a)))
          (string? (get-in word '(:b))))
     (get-italian (get-in word '(:a))
                  (get-in word '(:b)))

     (and (string? (get-in word '(:a)))
          (map? (get-in word '(:b))))
     (get-italian (get-in word '(:a))
                  (get-in word '(:b)))

     (and (map? (get-in word '(:a)))
          (map? (get-in word '(:b))))
     (get-italian
      (get-in word '(:a))
      (get-in word '(:b)))

     ;; TODO: this rule is pre-empting all of the following rules
     ;; that look in :a and :b. Either remove those following rules
     ;; if they are redundant and not needed, or move this general rule
     ;; below the following rules.
     (and (not (= :none (get-in word '(:a) :none)))
          (not (= :none (get-in word '(:b) :none))))
     (get-italian (get-in word '(:a))
                  (get-in word '(:b)))

     (and
      (string? (get-in word '(:a :italian)))
      (string? (get-in word '(:b :infinitive)))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      )
     (str (strip (get-in word '(:a :italian)))
          " "
          (strip (get-in word '(:b :infinitive))))

     (and
      (string? (get-in word '(:a)))
      (string? (get-in word '(:b :infinitive)))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      )
     (str (strip (get-in word '(:a)))
          " "
          (strip (get-in word '(:b :infinitive))))


     (and
      (string? (get-in word '(:a :infinitive)))
      (get-in word '(:a :infinitive))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      (= (get-in word '(:a :infl)) :top))
     (strip (str (get-in word '(:a :infinitive))
                 " " (get-italian-1 (get-in word '(:b)))))

     ;; handle lexical exceptions (plural feminine adjectives):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:cat)) :adjective)
      (string? (get-in word '(:irregular :fem :plur))))
     (get-in word '(:irregular :fem :plur))

     ;; handle lexical exceptions (plural feminine adjectives):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:cat)) :adjective)
      (string? (get-in word '(:irregular :fem :plur))))
     (get-in word '(:irregular :fem :plur))

     ;; handle lexical exceptions (plural masculine adjectives):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:cat)) :adjective)
      (string? (get-in word '(:irregular :masc :plur))))
     (get-in word '(:irregular :masc :plur))

     (and
      (or (= (get-in word '(:agr :gender)) :masc)
          (= (get-in word '(:agr :gender)) :top))
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:italian))
                     #"[eo]$" "i") ;; nero => neri

     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:italian))
                     #"[eo]$" "e") ;; nero => nere

     ;; handle lexical exceptions (plural nouns):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :noun)
      (string? (get-in word '(:irregular :plur))))
     (get-in word '(:irregular :plur))

     ;; regular masculine nouns
     (and
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat) :noun))
      (get-in word '(:italian)))
     (string/replace (get-in word '(:italian))
                     #"[eo]$" "i") ;; dottore => dottori; medico => medici

     ;; regular feminine nouns
     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat) :noun))
      (get-in word '(:italian)))
     (string/replace (get-in word '(:italian))
                     #"[a]$" "e") ;; donna => donne

     ;; TODO: move this down to other adjectives.
     ;; this was moved up here to avoid
     ;; another rule from matching it.
     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:italian))
                     #"[eo]$" "e") ;; nero => nere

     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :sing)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:italian))
                     #"[eo]$" "a") ;; nero => nera
     (and
      (string? (get-in word '(:italian)))
      (= :top (get-in word '(:agr :sing) :top)))
     (str (get-in word '(:italian)))

     (= (get-in word '(:a)) :top)
     (str
      ".." " " (get-italian-1 (get-in word '(:b))))

     (and
      (= (get-in word '(:b)) :top)
      (string? (get-italian-1 (get-in word '(:a)))))
     (str
      (get-italian-1 (get-in word '(:a)))
      " " "..")

     (and
      (= (get-in word '(:b)) :top)
      (string? (get-in word '(:a :italian))))
     (str
      (get-italian-1 (get-in word '(:a :italian)))
      " " "..")

     (and (= :infinitive (get-in word '(:infl)))
          (string? (get-in word '(:infinitive))))
     (get-in word '(:infinitive))
     
     (and (= (get-in word '(:infl)) :futuro)
          (get-in word '(:futuro-stem)))
     (let [stem (get-in word '(:futuro-stem))]
       (cond
        (and (= person :1st) (= number :sing))
        (str stem "ò")

        (and (= person :2nd) (= number :sing))
        (str stem "ai")

        (and (= person :3rd) (= number :sing))
        (str stem "à")

        (and (= person :1st) (= number :plur))
        (str stem "emo")

        (and (= person :2nd) (= number :plur))
        (str stem "ete")

        (and (= person :3rd) (= number :plur))
        (str stem "anno")))

     (and
      (= (get-in word '(:infl)) :futuro)
      (map? (get-in word '(:irregular :futuro))))
     (let [infinitive (get-in word '(:infinitive))
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]
       (cond
        (and (= person :1st) (= number :sing))
        (get-in word '(:irregular :futuro :1sing))
        (and (= person :2nd) (= number :sing))
        (get-in word '(:irregular :futuro :2sing))
        (and (= person :3rd) (= number :sing))
        (get-in word '(:irregular :futuro :3sing))
        (and (= person :1st) (= number :plur))
        (get-in word '(:irregular :futuro :1plur))
        (and (= person :2nd) (= number :plur))
        (get-in word '(:irregular :futuro :2plur))
        (and (= person :3rd) (= number :plur))
        (get-in word '(:irregular :futuro :3plur))


        (and (= (get-in word '(:infl)) :futuro)
             (string? (get-in word '(:infinitive))))
        (str (get-in word '(:infinitive)) " (futuro)")

        true ;; failthrough: should usually not get here:
        ;; TODO: describe when it might be ok, i.e. why log/warn not log/error.
        (do (log/warn (str "get-italian-1 could not match: " word))
        word)))

     ;; irregular inflection of conditional
     (and (= (get-in word '(:infl)) :conditional)
          (get-in word '(:futuro-stem)))
     (let [stem (get-in word '(:futuro-stem))]
       (cond
        (and (= person :1st) (= number :sing))
        (str stem "ei")

        (and (= person :2nd) (= number :sing))
        (str stem "esti")

        (and (= person :3rd) (= number :sing))
        (str stem "ebbe")

        (and (= person :1st) (= number :plur))
        (str stem "emmo")

        (and (= person :2nd) (= number :plur))
        (str stem "este")

        (and (= person :3rd) (= number :plur))
        (str stem "ebbero")))


     ;; regular inflection of conditional
     (and (= (get-in word '(:infl)) :conditional)
          (get-in word '(:infinitive)))

     (let [infinitive (get-in word '(:infinitive))
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))
           drop-e (get-in word '(:italian :drop-e) false)
           stem (stem-per-futuro infinitive drop-e)]

       (cond
        (and (= person :1st) (= number :sing))
        (str stem "ei")

        (and (= person :2nd) (= number :sing))
        (str stem "esti")

        (and (= person :3rd) (= number :sing))
        (str stem "ebbe")

        (and (= person :1st) (= number :plur))
        (str stem "emmo")

        (and (= person :2nd) (= number :plur))
        (str stem "este")

        (and (= person :3rd) (= number :plur))
        (str stem "ebbero")

        :else
        (get-in word '(:infinitive))))





     ;; regular inflection of futuro.
     (and (= (get-in word '(:infl)) :futuro)
          (get-in word '(:infinitive)))


     (let [infinitive (get-in word '(:infinitive))
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))
           drop-e (get-in word '(:italian :drop-e) false)
           stem (stem-per-futuro infinitive drop-e)]


       (cond
        (and (= person :1st) (= number :sing))
        (str stem "ò")

        (and (= person :2nd) (= number :sing))
        (str stem "ai")

        (and (= person :3rd) (= number :sing))
        (str stem "à")

        (and (= person :1st) (= number :plur))
        (str stem "emo")

        (and (= person :2nd) (= number :plur))
        (str stem "ete")

        (and (= person :3rd) (= number :plur))
        (str stem "anno")

        :else
        (get-in word '(:infinitive))))


     ;; irregular imperfetto sense:
     ;; 1) use irregular based on number and person.
     (and
      (= (get-in word '(:infl)) :imperfetto)
      (= :sing (get-in word '(:agr :number)))
      (= :1st (get-in word '(:agr :person)))
      (string? (get-in word '(:irregular :imperfetto :1sing))))
     (get-in word '(:irregular :imperfetto :1sing))

     (and
      (= (get-in word '(:infl)) :imperfetto)
      (= :sing (get-in word '(:agr :number)))
      (= :2nd (get-in word '(:agr :person)))
      (string? (get-in word '(:irregular :imperfetto :2sing))))
     (get-in word '(:irregular :imperfetto :2sing))

     (and
      (= (get-in word '(:infl)) :imperfetto)
      (= :sing (get-in word '(:agr :number)))
      (= :3rd (get-in word '(:agr :person)))
      (string? (get-in word '(:irregular :imperfetto :3sing))))
     (get-in word '(:irregular :imperfetto :3sing))

     (and
      (= (get-in word '(:infl)) :imperfetto)
      (= :plur (get-in word '(:agr :number)))
      (= :1st (get-in word '(:agr :person)))
      (string? (get-in word '(:irregular :imperfetto :1plur))))
     (get-in word '(:irregular :imperfetto :1plur))
     (and
      (= (get-in word '(:infl)) :imperfetto)
      (= :plur (get-in word '(:agr :number)))
      (= :2nd (get-in word '(:agr :person)))
      (string? (get-in word '(:irregular :imperfetto :2plur))))
     (get-in word '(:irregular :imperfetto :2plur))
     (and
      (= (get-in word '(:infl)) :imperfetto)
      (= :plur (get-in word '(:agr :number)))
      (= :3rd (get-in word '(:agr :person)))
      (string? (get-in word '(:irregular :imperfetto :3plur))))
     (get-in word '(:irregular :imperfetto :3plur))


     ;; regular imperfetto sense
     (and (= (get-in word '(:infl)) :imperfetto)
          (get-in word '(:infinitive)))
     (let [infinitive (get-in word '(:infinitive))
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))
           stem (stem-per-imperfetto infinitive)]
       (cond
        (and (= person :1st) (= number :sing))
        (str stem "vo")

        (and (= person :2nd) (= number :sing))
        (str stem "vi")

        (and (= person :3rd) (= number :sing))
        (str stem "va")

        (and (= person :1st) (= number :plur))
        (str stem "vamo")

        (and (= person :2nd) (= number :plur))
        (str stem "vate")

        (and (= person :3rd) (= number :plur))
        (str stem "vano")

        (string? infinitive)
        (str infinitive )

        :else
        (merge word
               {:error 1})))

     ;; TODO: remove this: :past is only for english (get-english-1), not italian.
     ;; italian uses :passato.
     (and
      (= :past (get-in word '(:infl)))
      (string? (get-in word '(:irregular :past))))
     (get-in word '(:irregular :past))

     (and
      (get-in word '(:a))
      (get-in word '(:b))
      true) (str
             (strip (get-italian-1 (get-in word '(:a)))) " "
             (strip (get-italian-1 (get-in word '(:b)))))

     ;; "fare [past]" + "bene" => "fatto bene"
     (and (= (get-in word '(:cat)) :verb)
          (= (get-in word '(:infl)) :past)
          (string? (get-in word '(:a :irregular :passato))))
     (str (get-in word '(:a :irregular :passato)) " "
          (get-italian-1 (get-in word '(:b))))

     ;; TODO: do not use brackets: if there's an error about there being
     ;; not enough information, throw an exception explicitly.
     ;; return the irregular form in square brackets, indicating that there's
     ;; not enough information to conjugate the verb.
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:irregular :passato))
          (get-in word '(:essere) true)
          (or (= :notfound (get-in word '(:agr :number) :notfound))
              (= :top (get-in word '(:agr :number)))))
     ;; 'nei': not enough information.
     (do
       (log/warn (str "not enough agreement specified to conjugate: " (get-in word '(:irregular :passato)) " (irreg past)]"))
       (get-in word '(:irregular :passato)))

     ;; TODO: do not use brackets: if there's an error about there being
     ;; regular passato prossimo and essere-verb => NEI (not enough information): defer conjugation and keep as a map.
     (and (= :past (get-in word '(:infl)))
          (= (get-in word '(:essere)) true)
          (or (= :notfound (get-in word '(:agr :number) :notfound))
              (= :top (get-in word '(:agr :number)))))
     ;; 'nei': not enough information.
     (do
       (log/warn (str "not enough agreement specified to conjugate: " (get-in word '(:irregular :passato)) " (past)]"))
       (str (get-in word [:infinitive]) " (past)"))

     ;; conjugate irregular passato: option 1) using :passato-stem
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:passato-stem)))
     (let [irregular-passato (get-in word '(:passato-stem))]
       (str irregular-passato (suffix-of word)))


     ;; conjugate irregular passato: option 2) using :irregular :passato
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:irregular :passato)))
     (let [irregular-passato (get-in word '(:irregular :passato))
           butlast (nth (re-find #"(.*).$" irregular-passato) 1)]
       (str butlast (suffix-of word)))

     ;; conjugate regular passato
     (and (= :past (get-in word '(:infl)))
          (string? (get-in word '(:infinitive))))
     (let [infinitive (get-in word '(:infinitive))
           are-type (try (re-find #"are$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive)))))
           ere-type (re-find #"ere$" infinitive)
           ire-type (re-find #"ire$" infinitive)
           stem (string/replace infinitive #"[iae]re$" "")
           last-stem-char-is-i (re-find #"i$" stem)

           ;; for passato prossimo, the last char depends on gender and number, if an essere-verb.
           suffix (suffix-of word)

           ]

       (cond

        (or are-type ere-type)
        (str stem "at" suffix) ;; "ato" or "ati"

        (or are-type ire-type)
        (str stem "it" suffix) ;; "ito" or "iti"

        true
        (str "(regpast:TODO):" stem)))

     (and (= (get-in word '(:infl)) :present)
          (= person :1st) (= number :sing)
          (string? (get-in word '(:irregular :present :1sing))))
     (get-in word '(:irregular :present :1sing))

     (and (= (get-in word '(:infl)) :present)
          (= person :2nd) (= number :sing)
          (string? (get-in word '(:irregular :present :2sing))))
     (get-in word '(:irregular :present :2sing))

     (and (= (get-in word '(:infl)) :present)
          (= person :3rd) (= number :sing)
          (string? (get-in word '(:irregular :present :3sing))))
     (get-in word '(:irregular :present :3sing))

     (and (= (get-in word '(:infl)) :present)
          (= person :1st) (= number :plur)
          (string? (get-in word '(:irregular :present :1plur))))
     (get-in word '(:irregular :present :1plur))

     (and (= (get-in word '(:infl)) :present)
          (= person :2nd) (= number :plur)
          (string? (get-in word '(:irregular :present :2plur))))
     (get-in word '(:irregular :present :2plur))

     (and (= (get-in word '(:infl)) :present)
          (= person :3rd) (= number :plur)
          (string? (get-in word '(:irregular :present :3plur))))
     (get-in word '(:irregular :present :3plur))

     (and
      (= (get-in word '(:infl)) :present)
      (string? (get-in word '(:infinitive))))
     (let [infinitive (get-in word '(:infinitive))
           are-type (try (re-find #"are$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           ere-type (re-find #"ere$" infinitive)
           ire-type (re-find #"ire$" infinitive)
           stem (string/replace infinitive #"[iae]re$" "")
           last-stem-char-is-i (re-find #"ire$" infinitive)
           last-stem-char-is-e (re-find #"ere$" infinitive)
           is-care-or-gare? (re-find #"[cg]are$" infinitive)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]
       (cond

        (and (= person :1st) (= number :sing)
             (string? (get-in word '(:irregular :present :1sing))))
        (get-in word '(:irregular :present :1sing))

        (and (= person :2nd) (= number :sing)
             (string? (get-in word '(:irregular :present :2sing))))
        (get-in word '(:irregular :present :2sing))

        (and (= person :3rd) (= number :sing)
             (string? (get-in word '(:irregular :present :3sing))))
        (get-in word '(:irregular :present :3sing))

        (and (= person :1st) (= number :plur)
             (string? (get-in word '(:irregular :present :1plur))))
        (get-in word '(:irregular :present :1plur))

        (and (= person :2nd) (= number :plur)
             (string? (get-in word '(:irregular :present :2plur))))
        (get-in word '(:irregular :present :2plur))

        (and (= person :3rd) (= number :plur)
             (string? (get-in word '(:irregular :present :3plur))))
        (get-in word '(:irregular :present :3plur))

        (and (= person :1st) (= number :sing))
        (str stem "o")

        (and (= person :2nd) (= number :sing)
            last-stem-char-is-i)
        (str stem)

        (and (= person :2nd) (= number :sing))
        (str stem "i")

        (and is-care-or-gare? 
             (= person :2nd) (= number :sing))
        (str stem "hi")

        (and (= person :3rd) (= number :sing) (or ire-type ere-type))
        (str stem "e")

        (and (= person :3rd) (= number :sing) are-type)
        (str stem "a")

        (and (= person :1st) (= number :plur)
             last-stem-char-is-i)
        (str stem "amo")

        (and is-care-or-gare?
             (= person :1st) (= number :plur))
        (str stem "hiamo")

        (and (= person :1st) (= number :plur))
        (str stem "iamo")

        (and (= person :2nd) (= number :plur) are-type)
        (str stem "ate")

        (and (= person :2nd) (= number :plur) ere-type)
        (str stem "ete")

        (and (= person :2nd) (= number :plur) ire-type)
        (str stem "ite")


        (and (= person :3rd) (= number :plur)
             last-stem-char-is-i)
        (str stem "ono")
        (and (= person :3rd) (= number :plur)
             last-stem-char-is-e)
        (str stem "ono")

        (and (= person :3rd) (= number :plur))
        (str stem "ano")

        :else
        (str infinitive )))

     (= (get-in word '(:infl)) :top)
     (str (get-in word '(:infinitive)) )

     (and
      (get-in word '(:a))
      (get-in word '(:b)))
     (get-italian
      (get-in word '(:a))
      (get-in word '(:b)))

     ;; TODO: remove support for deprecated :root.
     (and
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:agr :number)) :sing)
      (= (get-in word '(:cat)) :noun)
      (get-in word '(:root)))
     (get-in word '(:root))

     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :sing)
      (= (get-in word '(:cat)) :noun))
     (get-in word '(:italian))

     ;; deprecated: remove support for :root.
     (and
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat) :noun))
      (get-in word '(:root)))
     (string/replace (get-in word '(:root))
                     #"[eo]$" "i") ;; dottore => dottori; medico => medici

   ;; deprecated: TODO: remove this
   (and
    (= (get-in word '(:agr :gender)) :fem)
    (= (get-in word '(:agr :number)) :plur)
    (= (get-in word '(:cat)) :noun)
    (get-in word '(:root)))
   (string/replace (get-in word '(:root))
                   #"[a]$" "e") ;; donna => donne


   ;; deprecated: TODO: remove support for :root.
   (and
    (= (get-in word '(:agr :gender)) :fem)
    (= (get-in word '(:agr :number)) :sing)
    (= (get-in word '(:cat)) :noun)
    (string? (get-in word '(:root))))
   (get-in word '(:root))

   (and
    (= (get-in word '(:agr :gender)) :masc)
    (= (get-in word '(:agr :number)) :sing)
    (= (get-in word '(:cat) :adjective)))
   (get-in word '(:italian)) ;; nero

   (and
    (= (get-in word '(:agr :gender)) :masc)
    (= (get-in word '(:agr :number)) :plur)
    (= (get-in word '(:cat)) :adjective)
    ;; handle lexical exceptions.
    (string? (get-in word '(:irregular :masc :plur))))
   (get-in word '(:irregular :masc :plur))


   (and
    (= (get-in word '(:agr :gender)) :fem)
    (= (get-in word '(:agr :number)) :plur)
    (= (get-in word '(:cat)) :adjective)
    ;; handle lexical exceptions.
    (string? (get-in word '(:irregular :fem :plur))))
   (get-in word '(:irregular :fem :plur))

   (string? (get-in word '(:infinitive)))
   (get-in word '(:infinitive))

   (or
    (not (= :none (get-in word '(:a) :none)))
    (not (= :none (get-in word '(:b) :none))))
   (get-italian (get-in word '(:a))
                (get-in word '(:b)))

   (and (map? word)
        (nil? (:italian word)))
   ".."

   (or
    (= (get-in word '(:case)) {:not :acc})
    (= (get-in word '(:agr)) :top))
   ".."

   ;; TODO: throw exception rather than returning _word_, which is a map or something else unprintable.
   ;; in other words, if we've gotten this far, it's a bug.
   :else
   word)
  )))

(defn get-italian [a & [ b ]]
  (cond (and (nil? b)
             (seq? a))
        (get-italian-1 a)
        
        true
        (let [a (if (nil? a) "" a)
              b (if (nil? b) "" b)
              a (get-italian-1 a)
              b (get-italian-1 b)
              info-a (log/debug (str "get-italian: a: " a))
              info-b (if b (log/debug (str "get-italian: b: " b)))

              it-b (log/debug "it-a is string? " (string? (get-in a '(:italian))))
              it-b (log/debug "it-b is string? " (string? (get-in b '(:italian))))

              cat-a (log/debug (str "cat a:" (get-in a '(:cat))))
              cat-b (log/debug (str "cat b:" (get-in b '(:cat))))

              ]
          (cond

           (and (= a "i")
                (string? (get-in b '(:italian)))
                (re-find #"^[aeiou]" (get-in b '(:italian))))
           (str "gli " b)

           ;; TODO: cleanup & remove.
           (and false ;; going to throw out this logic: will use :initial and rule schemata instead.
                (= :verb (get-in a '(:cat)))
                (= :noun (get-in b '(:cat)))
                (= :acc (get-in b '(:case))))
           ;; flip order in this case:
           ;; i.e. "vedo ti" => "ti vedo".
           {:a (if (nil? b) :top b)
            :b (if (nil? a) :top a)}

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^il (mio|tio|suo|nostro|vostro|loro)\b" b))
           (str a " " (string/replace b #"^il " ""))

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^la (mia|tia|sua|nostra|vostra|loro)\b" b))
           (str a " " (string/replace b #"^la " ""))

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^i (miei|tuoi|suoi|nostri|vostri|loro)\b" b))
           (str a " " (string/replace b #"^i " ""))

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^le (mie|tue|sue|nostre|vostre|loro)\b" b))
           (str a " " (string/replace b #"^le " ""))

           (and (= a "di i")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "degli " b)

           (and (= a "di i")
                (string? b)
                (re-find #"^s[t]" b))
           (str "degli " b)

           (and (= a "di i")
                (string? b))
           (str "dei " b)

           (and (= (get-in a '(:italian)) "di i")
                (string? b))
           (str "dei " b)

           (and (= (get-in a '(:italian)) "di i")
                (string? (get-in b '(:italian))))
           (str "dei " (get-italian-1 (get-in b '(:italian))))

           (and (= a "di il")
                (string? b))
           (get-italian "del" b)  ;; allows this to feed next rule:

           (and (= a "del")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "dell'" b)

           (and (= a "di la")
                (string? b))
           (get-italian "della" b) ;; allows this to feed next rule:

           (and (= a "della")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "dell'" b)

           (and (= a "di le")
                (string? b))
           (str "delle " b)

           (and (= a "i")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "gli " b)

           (and (= a "i")
                (string? (get-in b '(:italian)))
                (re-find #"^[aeiou]" (get-in b '(:italian))))
           (str "gli " b)

           (and (= a "i")
                (string? b)
                (re-find #"^s[t]" b))
           (str "gli " b)

           ;; 1),2),3) handle e.g. "io lo ho visto" => "io l'ho visto"
           ;; 1)
           (and (= a "mi")
                (string? b)
                (re-find #"^[aeiouh]" b))
           (str "m'" b)
           ;; 2)
           (and (= a "ti")
                (string? b)
                (re-find #"^[aeiouh]" b))
           (str "t'" b)
           ;; 3)
           (and (string? a)
                (re-find #"^l[ao]$" a)
                (string? b)
                (re-find #"^[aeiouh]" b))
           (str "l'" b)

           ;; 4) handle e.g. "aiutari + ti" => "aiutarti"
           (and (string? a)
                (or (re-find #"are$" a)
                    (re-find #"ere$" a)
                    (re-find #"ire$" a))
                (or (= b "ci")
                    (= b "mi")
                    (= b "la")
                    (= b "le")
                    (= b "li")
                    (= b "lo")
                    (= b "ti")
                    (= b "vi")))
           (str (string/replace a #"[e]$" "")
                b)
           
           (and (= a "un")
                (string? b)
                (re-find #"^s[t]" b))
           (str "uno " b)

           (and (= a "una")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "un'" b)

           (and (= a "il")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "l'" b)

           (and (= a "il")
                (string? b)
                (re-find #"^s[ct]" b))
           (str "lo " b)

           (and (= a "la")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "l'" b)

           (and (= a "quell[ao]")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "quell'" b)

           (and (= a "quelli")
                (string? b)
                (re-find #"^(st|sc|[aeiou])" b))
           (str "quegli " b)

           (and (= a "quest[aeio]")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "quest'" b)

           ;; prepositional phrases
           (and (= a "a")
                (string? b)
                (re-find #"^il " b))
           (str "al " (string/replace b #"^il " ""))

           (and (= a "a")
                (string? b)
                (re-find #"^i " b))
           (str "ai " (string/replace b #"^i " ""))

           (and (= a "a")
                (string? b)
                (re-find #"^le " b))
           (str "alle " (string/replace b #"^le " ""))

           (and (= a "a")
                (string? b)
                (re-find #"^la " b))
           (str "alla " (string/replace b #"^la " ""))
     
           (and (string? a) (string? b))
           (str a " " b)
           
           (and (string? a) (string? (get-in b '(:italian))))
           (str a " " (get-in b '(:italian)))
           
           (and (string? (get-in a '(:italian)))
                (string? b))
           (str (get-in a '(:italian)) " " b)
           
           (and (string? a)
                (map? b))
           (str a " ..")

           (and (string? b)
                (map? a))
           (str " .." b)

           true
           {:a (if (nil? a) :top a)
            :b (if (nil? b) :top b)}))))
