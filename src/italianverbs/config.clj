(ns italianverbs.config
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [hiccup.core :refer (html)]
   [italianverbs.unify :refer [unify]]))

(def time-format "Dy Month FMDD YYYY HH24:MI TZ")

(declare short-language-name-to-edn)
(declare short-language-name-to-long)
(declare short-language-name-from-match)

(defn language-dropdown [language]
  (html
   [:div.dropdown {:style "margin-left:0.5em;float:left;width:auto"}
    "Show games for:"
    [:div {:style "float:right;padding-left:1em;width:auto;"}
     [:form {:method "get"}
      [:select#edit_which_language {:name "language"
                                    :onchange (str "document.location='/editor/' + this.value")}
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
             {:value "it" :label "Italiano"}])]]]]))

;; sqlnames: 'english','espanol' <- note: no accent on 'n' ,'italiano', ..
(defn sqlname-from-match [match-string]
  (cond (nil? match-string)
        (str "(no sqlname for language: (nil) detected).")
        (re-find #"espanol" (string/lower-case match-string))
        "espanol"
        (re-find #"español" (string/lower-case match-string))
        "espanol"
        (re-find #"english" (string/lower-case match-string))
        "english"
        (re-find #"italiano" (string/lower-case match-string))
        "italiano"
        (re-find #"italian" (string/lower-case match-string))
        "italiano"
        (re-find #"spanish" (string/lower-case match-string))
        "espanol"
        :else
        (str "(no sqlname for language:" match-string " detected.")))

(defn language-to-root-keyword [short-language-name]
  (sqlname-from-match (short-language-name-to-long short-language-name)))

(defn language-to-spec-path [short-language-name]
  "Take a language name like 'it' and turn it into an array like: [:root :italiano :italiano]."
  (let [language-keyword-name (language-to-root-keyword short-language-name)
        language-keyword (keyword language-keyword-name)]
    [:root language-keyword language-keyword]))

(defn language-to-root-spec [short-language-name root]
  "Take a language name like 'it' and a verb root and turn it into a map like: {:root {:italiano {:italiano <root>}}}."
  (let [language-keyword-name (language-to-root-keyword short-language-name)
        language-keyword (keyword language-keyword-name)]
    {:root {language-keyword {language-keyword root}}}))

;; TODO: throw exception rather than "???" for unknown languages.
(defn short-language-name-to-long [lang]
  (cond (= lang "it") "Italian"
        (= lang "en") "English"
        (= lang "es") "Spanish"
        true "???"))

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
