(ns skriptit.git
  (:require [babashka.fs :as fs]
            [babashka.process :refer [process shell]]
            [skriptit.cli :refer [shell* shell-str]]
            [skriptit.utils :refer [andstr resource]]))

(defn has-git-repository? []
  (= 0 (:exit (shell {:continue true
                      :err :string
                      :out :string}
                     "git status"
                     "&>/dev/null"))))

(defn changed-files
  "TODO: description"
  {:skriptit/cmd "changed-files"
   :skriptit/flags ["-s | --staged"
                    "-c | --created"]}
  [& opts]
  (when (has-git-repository?)
    (let [staged? (some #{"--staged" "-s"} opts)
          created? (some #{"--created" "-c"} opts)
          unstaged-changes (when-not staged?
                             (-> (process "git status" "-s")
                                 (process "egrep" (str "(^ M )" (andstr "|" (when created? "(^\\?\\? )"))))))
          staged-changes (when staged?
                           (-> (process "git status" "-s")
                               (process "egrep" (str "(^M  )" (andstr "|" (when created? "(^A  )"))))))]
      (-> (or unstaged-changes staged-changes)
          (shell "awk" "{ print $2 }")))))

(defn find-tags
  "TODO: description"
  {:skriptit/cmd "find-tags"}
  []
  (when (has-git-repository?)
    (-> (shell-str "git describe"
                   "--tags"
                   "--abbrev=0")
        :out)))

(defn short-hash
  "TODO: description"
  {:skriptit/cmd "short-hash"}
  []
  (when (has-git-repository?)
    (-> (shell-str "git rev-parse"
                   "--short"
                   "HEAD")
        :out)))

(defn init-gitignore
  "TODO: description"
  {:skriptit/cmd "init-gitignore"}
  []
  (when (has-git-repository?)
    (when-not (fs/exists? ".local.gitignore")
      (let [template (resource "template.local.gitignore")
            target (fs/absolutize ".local.gitignore")]
        (println (format "Copying %s to %s" template target))
        (fs/copy template target)))
    (shell* "git config core.excludesFile .local.gitignore")))
