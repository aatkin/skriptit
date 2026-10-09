(ns skriptit.cli
  (:require [clojure.string :as str]))

(def default-groups
  "The default command groups, as [prefix namespace-symbol] pairs.

  A private build extends the CLI by calling dispatch! with a larger vector
  from a -main of its own; see the README."
  [["dirb" 'skriptit.dirb]
   ["fileb" 'skriptit.fileb]])

(defn- cmd-name
  "Return the command name a var is registered under, or nil for a plain var."
  [command-var]
  (:skriptit/cmd (meta command-var)))

(defn- command-vars [namespace-symbol]
  (require namespace-symbol)
  (->> namespace-symbol
       find-ns
       ns-interns
       vals
       (filter cmd-name)
       (sort-by cmd-name)))

(defn find-autocomplete-cmds
  "Return the command names exposed by a namespace."
  [namespace-symbol]
  (map cmd-name (command-vars namespace-symbol)))

(defn- print-command-docs! [command-vars]
  (doseq [command-var command-vars
          :let [{:skriptit/keys [cmd args] :keys [doc]} (meta command-var)]]
    (println)
    (println (str/join " " (remove str/blank? [cmd args])))
    (println (apply str (repeat (count cmd) \-)))
    (println doc)))

(defn- arity-bounds
  "Return [minimum maximum] argument counts accepted by a command var.

  The maximum is nil for a variadic command."
  [command-var]
  (when-let [arglists (seq (:arglists (meta command-var)))]
    (let [fixed (map #(count (take-while (complement #{'&}) %)) arglists)]
      [(apply min fixed)
       (when-not (some (fn [arglist] (some #{'&} arglist)) arglists)
         (apply max fixed))])))

(defn- arity-phrase [minimum maximum]
  (let [arguments #(str % (if (= 1 %) " argument" " arguments"))]
    (cond
      (= 0 minimum maximum) "no arguments"
      (= minimum maximum) (arguments minimum)
      (nil? maximum) (str "at least " (arguments minimum))
      :else (str minimum " to " (arguments maximum)))))

(defn- validate-args
  "Return an error message when args cannot be applied to command-var."
  [command-var args]
  (when-let [[minimum maximum] (arity-bounds command-var)]
    (let [given (count args)]
      (when (or (< given minimum) (and maximum (> given maximum)))
        (str "`" (cmd-name command-var) "` takes "
             (arity-phrase minimum maximum) ", but got " given ".")))))

(defn- edit-distance
  "Return the Levenshtein distance between strings a and b."
  [a b]
  (peek
   (reduce (fn [previous [i a-char]]
             (reduce (fn [row [j b-char]]
                       (conj row (min (inc (peek row))
                                      (inc (nth previous (inc j)))
                                      (+ (nth previous j)
                                         (if (= a-char b-char) 0 1)))))
                     [(inc i)]
                     (map-indexed vector b)))
           (vec (range (inc (count b))))
           (map-indexed vector a))))

(defn- suggestion
  "Return the candidate word most plausibly meant, or nil.

  A candidate qualifies within two edits, or when word starts with it, as in
  `dias-dev` for `dias`. Ties go to the earlier candidate."
  [word candidates]
  (->> candidates
       (keep (fn [candidate]
               (let [distance (if (str/starts-with? word candidate)
                                0
                                (edit-distance word candidate))]
                 (when (and (<= distance 2) (< distance (count candidate)))
                   [distance candidate]))))
       (sort-by first)
       first
       second))

(defn- print-suggestion! [word candidates]
  (when-let [candidate (suggestion word candidates)]
    (println (str "Did you mean `" candidate "`?"))))

(defn run-namespace!
  "Dispatch args to a :skriptit/cmd var in namespace-symbol."
  [namespace-symbol args]
  (let [commands (command-vars namespace-symbol)
        command-name (first args)
        command-var (some #(when (= command-name (cmd-name %)) %) commands)]
    (cond
      (nil? command-name)
      (do (print-command-docs! commands) 0)

      (nil? command-var)
      (do
        (binding [*out* *err*]
          (println "Unknown command:" command-name)
          (print-suggestion! command-name (map cmd-name commands))
          (print-command-docs! commands))
        2)

      :else
      (let [command-args (rest args)]
        (if-let [message (validate-args command-var command-args)]
          (do
            (binding [*out* *err*]
              (println message)
              (print-command-docs! [command-var]))
            2)
          (do
            (apply command-var command-args)
            0))))))

(defn- group-namespace
  "Return the namespace symbol registered under prefix, or nil."
  [groups prefix]
  (some (fn [[group namespace-symbol]]
          (when (= prefix group) namespace-symbol))
        groups))

(defn- print-help! [groups args]
  (let [[prefix command-name] args]
    (cond
      (nil? prefix)
      (do
        (println "Usage: skriptit <group> <command> [arguments]")
        (println)
        (println "Command groups:")
        (doseq [[group _] groups]
          (println " " group))
        (println)
        (println "Run `skriptit help <group>` for commands in a group."))

      :else
      (if-let [namespace-symbol (group-namespace groups prefix)]
        (let [commands (command-vars namespace-symbol)
              selected (if command-name
                         (filter #(= command-name (cmd-name %)) commands)
                         commands)]
          (if (seq selected)
            (print-command-docs! selected)
            (throw (ex-info "Unknown command"
                            {:group prefix :command command-name}))))
        (throw (ex-info "Unknown command group" {:group prefix}))))))

(defn- autocomplete! [groups args]
  (if-let [prefix (first args)]
    (if-let [namespace-symbol (group-namespace groups prefix)]
      (do (run! println (find-autocomplete-cmds namespace-symbol)) 0)
      (do
        (binding [*out* *err*]
          (println "Unknown command group:" prefix))
        2))
    (do (run! println (map first groups)) 0)))

(def ^:private reserved-prefixes
  "Prefixes dispatch! answers itself, so no group can reach a command behind one."
  #{"help" "autocomplete"})

(defn- check-prefixes! [groups]
  (let [prefixes (map first groups)
        duplicates (->> prefixes
                        frequencies
                        (keep (fn [[prefix count]]
                                (when (> count 1) prefix)))
                        sort
                        seq)
        reserved (->> prefixes (filter reserved-prefixes) sort seq)]
    (when duplicates
      (throw (ex-info "Command prefixes must be unique"
                      {:duplicates duplicates})))
    (when reserved
      (throw (ex-info "Command prefixes are reserved by the CLI"
                      {:reserved reserved})))))

(defn dispatch!
  "Run skriptit CLI args against command groups; return a process exit status."
  ([args]
   (dispatch! args default-groups))
  ([args groups]
   (try
     (check-prefixes! groups)
     (let [[prefix & command-args] args]
       (cond
         (or (nil? prefix) (= "help" prefix))
         (do (print-help! groups command-args) 0)

         (= "autocomplete" prefix)
         (autocomplete! groups command-args)

         :else
         (if-let [namespace-symbol (group-namespace groups prefix)]
           (run-namespace! namespace-symbol command-args)
           (do
             (binding [*out* *err*]
               (println "Unknown command group:" prefix)
               (print-suggestion! prefix (conj (mapv first groups) "help"))
               (println "Run `skriptit help` to list command groups."))
             2))))
     (catch Exception exception
       (binding [*out* *err*]
         (println "skriptit:" (ex-message exception))
         (when-let [data (ex-data exception)]
           (println (pr-str data))))
       1))))

(defn -main [& args]
  (let [status (dispatch! args)]
    (when-not (zero? status)
      (System/exit status))))
