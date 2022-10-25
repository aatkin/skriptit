(ns update-chromedriver
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [clojure.tools.cli :refer [parse-opts]]))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]])
(def opts (:options (parse-opts *command-line-args* cli-options)))
(def debug? (:debug opts))

(defn debug-println [& xs]
  (when debug?
    (apply println (flatten (list "DEBUG:" xs)))))

(println ::start)

(def version (:body (curl/get "https://chromedriver.storage.googleapis.com/LATEST_RELEASE")))
(def uri (str "https://chromedriver.storage.googleapis.com/" version "/chromedriver_mac64.zip"))

(println "latest chromedriver version:" version)
(println "NB: this will overwrite your current file in /usr/local/bin/chromedriver!")
(println "do you want to proceed? (y/n)")
(def continue (read-line))
(when (or (= continue "y")
          (= continue "yes"))
  (def zip-file (:body (curl/get uri {:as :bytes})))
  (def temp-dir (fs/create-temp-dir))
  (def zip-path (str temp-dir "/chromedriver_latest.zip"))

  (fs/write-bytes zip-path zip-file)
  (debug-println "downloaded to" zip-path)

  (fs/unzip (fs/file zip-path) temp-dir)
  (debug-println "unzipped to" (str (fs/path temp-dir "chromedriver")))

  (-> (fs/file temp-dir "chromedriver")
      (fs/copy "/usr/local/bin" {:replace-existing true}))
  (println "wrote to" (str (fs/path "/usr/local/bin" "chromedriver"))))

(println ::end)
