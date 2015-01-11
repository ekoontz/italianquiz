(ns italianverbs.engine
  (:refer-clojure :exclude [get-in merge])
  (:use [hiccup core page])
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [hiccup.page :refer (html5)]

   [italianverbs.cache :refer (create-index)]
   [italianverbs.forest :as forest]
   [italianverbs.html :refer (tablize)]
   [italianverbs.morphology :refer [fo fo-ps remove-parens]]
   [italianverbs.ug :refer (head-principle)]
   [italianverbs.unify :refer [fail? get-in merge strip-refs unify unifyc]]

   [italianverbs.english :as en]
   [italianverbs.italiano :as it]))

(declare lookup)
(declare generate-from-request)
(declare resolve-model)

(def routes
  (compojure/routes
   (GET "/lookup" request
       (lookup request))

  (GET "/generate" request
       (generate-from-request request))))

(defn generate [spec language-model]
  (let [spec (unify spec
                    {:synsem {:subcat '()}})
        language-model (if (future? language-model)
                         @language-model
                         language-model)]
    (forest/generate spec 
                     (:grammar language-model)
                     (:lexicon language-model)
                     (:index language-model))))

(defn generate-from-request [request]
  "respond to an HTTP client's request with a generated sentence, given the client's desired spec, language name, and language model name."
  (let [pred (keyword (get-in request [:params :pred] :top))
        spec (get-in request [:params :spec])
        spec (if spec (json/read-str spec
                                     :key-fn keyword
                                     :value-fn (fn [k v]
                                                 (cond (string? v)
                                                       (keyword v)
                                                       :else v)))
                 :top)

        lang (get-in request [:params :lang])
        model (resolve-model (get-in request [:params :model]) lang)
        debug (get-in request [:params :debug] false)
        unified (unify {:synsem {:sem {:pred pred}}}
                       spec)]
    (log/info (str "generate with pred: " pred "; lang: " lang))
    (let [expression (generate unified model)
          semantics (strip-refs (get-in expression [:synsem :sem]))
          results (merge
                   {:spec spec
                    :pred pred
                    (keyword lang) (fo expression)
                    :semantics semantics})]
      (log/info (str "fo of expression: " (fo expression)))
      (log/info (str "semantics of expression: " semantics))

      (if (not (= "true" (get-in request [:params :debug])))
        ;; non-debug mode:
        {:status 200
         :headers {"Content-Type" "application/json;charset=utf-8"
                   "Cache-Control" "no-cache, no-store, must-revalidate"
                   "Pragma" "no-cache"
                   "Expires" "0"}
         :body (json/write-str results)}

        {:status 200
         :headers {"Content-Type" "text/html;charset=utf-8"
                   "Cache-Control" "no-cache, no-store, must-revalidate"
                   "Pragma" "no-cache"
                   "Expires" "0"}
         :body (html
                [:head
                 [:title "generate: debug"]
                 (include-css "/css/fs.css")
               (include-css "/css/layout.css")
               (include-css "/css/quiz.css")
               (include-css "/css/style.css")
               (include-css "/css/debug.css")
               ]
              [:body
               [:div

                [:div.major

                 [:h2 "input"]

                 [:table
                  [:tr
                   [:th "spec"]
                   [:td
                    (tablize (json/read-str (get-in request [:params :spec])
                                            :key-fn keyword
                                            :value-fn (fn [k v]
                                                        (cond (string? v)
                                                              (keyword v)
                                                              :else v))))]]]]]


               [:div.major
                [:h2 "intermediate"]

                "intermediate stuff .."

                ]


               [:div.major
                [:h2 "output"]
                (tablize results)]

               [:div#request {:class "major"}
                [:h2 "request"]
                (tablize request)]

               ])}))))

(defn resolve-model [model lang]
  (cond 
        (= model "en-small")
        en/small
        (= model "it-small")
        it/small

        ;; defaults if no model is given
        (= lang "en")
        en/small

        (= lang "it")
        it/small

        true ;; TODO: throw exception "no language model" if we got here.
        en/small))

(def possible-preds [:top])

(def lang-to-lexicon
  {"en" en/lexicon
   "it" it/lexicon})

(defn lookup [request]
  (let [lang (get-in request [:params :lang] "en") ;; if no lang specified, use english.
        lexicon (lang-to-lexicon lang)
        spec (if (not (= :null (get-in request [:params :spec] :null)))
               (json/read-str (get-in request [:params :spec])
                              :key-fn keyword
                              :value-fn (fn [k v]
                                          (cond (string? v)
                                                (keyword v)
                                                :else v)))
               :fail)

        intermediate
        (into {}
              (for [[k v] @lexicon]
                (let [filtered-v
                      (filter #(and (not (= true (get-in % [:synsem :aux]))) ;; filter out aux verbs.
                                    (not (fail? (unifyc % spec))))
                              v)]
                  (if (not (empty? filtered-v))
                    [k filtered-v]))))

        results
        {(keyword lang)
         (string/join "," (sort (keys intermediate)))}]

    (if (not (= "true" (get-in request [:params :debug])))
      ;; non-debug mode:
      {:status 200
       :headers {"Content-Type" "application/json;charset=utf-8"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma" "no-cache"
                 "Expires" "0"}
       :body (json/write-str results)}

      ;; debug mode:
      {:status 200
       :headers {"Content-Type" "text/html;charset=utf-8"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma" "no-cache"
                 "Expires" "0"}
       :body (html
              [:head
               [:title "lookup: debug"]
               (include-css "/css/fs.css")
               (include-css "/css/layout.css")
               (include-css "/css/quiz.css")
               (include-css "/css/style.css")
               (include-css "/css/debug.css")
               ]
              [:body
               [:div

                [:div.major

                 [:h2 "input"]

                 [:table
                  [:tr
                   [:th "spec"]
                   [:td
                    (tablize (json/read-str (get-in request [:params :spec])
                                            :key-fn keyword
                                            :value-fn (fn [k v]
                                                        (cond (string? v)
                                                              (keyword v)
                                                              :else v))))]]]]]


               [:div.major
                [:h2 "intermediate"]
                [:table
                (map #(html [:tr 
                             [:th.intermediate %]
                             [:td.intermediate (map (fn [each-val]
                                                      (html [:div.intermediate (tablize each-val)]))
                                                    (get intermediate %))]])
                     (keys intermediate))]]

               [:div.major
                [:h2 "output"]
                [:pre (json/write-str results)]]

               [:div#request {:class "major"}
                [:h2 "request"]
                (tablize request)]

               ])})))




