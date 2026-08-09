(ns skriptit.bookmarks-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [skriptit.config :as config]
            [skriptit.dirb :as dirb]
            [skriptit.fileb :as fileb]))

(defn- with-temp-data-dir
  "Run body-fn against a throwaway data directory instead of the real one."
  [body-fn]
  (let [temp-dir (fs/create-temp-dir {:prefix "skriptit-test-"})]
    (try
      (with-redefs [config/data-dir (constantly temp-dir)]
        (body-fn temp-dir))
      (finally
        (fs/delete-tree temp-dir)))))

(defn- printed [f]
  (str/trim (with-out-str (f))))

(deftest dirb-round-trip
  (with-temp-data-dir
    (fn [_]
      (with-out-str (dirb/save-cwd! "here"))
      (is (= (System/getProperty "user.dir")
             (printed #(dirb/read-entry! "here"))))

      (testing "reading a bookmark that was never saved fails"
        (is (thrown-with-msg? Exception #"does not exist"
                              (dirb/read-entry! "absent")))))))

(deftest fileb-round-trip
  (with-temp-data-dir
    (fn [temp-dir]
      (let [file (fs/path temp-dir "notes.md")]
        (spit (str file) "contents")
        (with-out-str (fileb/save-entry! "notes" (str file)))
        (is (= (str file) (printed #(fileb/read-entry! "notes"))))

        (testing "a listing marks paths that have disappeared"
          (fs/delete file)
          (is (str/includes? (printed #(fileb/list-entries!)) "[missing]")))))))

(deftest merge-defaults-expands-variables
  (with-temp-data-dir
    (fn [temp-dir]
      (let [defaults (fs/path temp-dir "defaults.edn")
            home (System/getenv "HOME")]
        (spit (str defaults)
              (pr-str {:dirb {"dirb-home" "$HOME"}
                       :fileb {"fileb-home" "$HOME"}}))
        (with-out-str (dirb/merge-defaults! (str defaults)))
        (with-out-str (fileb/merge-defaults! (str defaults)))

        (is (= home (printed #(dirb/read-entry! "dirb-home"))))
        (is (= home (printed #(fileb/read-entry! "fileb-home"))))

        (testing "a group ignores the other group's defaults"
          (is (thrown-with-msg? Exception #"does not exist"
                                (dirb/read-entry! "fileb-home"))))))))

(deftest autocomplete-switches-between-commands-and-entries
  (with-temp-data-dir
    (fn [_]
      (with-out-str (dirb/save-cwd! "here"))
      (is (str/includes? (printed #(dirb/autocomplete!)) "merge-defaults"))
      (is (= "here" (printed #(dirb/autocomplete! "entries")))))))
