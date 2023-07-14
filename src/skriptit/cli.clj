(ns skriptit.cli
  (:require [clojure.string :as str]
            [babashka.process :refer [process shell]]
            [skriptit.utils :refer [extract-vargs plus-keywords]]))

(defn print-cmd [process-opts]
  (apply println "cmd:" (:cmd process-opts)))

(defn shell*
  "As shell, but prints :cmd as default option."
  [& args]
  (let [args (filterv some? (extract-vargs args))
        opts? (first args)]
    (if (map? opts?)
      (apply shell
             (merge {:pre-start-fn print-cmd} opts?)
             (rest args))
      (apply shell
             {:pre-start-fn print-cmd}
             args))))

(defn wrap-quotes [s]
  (str "\"" s "\""))

(defn wrap-vec [& args]
  (str "[" (str/join " " (remove nil? args)) "]"))

(defn lein-dep [dependency & [version]]
  (wrap-vec dependency
            (some-> version (wrap-quotes))))

(defn edit-or-read [s]
  (let [cmd (some #{"code" "vim"} *command-line-args*)]
    (shell* (or cmd "less") s)))

;; find processes by name
(defn psgrep [cli-args]
  (-> (process "ps aux")
      (process "grep" (first cli-args))
      (process "grep -v grep")
      (shell "awk" "{ print $2, $10, $11 }")))

(defn gpg [cli-args]
  (case (first cli-args)
    "start" (shell* "gpg-connect-agent" "updatestartuptty" "/bye" ">" "/dev/null")
    "stop" (shell* "gpgconf" "--kill" "gpg-agent")
    "restart" (shell* "gpg-connect-agent" "reloadagent" "/bye")))

(defn arg=
  "Is `arg` one of given `matches`?"
  [arg & matches]
  (some #{arg} (-> matches extract-vargs plus-keywords)))
