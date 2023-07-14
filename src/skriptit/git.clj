(ns skriptit.git
  (:require [babashka.process :refer [shell]]
            #_[clojure.string :as str]))

(defn inside-git-repo? []
  (= 0 (:exit (shell {:continue true
                      :err :string
                      :out :string}
                     "git status"
                     "&>/dev/null"))))

(defn find-tags []
  (when (inside-git-repo?)
    (-> (shell {:out :string}
               "git describe"
               "--tags"
               "--abbrev=0")
        :out
        #_str/split-lines)))

(defn short-hash []
  (when (inside-git-repo?)
    (-> (shell {:out :string}
               "git rev-parse"
               "--short"
               "HEAD")
        :out)))

(defn cli [cli-args]
  (case (first cli-args)
    "find-tags" (some-> (find-tags) print)
    "short-hash" (some-> (short-hash) print)
    (println "cmds: find-tags short-hash")))
