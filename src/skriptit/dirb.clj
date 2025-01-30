(ns skriptit.dirb
  (:require [babashka.fs :as fs]
            [skriptit.utils :as utils]))

(def ^:private +path+ (str (fs/path (utils/resources-dir!) "dirb.edn")))

(def ^:private db
  (delay
    ;; create empty db if it doesn't exist
    (when-not (fs/exists? +path+)
      (fs/create-file +path+)
      (-> (str (fs/absolutize +path+))
          (spit {})))
    ;; read db from file
    (into (sorted-map) (utils/slurp-edn +path+))))

(defn- write-to-db! [current-db k v]
  (assert (map? current-db) "Current database must be a map")
  (assert (and (some? k) (some? v)) "Key and value are required")
  (if (= v (get current-db k))
    (do
      (println :dirb/no-changes)
      current-db)

    (let [updated-db (assoc current-db k v)]
      (println :dirb/save-entry (pr-str {k v}))
      (spit +path+ updated-db)
      updated-db)))

(defn- delete-from-db! [current-db k]
  (assert (map? current-db) "Current database must be a map")
  (assert (some? k) "Key is required")
  (if-not (contains? current-db k)
    (do
      (println :dirb/no-changes)
      current-db)

    (let [updated-db (dissoc current-db k)]
      (println :dirb/remove-entry k)
      (spit +path+ updated-db)
      updated-db)))



(defn list-entries!
  "List all entries in the database."
  {:skriptit/cmd "list"}
  [& [current-db]]
  (doseq [entry (or current-db @db)]
    (println (key entry) "->" (val entry))))

(defn read-entry!
  "Read entry from the database."
  {:skriptit/cmd "read"
   :skriptit/args ":key"}
  [k & [current-db]]
  (println (get (or current-db @db) k)))

(defn save-cwd!
  "Save current working directory to the database."
  {:skriptit/cmd "save"
   :skriptit/args ":key"}
  [k & [current-db]]
  (write-to-db! (or current-db @db)
                k
                (System/getenv "PWD")))

(defn remove-entry!
  "Remove entry from the database."
  {:skriptit/cmd "remove"
   :skriptit/args ":key"}
  [k & [current-db]]
  (delete-from-db! (or current-db @db) k))

(defn autocomplete!
  "Get autocomplete suggestions for the shell."
  {:skriptit/cmd "autocomplete"}
  [& [current-db]]
  (doseq [entry (or current-db @db)]
    (println (key entry))))
