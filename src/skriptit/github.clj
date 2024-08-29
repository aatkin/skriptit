(ns skriptit.github
  (:require [babashka.process :refer [shell]]
            [clojure.pprint]
            [clojure.string :as str]))

(defn wrap-vec [s] (str "[" s "]"))
(defn wrap-parens [s] (str "(" s ")"))

(defn md-url [& s]
  (str (wrap-vec (apply str s))
       (wrap-parens "{{.url}}")))

(defn template-if [condition then & [else]]
  (str "{{if " condition "}}"
       then
       else
       "{{end}}"))

(defn template-pipe [p1 p2 & pipes]
  (str "{{" (str/join " | " (concat [p1 p2] pipes)) "}}"))

(defn view-issue
  "View Github issue as markdown link `[.title .issue-number (.labels)](.url)`"
  {:skriptit/cmd "view-issue"
   :skriptit/args ":issue-number"}
  [issue-number & args]
  (assert (some? issue-number) "Missing required args: issue-number")
  (let [s (-> (shell {:out :string}
                     "gh issue view" issue-number
                     "--json" "labels,number,title,url"
                     "--template" (md-url "{{.title}} #{{.number}}"
                                          (template-if ".labels"
                                                       (str " " (wrap-parens (str "labels: "
                                                                                  (template-pipe ".labels" "pluck \"name\"" "join \", \"")))))))
              :out
              str/split-lines
              first)]
    (println s)))

(defn view-pr
  "View Github pull request as markdown link `[.title .pr-number (.author.login)](.url)`"
  {:skriptit/cmd "view-pr"
   :skriptit/args ":pr-number"}
  [pr-number & args]
  (assert (some? pr-number) "Missing required args: pr-number")
  (let [s (-> (shell {:out :string}
                     "gh pr view" pr-number
                     "--json" "author,labels,number,title,url"
                     "--template" (md-url "{{.title}} #{{.number}} " (wrap-parens "by: {{.author.login}}")))
              :out
              str/split-lines
              first)]
    (println s)))
