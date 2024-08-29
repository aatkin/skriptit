(ns skriptit.git
  (:require [clojure.pprint]
            [clojure.string :as str]
            [babashka.process :refer [process shell]]
            [skriptit.cli :refer [shell-str]]))

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
  [opts]
  (when (has-git-repository?)
    (let [staged? (some #{"--staged" "-s"} opts)
          created? (some #{"--created" "-c"} opts)
          changed "^ M "
          changed-staged "^M  "
          created (when created?
                    "^\\?\\? ")
          created-staged (when created?
                           "^A  ")]
      (-> (process "git status" "-s")
          (process "egrep" (str/join "|" (for [grep-line (if staged?
                                                           [changed-staged created-staged]
                                                           [changed created])
                                               :when grep-line]
                                           (str "(" grep-line ")"))))
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

(defn git-ignore
  "Open global .gitignore file. Defaults to `less`"
  {:skriptit/cmd "ignore"
   :skriptit/args "[:open-with]"}
  []
  (skriptit.cli/edit-or-read (skriptit.cli/get-env "GIT_IGNORE")))

(defn git-config
  "Open global .gitconfig file. Defaults to `less`"
  {:skriptit/cmd "config"
   :skriptit/args "[:open-with]"}
  []
  (skriptit.cli/edit-or-read (skriptit.cli/get-env "GIT_CONFIG")))
