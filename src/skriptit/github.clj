(ns skriptit.github
  (:require [babashka.process :refer [shell]]
            [clojure.pprint]
            [clojure.string :as str]))

(def ^:private issue-template
  {:markdown (str "{{if .labels}}"
                  "[{{.title}} #{{.number}} ({{.labels | pluck \"name\" | join \", \"}})]({{.url}})"
                  "{{else}}"
                  "[{{.title}} #{{.number}}]({{.url}})"
                  "{{end}}")
   :url "{{.url}}"})

(defn view-issue [issue & args]
  (let [args (partition 2 args)
        template (some->> args
                          (some #(when (= "--template" (first %))
                                   (second %)))
                          keyword)
        s (-> (shell {:out :string}
                     "gh issue view" issue
                     "--json" "labels,number,title,url"
                     "--template" (get issue-template (or template :markdown)))
              :out
              str/split-lines
              first)]
    (println s)))

(defn cli [cli-args]
  (let [cmd (first cli-args)
        opts (rest cli-args)]
    (case cmd
      "issue" (apply view-issue opts)
      (clojure.pprint/print-table
       [:cmd :flags]
       [{:cmd "issue" :flags "[--template]"}]))))
