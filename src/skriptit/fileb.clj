(ns skriptit.fileb
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [skriptit.cli :as cli]
            [skriptit.config :as config]
            [skriptit.db :as db]))

(defn- get-db! []
  (db/get-db! (config/database-path "fileb")))

(defn list-entries!
  "List file bookmarks. Missing paths are marked instead of aborting the list."
  {:skriptit/cmd "list"}
  []
  (doseq [[key entry] (db/entries (get-db!))]
    (let [path (db/entry->path entry)]
      (println (str key " -> " path
                    (when-not (fs/exists? path) " [missing]"))))))

(defn read-entry!
  "Print the path stored under key."
  {:skriptit/cmd "read"
   :skriptit/args "<key>"}
  [key]
  (println (db/getx-entry (get-db!) key)))

(defn save-entry!
  "Save path under key."
  {:skriptit/cmd "save"
   :skriptit/args "<key> <path>"}
  [key path]
  (db/write! (get-db!) key (db/path->entry path)))

(defn merge-defaults!
  "Merge :fileb entries from an EDN file. $VAR tokens are expanded."
  {:skriptit/cmd "merge-defaults"
   :skriptit/args "<path>"}
  [path]
  (reduce (fn [state [key value]]
            (db/write! state key
                       (db/path->entry (config/interpolate-env value))))
          (get-db!)
          (:fileb (edn/read-string (slurp path)))))

(defn remove-entry!
  "Remove the bookmark stored under key."
  {:skriptit/cmd "remove"
   :skriptit/args "<key>"}
  [key]
  (db/delete! (get-db!) key))

(defn rename-entry!
  "Rename key to new-key."
  {:skriptit/cmd "rename"
   :skriptit/args "<key> <new-key>"}
  [key new-key]
  (db/rename! (get-db!) key new-key))

(defn validate!
  "Check that every bookmarked path still exists."
  {:skriptit/cmd "validate"}
  []
  (db/validate! (get-db!)))

(defn fix!
  "Interactively remove bookmarks whose paths no longer exist."
  {:skriptit/cmd "fix"}
  []
  (db/clean-unused-entries! (get-db!)))

(defn autocomplete!
  "Print command names, or bookmark keys when passed `entries`."
  {:skriptit/cmd "autocomplete"
   :skriptit/args "[entries]"}
  ([] (run! println (cli/find-autocomplete-cmds 'skriptit.fileb)))
  ([_entries] (run! println (keys (db/entries (get-db!))))))
