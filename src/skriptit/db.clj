(ns skriptit.db
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [skriptit.util :refer [blank?]]))

(defn- persist! [path data]
  (fs/create-dirs (fs/parent path))
  (spit (str path) (str (pr-str (into (sorted-map) data)) "\n")))

(defn get-db!
  "Open an EDN map at path, creating an empty database when absent."
  [path]
  (let [path (fs/absolutize path)]
    (when-not (fs/exists? path)
      (persist! path {}))
    (let [data (edn/read-string (slurp (str path)))]
      (when-not (map? data)
        (throw (ex-info "Bookmark database must contain an EDN map"
                        {:path (str path)})))
      {:db (into (sorted-map) data)
       :path path})))

(defn- state [db-or-path]
  (if (map? db-or-path)
    db-or-path
    (get-db! db-or-path)))

(defn path->entry
  "Turn a user-supplied path into the absolute string stored in a database."
  [path]
  (when-not (string? path)
    (throw (ex-info "Bookmark path must be a string" {:path path})))
  (str (fs/normalize (fs/absolutize (fs/expand-home path)))))

(defn entry->path
  "Resolve a stored entry to an absolute path string.

  Databases written before bookmarks were stored as strings hold a vector of
  path components instead, so both shapes are accepted on read."
  [entry]
  (let [path (if (string? entry)
               (fs/expand-home entry)
               (apply fs/path (flatten [entry])))]
    (str (fs/normalize (fs/absolutize path)))))

(defn write! [db-or-path key value]
  (when (blank? key)
    (throw (ex-info "Bookmark key must be a non-empty string" {:key key})))
  (when (nil? value)
    (throw (ex-info "Bookmark value is required" {:key key})))
  (let [{:keys [db path] :as current} (state db-or-path)]
    (if (= value (get db key))
      current
      (let [updated (assoc db key value)]
        (persist! path updated)
        (println "saved" key "->" (entry->path value))
        (assoc current :db updated)))))

(defn delete! [db-or-path key]
  (let [{:keys [db path] :as current} (state db-or-path)]
    (if-not (contains? db key)
      current
      (let [updated (dissoc db key)]
        (persist! path updated)
        (println "removed" key)
        (assoc current :db updated)))))

(defn rename! [db-or-path key new-key]
  (when (blank? new-key)
    (throw (ex-info "New bookmark key must be a non-empty string"
                    {:key new-key})))
  (let [{:keys [db path] :as current} (state db-or-path)]
    (when-not (contains? db key)
      (throw (ex-info "Bookmark does not exist" {:key key})))
    (when (contains? db new-key)
      (throw (ex-info "A bookmark with the new key already exists"
                      {:key new-key})))
    (let [updated (-> db (dissoc key) (assoc new-key (get db key)))]
      (persist! path updated)
      (println "renamed" key "->" new-key)
      (assoc current :db updated))))

(defn entries [db-or-path]
  (:db (state db-or-path)))

(defn resolve-path [entry]
  (let [path (entry->path entry)]
    (when-not (fs/exists? path)
      (throw (ex-info "Bookmark path does not exist" {:path path})))
    path))

(defn get-entry [db-or-path key]
  (some-> (get (entries db-or-path) key) resolve-path))

(defn getx-entry [db-or-path key]
  (or (get-entry db-or-path key)
      (throw (ex-info "Bookmark does not exist" {:key key}))))

(defn invalid-entries [db-or-path]
  (into (sorted-map)
        (filter (fn [[_ entry]]
                  (try
                    (resolve-path entry)
                    false
                    (catch Exception _ true))))
        (entries db-or-path)))

(defn validate! [db-or-path]
  (let [invalid (invalid-entries db-or-path)]
    (if (seq invalid)
      (do
        (println "Invalid bookmarks:")
        (doseq [[key entry] invalid]
          (println key "->" (entry->path entry)))
        false)
      (do
        (println "All bookmarks are valid.")
        true))))

(defn clean-unused-entries! [db-or-path]
  (let [{:keys [db path] :as current} (state db-or-path)
        invalid (invalid-entries current)]
    (if-not (seq invalid)
      (do (println "All bookmarks are valid.") current)
      (do
        (println "Remove these invalid bookmarks?" (str (vec (keys invalid))) "[y/N]")
        (if (contains? #{"y" "yes"} (.toLowerCase (or (read-line) "")))
          (let [updated (apply dissoc db (keys invalid))]
            (persist! path updated)
            (assoc current :db updated))
          current)))))
