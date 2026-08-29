(ns skriptit.dirb
  (:require [babashka.fs :as fs]
            [skriptit.cli :as cli]
            [skriptit.config :as config]
            [skriptit.db :as db]))

(defn- get-db! []
  (db/get-db! (config/database-path "dirb")))

(defn list-entries!
  "List directory bookmarks. Missing paths are marked instead of aborting the list."
  {:skriptit/cmd "list"}
  []
  (doseq [[key entry] (db/entries (get-db!))
          :let [path (db/entry->path entry)]]
    (println (str key " -> " path
                  (when-not (fs/directory? path) " [missing]")))))

(defn read-entry!
  "Print the directory stored under key."
  {:skriptit/cmd "read"
   :skriptit/args "<key>"}
  [key]
  (let [path (db/getx-entry (get-db!) key)]
    (when-not (fs/directory? path)
      (throw (ex-info "Bookmark does not point to a directory"
                      {:key key :path path})))
    (println path)))

(defn save-cwd!
  "Save the current working directory under key."
  {:skriptit/cmd "save"
   :skriptit/args "<key>"}
  [key]
  (db/write! (get-db!) key (db/path->entry (System/getProperty "user.dir"))))

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
  ([] (run! println (cli/find-autocomplete-cmds 'skriptit.dirb)))
  ([_entries] (run! println (keys (db/entries (get-db!))))))
