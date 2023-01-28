#!/usr/bin/env bb

(ns update-chromedriver
  (:require [bb-utils :as bbu]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [clojure.tools.cli :refer [parse-opts]]))

(def +version-file+ "chromedriver_version.txt")

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]
                  ["-f" "--force" "Force update"]])
(def opts (:options (parse-opts *command-line-args* cli-options)))

(defn update-version-file [version]
  (-> (bbu/relative-file +version-file+)
      (spit version)))

(defn get-chromedriver [version & [{:keys [os]}]]
  (let [os-suffix (-> {:mac/intel "/chromedriver_mac64.zip"}
                      (get os))
        uri (str "https://chromedriver.storage.googleapis.com/" version os-suffix)]
    (bbu/debug-println #'get-chromedriver "get uri" uri)
    (let [response (curl/get uri {:as :bytes})]
      (bbu/debug-println #'get-chromedriver "downloaded file:" response)
      (let [files (bbu/unzip-bytes {:data (:body response)})]
        (assert (some #{"chromedriver"} (:file-list files))
                "unzipped contents must contain chromedriver")
        files))))

(defn main []
  (binding [bbu/*debug* (:debug opts)]
    (bbu/debug-println #'main :main/start)

    (let [current-version (bbu/slurp-if-exists "chromedriver_version.txt")
          version (:body (curl/get "https://chromedriver.storage.googleapis.com/LATEST_RELEASE"))]
      (if (and (not (:force opts))
               (= current-version version))
        (println "already on latest version:" version)
        (do
          (println "latest chromedriver version:" version (str "(current: " current-version ")"))
          (println "NB: this will overwrite your current file in /usr/local/bin/chromedriver!")
          (println "do you want to proceed? (y/n)")
          (when (some #{"y" "yes"} (list (read-line)))
            (-> (get-chromedriver version {:os :mac/intel})
                (:dir) ; temp dir path
                (fs/file "chromedriver")
                (fs/copy "/usr/local/bin" {:replace-existing true}))
            (bbu/debug-println #'main "wrote to" (str (fs/path "/usr/local/bin" "chromedriver")))
            (update-version-file version)))))

    (bbu/debug-println #'main :main/end)))

(main)
