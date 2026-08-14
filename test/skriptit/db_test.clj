(ns skriptit.db-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [skriptit.db :as db]))

(deftest database-lifecycle
  (let [temp-dir (fs/create-temp-dir {:prefix "skriptit-test-"})
        database (fs/path temp-dir "nested" "bookmarks.edn")]
    (try
      (testing "opening creates an empty database and its parent directory"
        (is (= {} (db/entries database)))
        (is (fs/regular-file? database)))

      (testing "entries can be saved, resolved, renamed, and deleted"
        (let [saved (with-out-str
                      (db/write! database "workspace"
                                 (db/path->entry (str temp-dir))))]
          (is (= (str "saved workspace -> " (fs/absolutize temp-dir) "\n")
                 saved)))
        (is (= (str (fs/absolutize temp-dir))
               (db/getx-entry database "workspace")))
        (with-out-str (db/rename! database "workspace" "work"))
        (is (nil? (db/get-entry database "workspace")))
        (is (= (str (fs/absolutize temp-dir))
               (db/getx-entry database "work")))
        (with-out-str (db/delete! database "work"))
        (is (= {} (db/entries database))))
      (finally
        (fs/delete-tree temp-dir)))))

(deftest paths-are-stored-normalized
  (testing "redundant path segments are removed before storing"
    (is (= "/tmp/notes.md" (db/path->entry "/tmp/./notes.md")))
    (is (= "/tmp/notes.md" (db/path->entry "/tmp/archive/../notes.md"))))

  (testing "databases written before string entries still resolve"
    (is (= "/tmp/notes.md" (db/entry->path ["/" "tmp" "notes.md"])))))

(deftest invalid-bookmarks-are-reported
  (let [temp-dir (fs/create-temp-dir {:prefix "skriptit-test-"})
        database (fs/path temp-dir "bookmarks.edn")
        missing (fs/path temp-dir "missing")]
    (try
      (with-out-str
        (db/write! database "missing" (db/path->entry (str missing))))
      (is (= #{"missing"} (set (keys (db/invalid-entries database)))))
      (finally
        (fs/delete-tree temp-dir)))))
