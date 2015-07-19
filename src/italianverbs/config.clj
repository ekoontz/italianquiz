(ns italianverbs.config
  (:require
;   [italianverbs.html :as html]
   [hiccup.core :refer (html)]))

(def time-format "Dy Month FMDD YYYY HH24:MI TZ")

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

