(ns italianverbs.morphology.francais
  (:refer-clojure :exclude [get-in merge resolve]))

(require '[clojure.core :as core])
(require '[clojure.string :as string])
(require '[clojure.string :refer (trim)])
(require '[clojure.tools.logging :as log])
(require '[italianverbs.stringutils :refer :all])
(require '[dag-unify.core :refer (copy dissoc-paths fail? get-in merge ref? strip-refs unifyc)])

(declare get-string)
(declare suffix-of)

;; TODO: this is an overly huge method that needs to be rewritten to be easier to understand and maintain.
(defn get-string-1 [word & [:usted usted :tú tu :vosotros vosotros :ustedes ustedes]]
  (cond (string? word)
        word
        (seq? word)
        (map (string/join " " #(get-string-1 %))
             word)
        true
  (let [person (get-in word '(:agr :person))
        number (get-in word '(:agr :number))
        info (log/debug "get-string-1: input word: " word)
        vosotros (if vosotros vosotros true)
        ustedes (if ustedes ustedes false)
        tú (if tú tú false)
        usted (if usted usted false)]

    (log/debug (str "get-string-1: word: " word))
    (log/debug (str "get-string-1: word (stripped-refs): " (strip-refs word)))
    (log/debug (str "word's a is a string? " (get-in word '(:a)) " => " (string? (get-in word '(:a)))))
    (log/debug (str "word's b is a map? " (get-in word '(:b)) " => " (map? (get-in word '(:b)))))

    (log/debug (str "word's a french is a string? " (get-in word '(:a :french)) " => " (string? (get-in word '(:a :french)))))

    (cond

     (= word :top) ".."

     (ref? word)
     (get-string-1 @word)

     ;; TODO: this is a special case that should be handled below instead
     ;; of forcing every input to go through this check.
     (= word {:initial false})
     ".."
     (= word {:initial true})
     ".."

     (and (string? (get-in word '(:a)))
          (string? (get-in word '(:b))))
     (get-string (get-in word '(:a))
                 (get-in word '(:b)))

     (and (string? (get-in word '(:a)))
          (map? (get-in word '(:b))))
     (get-string (get-in word '(:a))
                 (get-in word '(:b)))

     (and (map? (get-in word '(:a)))
          (map? (get-in word '(:b))))
     (get-string
      (get-in word '(:a))
      (get-in word '(:b)))

     ;; TODO: this rule is pre-empting all of the following rules
     ;; that look in :a and :b. Either remove those following rules
     ;; if they are redundant and not needed, or move this general rule
     ;; below the following rules.
     (and (not (= :none (get-in word '(:a) :none)))
          (not (= :none (get-in word '(:b) :none))))
     (get-string (get-in word '(:a))
                 (get-in word '(:b)))

     (and
      (string? (get-in word '(:a :french)))
      (string? (get-in word '(:b :french)))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      )
     (str (string/trim (get-in word '(:a :french)))
          " "
          (string/trim (get-in word '(:b :french))))

     (and
      (string? (get-in word '(:a)))
      (string? (get-in word '(:b :french)))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      )
     (str (string/trim (get-in word '(:a)))
          " "
          (string/trim (get-in word '(:b :french))))

     (and
      (string? (get-in word '(:a :french)))
      (get-in word '(:a :french))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      (= (get-in word '(:a :infl)) :top))
     (string/trim (str (get-in word '(:a :french))
                 " " (get-string-1 (get-in word '(:b)))))

     (= true (get-in word [:exception]))
     (get-in word [:french])

     (and
      (= (get-in word '(:infl)) :present)
      (string? (get-in word '(:french))))
     (let [infinitive (get-in word '(:french))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]
       ;;QUI COMINCIANO I VERBI FRANCESI REGOLARI
       (cond

        (and (= person :1st) (= number :sing) er-type)
        (str stem "e")

        (and (= person :1st) (= number :sing) ir-type)
        (str stem "is")
        
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "es")
        
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "is")

        (and (= person :2nd) (= number :sing) er-type)
        (str stem "ez")
       
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "issez")

        (and (= person :3rd) (= number :sing) er-type)
        (str stem "e")
        (and (= person :3rd) (= number :sing) (ir-type))
        (str stem "it")


        (and (= person :1st) (= number :plur) er-type)
        (str stem "ons")
       
        (and (= person :1st) (= number :plur) ir-type)
        (str stem "issons")
       
        ;; <second person plural present>

        (and (= person :2nd) (= number :plur) er-type)
        (str stem "ez")

         (and (= person :2nd) (= number :plur) ir-type)
        (str stem "issez")  
       
      
        ;; </second person plural present>

        ;; <third person plural present>
        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "ent")
               (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "issent")

        ;; </third person plural present>
        
        ;; agreement is underspecified, but an infinitive form (the :french key) exists, so just return that infinitive form.
        (and (= (get-in word [:agr]) :top)
             (string? (get-in word [:french])))
        (get-in word [:french])

        :else
        (throw (Exception. (str "get-string-1: present regular inflection: don't know what to do with input argument: " (strip-refs word))))))

     (and
      (= (get-in word '(:infl)) :imperfetto)
      (string? (get-in word '(:french))))
     (let [infinitive (get-in word '(:french))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           vosotros (if vosotros vosotros true)
           ustedes (if ustedes ustedes false)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]
;;QUI COMINCIA L’ IMPERFETTO


       (cond
        (and (= person :1st) (= number :sing) er-type)
        (str stem "ais")

        (and (= person :1st) (= number :sing) ir-type)
        (str stem "íssais")

        (and (= person :2nd) (= number :sing) er-type)
        (str stem "ais")

        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "íssais")

        
        (and (= person :3rd) (= number :sing) er-type)
        (str stem "ait")

        (and (= person :3rd) (= number :sing) ir-type)
        (str stem "íssait")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "ions")
        
        (and (= person :1st) (= number :plur) ir-type)
        (str stem "íssions")

        ;; <second person plural imperfecto>

        (and (= person :2nd) (= number :plur) er-type)
        (str stem "iez")

        (and (= person :2nd) (= number :plur) ir-type)
        (str stem "íssiez")

   
        ;; </second person plural imperfecto>

        ;; <third person plural imperfecto>
        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "aient")

       
        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "íssaient")

        ;; </third person plural imperfecto>

        :else
        (throw (Exception. (str "get-string-1: imperfecto regular inflection: don't know what to do with input argument: " (strip-refs word))))))

     (and
      (= (get-in word '(:infl)) :futuro)
      (string? (get-in word '(:french))))
     (let [infinitive (get-in word '(:french))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           
           vosotros (if vosotros vosotros true)
           ustedes (if ustedes ustedes false)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]
;; QUI COMINCIA IL FUTURO FRANCESE
       (cond

        (and (= person :1st) (= number :sing) er-type)
        (str stem "erai")

        (and (= person :1st) (= number :sing) ir-type)
        (str stem "iré")

        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "iras")
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "eras")
        
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "irez")
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "erez")

        
        (and (= person :3rd) (= number :sing) ir-type)
        (str stem "ira")
        (and (= person :3rd) (= number :sing) er-type)
        (str stem "era")

        

        (and (= person :1st) (= number :plur) er-type)
        (str stem "erons")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "irons")

        ;; <second person plural future>

        
        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "erez")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "irez")


        ;; </second person plural future>

        ;; <third person plural future>
        

        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "eront")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "iront")

        ;; </third person plural future>

        :else
        (throw (Exception. (str "get-string-1: futuro regular inflection: don't know what to do with input argument: " (strip-refs word))))))

     (and
      (= (get-in word '(:infl)) :conditional)
      (string? (get-in word '(:french))))
     (let [infinitive (get-in word '(:french))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           vosotros (if vosotros vosotros true)
           ustedes (if ustedes ustedes false)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

;;QUI COMINCIA IL CONDIZIONALE FRANCESE
       (cond
        
        (and (= person :1st) (= number :sing) er-type)
        (str stem "erais")
        (and (= person :1st) (= number :sing) ir-type)
        (str stem "irais")

        
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "erais")
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "irais")

        
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "eriez")
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "iriez")
        
        (and (= person :3rd) (= number :sing) ir-type)
        (str stem "erait")
        (and (= person :3rd) (= number :sing) er-type)
        (str stem "irait")

        

        (and (= person :1st) (= number :plur) er-type)
        (str stem "erions")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "irions")

        ;; <second person plural conditional>

        

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "eriez")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "iriez")

       

       )

        ;; </second person plural conditional>

        ;; <third person plural conditional>
        

        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "eraient")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "iraient")

        ;; </third person plural conditional>

        :else
        (throw (Exception. (str "get-string-1: conditional regular inflection: don't know what to do with input argument: " (strip-refs word))))))

     (and
      (= (get-in word '(:infl)) :preterito)
      (string? (get-in word '(:french))))
     (let [infinitive (get-in word '(:french))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           vosotros (if vosotros vosotros true)
           ustedes (if ustedes ustedes false)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

;;ON JULY 7 2015 I HAVE WORKED THIS FAR

;;HERE THE PASSE SIMPLE BEGINS
       (cond
        (and (= person :1st) (= number :sing) er-type)
        (str stem "ai")

        (and (= person :1st) (= number :sing) (or ir-type))
        (str stem "is")

        (and (= person :2nd) (= number :sing) er-type)
        (str stem "as")

        (and (= person :2nd) (= number :sing) (or ir-type))
        (str stem "is")

        (and (= person :2nd) (= number :sing) er-type)
        (str stem "âtes")

        (and (= person :2nd) (= number :sing) (or ir-type))
        (str stem "îtes")

        (and (= person :3rd) (= number :sing) er-type)
        (str stem "a")

        (and (= person :3rd) (= number :sing) (or ir-type))
        (str stem "it")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "âmes")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "îmes")

        ;; <second person plural passé simple>

        (and (= person :2nd) (= number :plur) er-type)
        (str stem "âtes")

        (and (= person :2nd) (= number :plur) ir-type)
        (str stem "îtes")

      

        ;; </second person plural passé simple>

        ;; <third person plural preterite>
        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "érent")
      
        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "irent")

        ;; </third person plural preterite>

        :else
;;ON JULY 19 I HAVE GOT THIS FAR

;;JULY 21ST ATTEMPT TO PASSE COMPOSE


  (and (= :past (get-in word '(:infl)))


          (string? (get-in word '(:french))))


     (let [infinitive (get-in word '(:french))


           ;; e.g.: se laver => laver


           infinitive (if (re-find #"[aei]rsi$" infinitive)


                        (string/replace infinitive #"si$" "e")


                        infinitive)







           er-type (try (re-find #"er$" infinitive)


                         (catch Exception e


                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive)))))


       


           ir-type (re-find #"ire$" infinitive)


           stem (string/replace infinitive #"[iae]re$" "")


           last-stem-char-is-i (re-find #"i$" stem)







           ;; for passato prossimo, the last char depends on gender and number, if an être-verb.


           suffix (suffix-of word)



           ]







       (cond

;;; TODO: re-type is not defined: typo?
;;;        re-type
;;;        (str stem "ut" suffix) ;; "u","us","ue" or "ue"

        er-type
        (str stem "at" suffix) ;; "ato","ati","ata", or "ate"


        ir-type
        (str stem "it" suffix) ;; "i","is","ie", or "ies"

        true
        (str "(regpast:TODO):" stem)))







     (and (= (get-in word '(:infl)) :present)


          (= person :1st) (= number :sing)


          (string? (get-in word '(:present :1sing))))


     (get-in word '(:present :1sing))







     (and (= (get-in word '(:infl)) :present)


          (= person :2nd) (= number :sing)


          (string? (get-in word '(:present :2sing))))


     (get-in word '(:present :2sing))







     (and (= (get-in word '(:infl)) :present)


          (= person :3rd) (= number :sing)


          (string? (get-in word '(:present :3sing))))


     (get-in word '(:present :3sing))







     (and (= (get-in word '(:infl)) :present)


          (= person :1st) (= number :plur)


          (string? (get-in word '(:present :1plur))))


     (get-in word '(:present :1plur))







     (and (= (get-in word '(:infl)) :present)


          (= person :2nd) (= number :plur)


          (string? (get-in word '(:present :2plur))))


     (get-in word '(:present :2plur))







     (and (= (get-in word '(:infl)) :present)


          (= person :3rd) (= number :plur)


          (string? (get-in word '(:present :3plur))))


     (get-in word '(:present :3plur))







     (and


      (= (get-in word '(:infl)) :present)


      (string? (get-in word '(:french))))


     (let [infinitive (get-in word '(:french))


           ;; e.g.: se laver => laver


           infinitive (if (re-find #"[aei]rsi$" infinitive)


                        (string/replace infinitive #"si$" "e")


                        infinitive)


           er-type (try (re-find #"er$" infinitive)


                         (catch Exception e


                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))


           re-type (re-find #"re$" infinitive)


           ir-type (re-find #"ir$" infinitive)


           stem (cond (and (get-in word [:boot-stem1])


                           (or (= (get-in word [:agr :number])


                                  :sing)


                               (and (= (get-in word [:agr :person])


                                       :3rd)


                                    (= (get-in word [:agr :number])


                                       :plur))))


                      (get-in word [:boot-stem1])

;;THE FOLLOWING TWO LINES NEED TO BE CHANGED

                      true
                      (string/replace infinitive #"[iae]re$" ""))


           last-stem-char-is-i (re-find #"i[iae]re$" infinitive)


           last-stem-char-is-e (re-find #"e[iae]re$" infinitive)


           is-care-or-gare? (re-find #"[cg]are$" infinitive)


           person (get-in word '(:agr :person))


           number (get-in word '(:agr :number))]


       (cond


        (and (= person :1st) (= number :sing))
        (str stem "o")

;;JULY 21ST THIS FAR
        (and (= person :2nd) (= number :sing)
             last-stem-char-is-i)
        ;; do not add 'i' at the end here to prevent double i:
        (str stem "")


        (and (= person :2nd) (= number :sing))
        (str stem "i")

        (and is-care-or-gare? 
             (= person :2nd) (= number :sing))
        (str stem "hi")

        (and (= person :3rd) (= number :sing) (or re-type er-type))
        (str stem "e")

        (and (= person :3rd) (= number :sing) re-type)
        (str stem "a")

        (and (= person :1st) (= number :plur)
             last-stem-char-is-i)
        (str stem "amo")

        (and is-care-or-gare?


             (= person :1st) (= number :plur))


        (str stem "hiamo")







        (and (= person :1st) (= number :plur))
        (str stem "iamo")

        (and (= person :2nd) (= number :plur) re-type)
        (str stem "ate")

        (and (= person :2nd) (= number :plur) er-type)
        (str stem "ete")

        (and (= person :2nd) (= number :plur) ir-type)
        (str stem "ite")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "ono")

        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "ono")

        (and (= person :3rd) (= number :plur))
        (str stem "ano")

        :else
        (str infinitive )))

     (and
      (string? (get-in word '(:italiano)))
      (= :top (get-in word '(:agr :sing) :top)))
     (str (get-in word '(:italiano)))

     ;; TODO: possibly remove this: not sure it's doing anything.
     (= true (get-in word [:exception]))
     (get-in word [:italiano])

     (= (get-in word '(:infl)) :top)
     (str (get-in word '(:italiano)))

     (and
      (get-in word '(:a))
      (get-in word '(:b)))
     (get-string
      (get-in word '(:a))
      (get-in word '(:b)))

     (= (get-in word '(:a)) :top)
     (str

      ".." " " (get-string-1 (get-in word '(:b))))

     (and
      (= (get-in word '(:b)) :top)
      (string? (get-string-1 (get-in word '(:a)))))
     (str
      (get-string-1 (get-in word '(:a)))
      " " "..")

     (and
      (= (get-in word '(:b)) :top)
      (string? (get-in word '(:a :italiano))))

     (str
      (get-string-1 (get-in word '(:a :italiano)))
      " " "..")

     (and


      (= (get-in word '(:agr :gender)) :fem)


      (= (get-in word '(:agr :number)) :sing)


      (= (get-in word '(:cat)) :noun))


     (get-in word '(:italiano))







     (and


      (= (get-in word '(:agr :gender)) :masc)


      (= (get-in word '(:agr :number)) :sing)


      (= (get-in word '(:cat) :adjective)))


     (get-in word '(:italiano)) ;; nero







     (and


      (= (get-in word '(:agr :gender)) :masc)


      (= (get-in word '(:agr :number)) :plur)


      (= (get-in word '(:cat)) :adjective)


      ;; handle lexical exceptions.


      (string? (get-in word '(:masc :plur))))


     (get-in word '(:masc :plur))







     (and


      (= (get-in word '(:agr :gender)) :fem)


      (= (get-in word '(:agr :number)) :plur)


      (= (get-in word '(:cat)) :adjective)


      ;; handle lexical exceptions.


      (string? (get-in word '(:fem :plur))))


     (get-in word '(:fem :plur))


     


     (string? (get-in word '(:italiano)))


     (get-in word '(:italiano))







     (or


      (not (= :none (get-in word '(:a) :none)))


      (not (= :none (get-in word '(:b) :none))))


     (get-string (get-in word '(:a))


                 (get-in word '(:b)))







     (and (map? word)


          (nil? (:italiano word)))


     ".."







     (or


      (= (get-in word '(:case)) {:not :acc})


      (= (get-in word '(:agr)) :top))


     ".."













        (throw (Exception. (str "get-string-1: conditional regular inflection: don't know what to do with input argument: " (strip-refs word))))))

     (string? (get-in word [:french]))
     (get-in word [:french])

     true
     (throw (Exception. (str "get-string-1: don't know what to do with input argument: " word))))))

(defn get-string [a & [ b ]]
  (cond (and (nil? b)
             (seq? a))
        (let [result (get-string-1 a)]
          (if (string? result)
            (trim result)
            result))

        true
        (trim (string/join " "
                           (list (get-string-1 a)
                                 (if b (get-string-1 b)
                                     ""))))))

(declare fo-ps-it)

(defn fo-ps [expr]
  "show the phrase-structure of a phrase structure tree, e.g [hh21 'mangiare (to eat)' [cc10 'il (the)' 'pane(bread)']]"
  ;; [:first = {:head,:comp}] will not yet be found in expr, so this head-first? will always be false.
  (let [head-first? (= :head (get-in expr [:first]))]
    (cond

     (and
      (or (set? expr)
          (seq? expr)
          (vector? expr))
      (empty? expr))
     (str "")


     (and
      (or (set? expr)
          (seq? expr)
          (vector? expr))
      (not (empty? expr)))

     ;; expr is a sequence of some kind. Assume each element is a phrase structure tree and show each.
     (map (fn [each]
            (fo-ps each))
          expr)

     (and (map? expr)
          (:french expr))
     (fo-ps-it (:french expr))

     (and (map? expr)
          (:rule expr)
          (= (get-in expr '(:french :a))
             (get-in expr '(:comp :french))))
     ;; complement first
     (str "[" (:rule expr) " "
          (fo-ps (get-in expr '(:comp)))
          " "
          (fo-ps (get-in expr '(:head)))
          "]")

     (and (map? expr)
          (:rule expr))
     ;; head first ('else' case of above.)
     (str "[" (:rule expr) " "
          (fo-ps (get-in expr '(:head)))
          " "
          (fo-ps (get-in expr '(:comp)))
          "]")

     (and (map? expr)
          (:comment expr)
          (= (get-in expr '(:french :a))
             (get-in expr '(:comp :french))))
     ;; complement first
     (str "[" (:comment expr) " "
          (fo-ps (get-in expr '(:comp)))
          " "
          (fo-ps (get-in expr '(:head)))
          "]")

     (and (map? expr)
          (:comment expr))
     ;; head first ('else' case of above.)
     (str "[" (:comment expr) " "
          (fo-ps (get-in expr '(:head)))
          " "
          (fo-ps (get-in expr '(:comp)))
          "]")

     (and
      (map? expr)
      (:french expr))
     (get-string-1 (get-in expr '(:french)))

     true
     expr)))

(defn stem-per-passato-prossimo [infinitive]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (string/replace infinitive #"^(.*)([aei])(re)$" (fn [[_ prefix vowel suffix]] (str prefix))))

(defn passato-prossimo [infinitive]
  (str (stem-per-passato-prossimo infinitive) "ato"))

;; allows reconstruction of the infinitive form from the inflected form
(def future-to-infinitive
  {
   ;; future
   #"ò$" 
   {:replace-with "e"
    :unify-with {:french {:infl :futuro
                            :agr {:number :sing
                                  :person :1st}}}}
   #"ai$" 
   {:replace-with "e"
    :unify-with {:french {:infl :futuro
                            :agr {:number :sing
                                  :person :2nd}}}}
   #"à$" 
   {:replace-with "e"
    :unify-with {:french {:infl :futuro
                            :agr {:number :sing
                                  :person :3rd}}}}
   #"emo$" 
   {:replace-with "e"
    :unify-with {:french {:infl :futuro
                            :agr {:number :plur
                                  :person :1st}}}}
   #"ete$" 
   {:replace-with "e"
    :unify-with {:french {:infl :futuro
                            :agr {:number :plur
                                  :person :2nd}}}}
   #"anno$" 
   {:replace-with "e"
    :unify-with {:french {:infl :futuro
                            :agr {:number :plur
                                  :person :3rd}}}}})

(def present-to-infinitive-ire
  {
   ;; present -ire
   #"o$"
   {:replace-with "ire"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :1st}}}}
   
   #"i$"
   {:replace-with "ire"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :2nd}}}}

   #"e$"
   {:replace-with "ire"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :3rd}}}}

   #"iamo$"
   {:replace-with "ire"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :1st}}}}

   #"ete$"
   {:replace-with "ire"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :2nd}}}}
   
   #"ono$"
   {:replace-with "ire"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :3rd}}}}})


(def present-to-infinitive-ere
  {;; present -ere
   #"o$"
   {:replace-with "ere"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :1st}}}}

   #"i$"
   {:replace-with "ere"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :2nd}}}}
   
   #"e$"
   {:replace-with "ere"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :3rd}}}}
   
   #"iamo$"
   {:replace-with "ere"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :1st}}}}

   #"ete$"
   {:replace-with "ere"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :2nd}}}}
   
   #"ano$"
   {:replace-with "ere"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :3rd}}}}})

(def present-to-infinitive-are
  {
   ;; present -are
   #"o$"
   {:replace-with "are"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :1st}}}}

   #"i$"
   {:replace-with "are"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :2nd}}}}

   #"e$"
   {:replace-with "are"
    :unify-with {:french {:infl :present
                            :agr {:number :sing
                                  :person :3rd}}}}
   
   #"iamo$"
   {:replace-with "are"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :1st}}}}

   #"ete$"
   {:replace-with "are"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :2nd}}}}

   #"ano$"
   {:replace-with "are"
    :unify-with {:french {:infl :present
                            :agr {:number :plur
                                  :person :3rd}}}}})

(def imperfect-to-infinitive-irreg1
  {
   ;; e.g.: "bevevo/bevevi/..etc" => "bere"
   #"vevo$"
   {:replace-with "re"
    :unify-with {:french {:infl :imperfetto
                            :agr {:number :sing
                                  :person :1st}}}}

   #"vevi$"
   {:replace-with "re"
    :unify-with {:french {:infl :imperfetto
                            :agr {:number :sing
                                  :person :2nd}}}}

   #"veva$"
   {:replace-with "re"
    :unify-with {:french {:infl :imperfetto
                            :agr {:number :sing
                                  :person :3rd}}}}
   })

(def past-to-infinitive
  {#"ato$"
   {:replace-with "are"
    :unify-with {:french {:infl :past}}}

   #"ito$"
   {:replace-with "ire"
    :unify-with {:french {:infl :past}}}

   #"uto$"
   {:replace-with "ere"
    :unify-with {:french {:infl :past}}}})

(def plural-to-singular-noun-fem-1
  {#"e$"
   {:replace-with "a"
    :unify-with {:synsem {:cat :noun
                          :agr {:gender :fem
                                :number :plur}}}}})

(def plural-to-singular-noun-masc-1
  {#"i$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :noun
                          :agr {:number :plur}}}}})

(def plural-to-singular-noun-masc-2 ;; e.g. "cani" -> "cane"
  {#"i$"
   {:replace-with "e"
    :unify-with {:synsem {:cat :noun
                          :agr {:number :plur}}}}})


(def plural-to-singular-adj-masc
  {#"i$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :adjective
                          :agr {:gender :masc
                                :number :plur}}}}})

(def plural-to-singular-adj-fem-sing
  {#"a$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :adjective
                          :agr {:gender :fem
                                :number :sing}}}}})

(def plural-to-singular-adj-fem-plur
  {#"e$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :adjective
                          :agr {:gender :fem
                                :number :plur}}}}})

(def infinitive-to-infinitive
  {:identity
   {:unify-with {:synsem {:cat :verb
                          :infl :infinitive}}}})

(def lexical-noun-to-singular
  {:identity
   {:unify-with {:synsem {:cat :noun
                          :agr {:number :sing}}}}})

(defn analyze [surface-form lookup-fn]
  "return the map incorporating the lexical information about a surface form."
  (let [replace-pairs
        ;; Even though it's possible for more than one KV pair to have the same key:
        ;; e.g. plural-to-singular-noun-masc-1 and plural-to-singular-noun-masc-2 both have
        ;; #"i$", they are distinct as separate keys in this 'replace-pairs' hash, as they should be.
        (merge 
         future-to-infinitive
         imperfect-to-infinitive-irreg1
         infinitive-to-infinitive ;; simply turns :top into :infl
         lexical-noun-to-singular ;; turns :number :top to :number :sing
         past-to-infinitive
         present-to-infinitive-ire
         present-to-infinitive-ere
         present-to-infinitive-are
         plural-to-singular-noun-fem-1
         plural-to-singular-noun-masc-1
         plural-to-singular-noun-masc-2
         plural-to-singular-adj-masc
         plural-to-singular-adj-fem-plur
         plural-to-singular-adj-fem-sing
         )
        
        analyzed
        (remove fail?
                (mapcat
                 (fn [key]
                   (if (and (not (keyword? key)) (re-find key surface-form))
                        (let [replace-with (get replace-pairs key)
                              lexical-form (if (= key :identity)
                                             surface-form
                                             (string/replace surface-form key
                                                             (:replace-with replace-with)))
                              looked-up (lookup-fn lexical-form)]
                          (map #(unifyc 
                                 %
                                 (:unify-with replace-with))
                               looked-up))))
                 (keys replace-pairs)))

        ;; Analyzed-via-identity is used to handle infinitive verbs: converts them from unspecified inflection to
        ;; {:infl :infinitive}
        ;; Might also be used in the future to convert nouns from unspecified number to singular number.
        analyzed-via-identity
        (remove fail?
                (mapcat
                 (fn [key]
                   (if (and (keyword? key) (= key :identity))
                        (let [lexical-form surface-form
                              looked-up (lookup-fn lexical-form)]
                          (map #(unifyc 
                                 %
                                 (:unify-with (get replace-pairs key)))
                               looked-up))))
                 (keys replace-pairs)))]


    (concat
     analyzed

     ;; also lookup the surface form itself, which
     ;; might be either the canonical form of a word, or an irregular conjugation of a word.
     (if (not (empty? analyzed-via-identity))
       analyzed-via-identity
       (lookup-fn surface-form)))))

(defn exception-generator [lexicon]
  (let [lexeme-kv (first lexicon)
        lexemes (second lexeme-kv)]
    (if lexeme-kv
      (let [result (mapcat (fn [path-and-merge-fn]
                             (let [path (:path path-and-merge-fn)
                                   merge-fn (:merge-fn path-and-merge-fn)]
                               ;; a lexeme-kv is a pair of a key and value. The key is a string (the word's surface form)
                               ;; and the value is a list of lexemes for that string.
                               (log/debug (str (first lexeme-kv) "looking at path: " path))
                               (mapcat (fn [lexeme]
                                         ;; this is where a unify/dissoc that supported
                                         ;; non-maps like :top and :fail, would be useful:
                                         ;; would not need the (if (not (fail? lexeme)..)) check
                                         ;; to avoid a difficult-to-understand "java.lang.ClassCastException: clojure.lang.Keyword cannot be cast to clojure.lang.IPersistentMap" error.
                                         (let [lexeme (cond (= lexeme :fail)
                                                            :fail
                                                            (= lexeme :top)
                                                            :top
                                                            true
                                                            (dissoc (copy lexeme) :serialized))]
                                           (if (not (= :none (get-in lexeme path :none)))
                                             (list {(get-in lexeme path :none)
                                                    (merge
                                                     lexeme
                                                     (unifyc (apply merge-fn (list lexeme))
                                                             {:french {:exception true}}))}))))
                                       lexemes)))
                           [
                            ;; 1. past-tense exceptions
                            {:path [:french :passato]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :past
                                           :french (get-in val [:french :passato] :nothing)}})}

                            ;; 2. present-tense exceptions
                            {:path [:french :present :1sing]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :present
                                           :french (get-in val [:french :present :1sing] :nothing)
                                           :agr {:number :sing
                                                 :person :1st}}})}
                            {:path [:french :present :2sing]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :present
                                           :french (get-in val [:french :present :2sing] :nothing)
                                           :agr {:number :sing
                                                 :person :2nd}}})}

                            {:path [:french :present :3sing]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :present
                                           :french (get-in val [:french :present :3sing] :nothing)
                                           :agr {:number :sing
                                                 :person :3rd}}})}

                            {:path [:french :present :1plur]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :present
                                           :french (get-in val [:french :present :1plur] :nothing)
                                           :agr {:number :plur
                                                 :person :1st}}})}

                            {:path [:french :present :2plur]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :present
                                           :french (get-in val [:french :present :2plur] :nothing)
                                           :agr {:number :plur
                                                 :person :2nd}}})}

                            {:path [:french :present :3plur]
                             :merge-fn
                             (fn [val]
                               {:french {:infl :present
                                           :french (get-in val [:french :present :3plur] :nothing)
                                           :agr {:number :plur
                                                 :person :3rd}}})}

                            ;; adjectives
                            {:path [:french :masc :plur]
                             :merge-fn
                             (fn [val]
                               {:french {:agr {:gender :masc
                                                 :number :plur}}})}

                            {:path [:french :fem :plur]
                             :merge-fn
                             (fn [val]
                               {:french {:agr {:gender :fem
                                                 :number :plur}}})}
                            ])]
        (if (not (empty? result))
          (concat result (exception-generator (rest lexicon)))
          (exception-generator (rest lexicon)))))))

(defn phonize [a-map a-string]
  (let [common {:phrasal false}]
    (cond (or (vector? a-map) (seq? a-map))
          (map (fn [each-entry]
                 (phonize each-entry a-string))
               a-map)

          (and (map? a-map)
               (not (= :no-french (get-in a-map [:french] :no-french))))
          (unifyc {:french {:french a-string}}
                  common
                  a-map)

        true
        (unifyc a-map
                {:french {:french a-string}}
                common))))

(defn agreement [lexical-entry]
  (cond
   (= (get-in lexical-entry [:synsem :cat]) :verb)
   (let [cat (ref :top)
         infl (ref :top)]
     (unifyc lexical-entry
             {:french {:cat cat
                         :infl infl}
              :synsem {:cat cat
                       :infl infl}}))

   (= (get-in lexical-entry [:synsem :cat]) :noun)
   (let [agr (ref :top)
         cat (ref :top)]
     (unifyc lexical-entry
             {:french {:agr agr
                        :cat cat}
              :synsem {:agr agr
                       :cat cat}}))

   true
   lexical-entry))

(def french-specific-rules
  (list agreement))

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

