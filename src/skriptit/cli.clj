(ns skriptit.cli
  (:require [clojure.string :as str]
            [babashka.process :refer [process shell]]))

(def print-cmd {:pre-start-fn (comp (partial apply println) :cmd)})

(defn wrap-quotes [s]
  (str "\"" s "\""))

(defn wrap-vec [& args]
  (str "[" (str/join " " (remove nil? args)) "]"))

(defn lein-dep [dependency & [version]]
  (wrap-vec dependency
            (some-> version (wrap-quotes))))

(defn edit-or-read [s]
  (let [cmd (some #{"code" "vim"} *command-line-args*)]
    (shell print-cmd (or cmd "less") s)))

;; find processes by name
(defn psgrep [cli-args]
  (-> (process "ps aux")
      (process "grep" (first cli-args))
      (process "grep -v grep")
      (shell "awk" "{ print $2, $10, $11 }")))

(defn gpg [cli-args]
  (case (first cli-args)
    "start" (shell print-cmd "gpg-connect-agent" "updatestartuptty" "/bye" ">" "/dev/null")
    "stop" (shell print-cmd "gpgconf" "--kill" "gpg-agent")
    "restart" (shell print-cmd "gpg-connect-agent" "reloadagent" "/bye")))

