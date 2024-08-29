(ns skriptit.cli
  (:require [clojure.pprint]
            [clojure.set]
            [clojure.string :as str]
            [babashka.process :refer [process shell]]
            [skriptit.utils :as utils]))

(defn- print-cmd [process-opts]
  (apply println "cmd:" (:cmd process-opts)))

(defn- extract-vargs [args]
  (if (sequential? (first args))
    (first args)
    args))

(defn cmd-meta [f]
  (-> (meta f)
      (clojure.set/rename-keys
       {:skriptit/cmd :cmd
        :skriptit/args :args
        :skriptit/flags :flags})))

(defn print-docs [fns]
  (let [meta-fns (mapv cmd-meta fns)]
    (doseq [meta-fn meta-fns
            :let [cmd (:cmd meta-fn)
                  args (:args meta-fn)
                  flags (:flags meta-fn)]]
      (print "\n")
      (cond
        args (println cmd args)
        flags (println cmd (->> flags
                                (map (fn [s] (str "[" s "]")))
                                (str/join " ")))
        :else (println cmd))
      (println (str/join (repeat (count cmd) "-")))
      (println (:doc meta-fn)))))

(defn- examine-ns [ns-sym]
  (->> (the-ns ns-sym)
       ns-interns
       vals
       (filter (comp :skriptit/cmd meta))))

(defn run [f-namespace cli-args]
  (let [fns (examine-ns f-namespace)
        cmd (first cli-args)
        opts (rest cli-args)
        get-cmd (comp #{cmd} :cmd cmd-meta)]
    (if-some [f (utils/find-first get-cmd fns)]
      (apply f opts)
      (print-docs fns))))

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
  (let [columns (->> ['user 'pid 'start 'time 'command]
                     (map name)
                     (str/join ","))
        ps (:out (shell {:out :string}
                        "ps" "-o" columns))
        grep (shell {:in ps
                     :out :string}
                    "grep" (first cli-args))
        grepv (shell {:in (:out grep)
                      :out :string
                      :continue true}
                     "grep" "-v" "grep")]
    (when (zero? (:exit grepv))
      ;; print header
      (shell {:in ps} "head" "-n" 1)
      ;; ignore grep itself
      (println (:out grepv)))))

(defn gpg [cli-args]
  (case (first cli-args)
    "start" (shell* "gpg-connect-agent" "updatestartuptty" "/bye" ">" "/dev/null")
    "stop" (shell* "gpgconf" "--kill" "gpg-agent")
    "restart" (shell* "gpg-connect-agent" "reloadagent" "/bye")))
