(ns skriptit.git
  (:require [clojure.pprint]
            [clojure.string :as str]
            [babashka.process :refer [process shell]]
            [skriptit.utils :as utils]))

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

(defn changed-files [opts]
  (when (inside-git-repo?)
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

(defn cli [cli-args]
  (case (first cli-args)
    "changed-files" (changed-files (rest cli-args))
    "find-tags" (some-> (find-tags) print)
    "short-hash" (some-> (short-hash) print)
    (clojure.pprint/print-table
     [:cmd :flags]
     [{:cmd "changed-files" :flags "[-s | --staged] [-c | --created]"}
      {:cmd "find-tags"}
      {:cmd "short-hash"}])))
