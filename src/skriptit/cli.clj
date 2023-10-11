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

(defn edit-or-read [s]
  (let [cmd (some #{"code" "vim"} *command-line-args*)]
    (shell* (or cmd "less") s)))

(defn get-env
  "Read environment variable, throw if undefined."
  [k]
  (let [v (System/getenv k)]
    (assert (some? v) (str "Environment variable is not defined: $" k))
    v
    ))

(defn awk-print-columns [columns]
  (let [columns (partition 2 (interleave (map name columns)
                                         (map inc (range))))]
    (for [[col idx] columns]
      [col (str "$" idx)])))

(defn psgrep [cli-args]
  (let [grep (first cli-args)
        columns (->> ['user 'pid 'start 'time 'command]
                     (map name)
                     (str/join ","))
        ps (shell {:out :string} "ps" "-o" columns)]
    (shell {:in (:out ps)} "head" "-n" 1) ; print header
    (-> (process {:in (:out ps)} "grep" grep)
        (shell "grep" "-v" "grep") ; ignore grep itself
        )))

(defn gpg [cli-args]
  (case (first cli-args)
    "start" (shell* "gpg-connect-agent" "updatestartuptty" "/bye" ">" "/dev/null")
    "stop" (shell* "gpgconf" "--kill" "gpg-agent")
    "restart" (shell* "gpg-connect-agent" "reloadagent" "/bye")))
