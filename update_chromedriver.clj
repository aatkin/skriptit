(ns update-chromedriver
  (:require [bb-utils :as bb-utils]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [clojure.tools.cli :refer [parse-opts]]))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]])
(def opts (:options (parse-opts *command-line-args* cli-options)))
(def version-file "chromedriver_version.txt")
(def current-version (when (.exists (fs/file version-file))
                       (slurp version-file)))

(binding [bb-utils/*debug* (:debug opts)]
  (bb-utils/debug-println ::start)

  (def version (:body (curl/get "https://chromedriver.storage.googleapis.com/LATEST_RELEASE")))
  (spit version-file version)
  (def uri (str "https://chromedriver.storage.googleapis.com/" version "/chromedriver_mac64.zip"))

  (if (= current-version version)
    (println "already on latest version:" version)
    (do
      (println "latest chromedriver version:" version)
      (println "NB: this will overwrite your current file in /usr/local/bin/chromedriver!")
      (println "do you want to proceed? (y/n)")
      (when (some #{"y" "yes"} (list (read-line)))
        (let [response (curl/get uri {:as :bytes})
              _ (bb-utils/debug-println "downloaded file:" response)
              files (bb-utils/unzip-bytes {:data (:body response)})]
          (assert (some #{"chromedriver"} (:file-list files))
                  "unzipped contents must contain chromedriver")
          (-> (fs/file (:dir files) "chromedriver")
              (fs/copy "/usr/local/bin" {:replace-existing true}))
          (bb-utils/debug-println "wrote to" (str (fs/path "/usr/local/bin" "chromedriver")))))))

  (bb-utils/debug-println ::end))
