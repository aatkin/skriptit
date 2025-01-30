(ns skriptit.github
  (:require [clojure.pprint]
            [clojure.string :as str]
            [skriptit.cli :refer [shell-str]]))

(defn md-url [& s]
  (str "[" (str/join "" s) "]" "({{.url}})"))

(defn t-if
  "Template if-else"
  [condition then & [else]]
  (let [then-else (->> [then else]
                       (remove str/blank?)
                       (str/join " "))]
    (str "{{if" condition "}}" then-else "{{end}}")))

(defn t-pipe
  "Template pipe"
  [p1 p2 & pipes]
  (str "{{" (str/join " | " (concat [p1 p2] pipes)) "}}"))

(defn join-labels-by-name [sep]
  (t-pipe ".labels"
          "pluck \"name\""
          (str "join \"" sep "\"")))

(defn view-issue
  "View Github issue as markdown link `[.title .issue-number (.labels)](.url)`"
  {:skriptit/cmd "view-issue"
   :skriptit/args ":issue-number"}
  [issue-number & args]
  (assert (some? issue-number) "Missing required args: issue-number")
  (let [s (-> (shell-str "gh issue view" issue-number
                         "--json" "labels,number,title,url"
                         "--template" (md-url "{{.title}} #{{.number}}"
                                              (t-if ".labels"
                                                    (str " (labels: " (join-labels-by-name ", ") ")"))))
              :out
              str/split-lines
              first)]
    (println s)))

(defn list-issues
  "Get a list of all GitHub issues as JSON. Optionally specify limit."
  {:skriptit/cmd "list-issues"
   :skriptit/args "[:limit]"}
  [& [limit]]
  (let [result (apply shell-str
                      "gh issue list"
                      "--state" "all"
                      "--json" "author,labels,number,title,state,createdAt,updatedAt,comments"
                      "--jq" ".[] | {author: .author.login, number, title, state, createdAt, updatedAt, labels: .labels | map(.name), comments: .comments | map({author: .author.login, body, createdAt, updatedAt})}"
                      (concat []
                              (when (some? limit) ["--limit" limit])))]
    (println (:out result))))

(defn view-pr
  "View Github pull request as markdown link `[.title .pr-number (.author.login)](.url)`"
  {:skriptit/cmd "view-pr"
   :skriptit/args ":pr-number"}
  [pr-number & args]
  (assert (some? pr-number) "Missing required args: pr-number")
  (let [s (-> (shell-str "gh pr view" pr-number
                         "--json" "author,labels,number,title,url"
                         "--template" (md-url "{{.title}} #{{.number}}"
                                              "(by: {{.author.login}})"))
              :out
              str/split-lines
              first)]
    (println s)))
