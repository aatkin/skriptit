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

(defn view-issue
  "View Github issue using template format. Defaults to markdown link:
   `[.title .issue-number (.labels)](.url)`"
  {:skriptit/cmd "view-issue"
   :skriptit/args "issue-number"
   :skriptit/flags "--template"}
  [issue-number & args]
  (assert (some? issue-number) "Missing required args: issue-number")
  (let [args (partition 2 args)
        template (some->> args
                          (some #(when (= "--template" (first %))
                                   (second %)))
                          keyword)
        s (-> (shell {:out :string}
                     "gh issue view" issue-number
                     "--json" "labels,number,title,url"
                     "--template" (get issue-template (or template :markdown)))
              :out
              str/split-lines
              first)]
    (println s)))

(defn view-pr
  "View Github pull request using template format. Defaults to markdown link:
   `[.title .pr-number (.labels)](.url)`"
  {:skriptit/cmd "view-pr"
   :skriptit/args "pr-number"
   :skriptit/flags "--template"}
  [pr-number & args]
  (assert (some? pr-number) "Missing required args: pr-number")
  (let [args (partition 2 args)
        template (some->> args
                          (some #(when (= "--template" (first %))
                                   (second %)))
                          keyword)
        s (-> (shell {:out :string}
                     "gh pr view" pr-number
                     "--json" "labels,number,title,url"
                     "--template" (get issue-template (or template :markdown)))
              :out
              str/split-lines
              first)]
    (println s)))

#_(defn cli [cli-args]
  (let [cmd (first cli-args)
        opts (rest cli-args)]
    (case cmd
      "view-issue" (apply view-issue opts)
      (clojure.pprint/print-table
       [:cmd :flags]
       [{:cmd "view-issue" :flags "[--template]"}]))))

(def commands
  (list #'view-issue
        #'view-pr))
