(ns skriptit.cli
  (:require [clojure.pprint]
            [clojure.string :as str]
            [babashka.process :refer [process shell]]
            [skriptit.utils :as utils]))

(defn- print-cmd [process-opts]
  (apply println "cmd:" (:cmd process-opts)))

(defn- extract-vargs [args]
  (if (sequential? (first args))
    (first args)
    args))

(defn cmd-meta [fn-symbol]
  (let [m (meta fn-symbol)]
    {:cmd (:skriptit/cmd m)
     :params (:skriptit/params m)
     :flags (:skriptit/flags m)}))

(defn run [fns cli-args]
  (let [cmd (first cli-args)
        opts (rest cli-args)
        get-cmd (comp #{cmd} :cmd cmd-meta)]
    (if-some [f (utils/find-first get-cmd fns)]
      (apply f opts)
      (clojure.pprint/print-table (mapv cmd-meta fns)))))

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

(defn quote-str [s]
  (str "\"" s "\""))

(defn quote-vec [& args]
  (str "[" (str/join " " (remove nil? args)) "]"))

(defn lein-dep [dependency & [version]]
  (quote-vec dependency
             (some-> version quote-str)))

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
