(ns italianverbs.config
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [hiccup.core :refer (html)]
   [dag-unify.core :refer [unify]]))

(def time-format "Dy Month FMDD YYYY HH24:MI TZ")

(declare short-language-name-to-edn)
(declare short-language-name-to-long)
(declare short-language-name-from-match)

(defn language-dropdown [language & [prefix]]
  (let [prefix (if prefix prefix "/editor/")]
    (html
     [:div.dropdown {:style "margin-left:0.5em;float:left;width:auto"}
      "Show games for:"
      [:div {:style "float:right;padding-left:1em;width:auto;"}
       [:form {:method "get"}
        [:select#edit_which_language {:name "language"
                                      :onchange (str "document.location='" prefix "' + this.value")}
         (map (fn [lang-pair]
                (let [label (:label lang-pair)
                      value (:value lang-pair)]
                  [:option (conj {:value value}
                                 (if (= language (:value lang-pair))
                                 {:selected "selected"}
                                 {}))
                   label]))
              [{:value "" :label "All Languages"}
               {:value "es" :label "Español"}
               {:value "it" :label "Italiano"}])]]]])))

;; sqlnames: 'english','espanol' <- note: no accent on 'n' ,'italiano', ..
(defn sqlname-from-match [match-string]
  (cond (nil? match-string)
        (str "(no sqlname for language: (nil) detected).")

        (re-find #"espanol" (string/lower-case match-string))
        "espanol"
        (re-find #"español" (string/lower-case match-string))
        "espanol"
        (re-find #"spanish" (string/lower-case match-string))
        "espanol"

        (re-find #"english" (string/lower-case match-string))
        "english"

        (re-find #"french" (string/lower-case match-string))
        "français"
        (re-find #"français" (string/lower-case match-string))
        "français"
        (re-find #"francais" (string/lower-case match-string))
        "français"

        (re-find #"italiano" (string/lower-case match-string))
        "italiano"
        (re-find #"italian" (string/lower-case match-string))
        "italiano"

        :else
        (str "(no sqlname for language:" match-string " detected.")))

(defn language-to-root-keyword [short-language-name]
  (sqlname-from-match (short-language-name-to-long short-language-name)))

(defn language-to-spec-path [short-language-name]
  "Take a language name like 'it' and turn it into an array like: [:root :italiano :italiano]."
  (let [language-keyword-name (language-to-root-keyword short-language-name)
        language-keyword (keyword language-keyword-name)]
    [:root language-keyword language-keyword]))

(defn language-radio-buttons []
  {:name :lang :label "Language" :type "radios"
   :options [{:value "es" :label "Español"}
             ;; French not supported yet, so commented out.
             {:value "fr" :label "Français"}
             {:value "it" :label "Italiano"}]})

(defn language-to-root-spec [short-language-name root]
  "Take a language name like 'it' and a verb root and turn it into a map like: {:root {:italiano {:italiano <root>}}}."
  (let [language-keyword-name (language-to-root-keyword short-language-name)
        language-keyword (keyword language-keyword-name)]
    {:root {language-keyword {language-keyword root}}}))

;; TODO: throw exception rather than 'unknown for unknown languages.
(defn short-language-name-to-long [lang]
  (cond (= lang "it") "Italian"
        (= lang "en") "English"
        (= lang "es") "Spanish"
        (= lang "fr") "French"
        true (str "unknown:" lang)))

+;; TODO: throw exception rather than "(no shortname for language)"
(defn short-language-name-from-match [match-string]
   (cond (nil? match-string)
         (str "(no shortname for language: (nil) detected).")

         (re-find #"espanol" (string/lower-case match-string))
         "es"
         (re-find #"español" (string/lower-case match-string))
         "es"

         (re-find #"english" (string/lower-case match-string))
         "en"

         (re-find #"french" (string/lower-case match-string))
         "fr"
         (re-find #"français" (string/lower-case match-string))
         "fr"
         (re-find #"francais" (string/lower-case match-string))
         "fr"

         (re-find #"italiano" (string/lower-case match-string))
         "it"
         (re-find #"italian" (string/lower-case match-string))
         "it"

         :else
         (str "(no shortname for language:" match-string " detected.")))

(def tenses-human-readable
  {{:synsem {:sem {:tense :conditional}}} "conditional"
   {:synsem {:sem {:tense :futuro}}} "future"
   {:synsem {:sem {:tense :past :aspect :progressive}}} "imperfect"
   {:synsem {:sem {:tense :past :aspect :perfect}}} "past"
   {:synsem {:sem {:tense :present}}} "present"})

;; reverse of tenses-human-readable, immediately-above.
(def human-tenses-to-spec
  (zipmap (map keyword (vals tenses-human-readable))
          (keys tenses-human-readable)))

(defn describe-spec [refine]
  (let [lexeme
        (cond
         (string? (get-in refine [:root :italiano :italiano]))
         (get-in refine [:root :italiano :italiano])

         (string? (get-in refine [:root :english :english]))
         (get-in refine [:root :english :english])

         (string? (get-in refine [:root :espanol :espanol]))
         (get-in refine [:root :espanol :espanol])

         (string? (get-in refine [:root :français :français]))
         (get-in refine [:root :français :français])

         true (type refine))
        
        aspect (get-in refine [:synsem :sem :aspect] :top)
        tense (get-in refine [:synsem :sem :tense] :top)

        tense-spec (unify (if (not (= aspect :top))
                            {:synsem {:sem {:aspect aspect}}}
                            :top)
                          (if (not (= tense :top))
                            {:synsem {:sem {:tense tense}}}
                            :top))
        human-readable-tense (get tenses-human-readable tense-spec)]
    (string/join ", " (remove #(nil? %) (list lexeme human-readable-tense)))))


(defn json-read-str [json]
  (json/read-str json
                 :key-fn keyword
                 :value-fn (fn [k v]
                             (cond
                              (and (or (= k :english)
                                       (= k :espanol)
                                       (= k :français)
                                       (= k :italiano))
                                   (not (map? v)))
                              (str v)

                              (and (string? v)
                                   (= (nth v 0) \:))
                              (keyword (string/replace-first v ":" ""))

                              (string? v)
                              (keyword v)
                              :else v))))

(def tenses-human-readable
  {{:synsem {:sem {:tense :conditional}}} "conditional"
   {:synsem {:sem {:tense :futuro}}} "future"
   {:synsem {:sem {:tense :past :aspect :progressive}}} "imperfect"
   {:synsem {:sem {:tense :past :aspect :perfect}}} "past"
   {:synsem {:sem {:tense :present}}} "present"})

(def tenses-as-editables
  [{:label "conditional"
    :value (json/write-str {:synsem {:sem {:tense :conditional}}})}

   {:label "future"
    :value (json/write-str {:synsem {:sem {:tense :futuro}}})}

   {:label "imperfect"
    :value (json/write-str {:synsem {:sem {:tense :past
                                           :aspect :progressive}}})}
   {:label "past"
    :value (json/write-str {:synsem {:sem {:tense :past
                                           :aspect :perfect}}})}
   {:label "present"
    :value (json/write-str {:synsem {:sem {:tense :present}}})}])

;; TODO: remove need for this
(def tenses-as-editables-french
  [{:label "conditional"
    :value (json/write-str {:synsem {:sem {:tense :conditional}}})}
   {:label "future"
    :value (json/write-str {:synsem {:sem {:tense :futuro}}})}
   {:label "present"
    :value (json/write-str {:synsem {:sem {:tense :present}}})}])

;; TODO: throw exception rather than "unknown language"
(defn short-language-name-to-edn [lang]
  (cond (= lang "en") :english
        (= lang "es") :espanol
        (= lang "fr") :français
        (= lang "it") :italiano
        true (str "unknown lang: " lang)))

(defn default-city-for-language [lang]
  (cond (= lang "es") "Barcelona"
        (= lang "fr") "Paris"
        (= lang "it") "Firenze"
        true (str "unknown lang: " lang)))

(def default-source-language "en")

  
