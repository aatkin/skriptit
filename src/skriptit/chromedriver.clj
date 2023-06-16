(ns skriptit.chromedriver
  (:require [skriptit.utils :as utils]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [taoensso.timbre :as timbre]))

(def +chromedriver-google-uri+ "https://chromedriver.storage.googleapis.com")
(def +version-file-path+ (str (fs/path (utils/get-project-root-path)
                                       "resources"
                                       "chromedriver_version.edn")))
(def +target-path+ (str (fs/path "/usr/local/bin" "chromedriver")))

(def ^:private url-for-os
  {:mac/intel "chromedriver_mac64.zip"})

(defn- download-and-unzip [version & [opts]]
  (let [uri (str +chromedriver-google-uri+
                 "/" version
                 "/" (get url-for-os (:os opts :mac/intel)))
        response (curl/get uri {:as :bytes})
        files (utils/unzip-bytes {:data (:body response)})]
    (assert (some #{"chromedriver"} (:file-list files))
            "expected unzipped contents to contain chromedriver")
    (fs/file (:dir files) "chromedriver")))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]
                  ["-f" "--force" "Force update"]
                  ["-y" "--yes" "Run script without having it ask any questions"]
                  ["-h" "--help" "Show this help"]])

(defn get-current-and-new-versions []
  (let [version-file (when (.exists (fs/file +version-file-path+))
                       (utils/slurp-edn +version-file-path+))
        current-version (:current version-file)
        version (:body (curl/get (str +chromedriver-google-uri+ "/LATEST_RELEASE")))]
    {:current current-version
     :new version}))

(defn- check-version [opts]
  (let [versions (get-current-and-new-versions)
        current-version (:current versions)
        new-version (:new versions)]
    (if (:force opts)
      new-version
      (if (= current-version new-version)
        (timbre/info "already on latest version:" current-version)
        (do
          (println "latest chromedriver version:" new-version (str "(current: " current-version ")"))
          (println "NB: this will overwrite current file in" +target-path+)
          (println "do you want to proceed? (y/n)")
          (when (some #{"y" "yes"} (list (read-line)))
            new-version))))))

(defn update-chromedriver! [cli-args]
  (let [opts (utils/parse-cli cli-args cli-options)]
    (when-some [new-version (check-version opts)]
      (-> new-version
          (download-and-unzip {:os :mac/intel}) ; XXX: support other OS downloads
          (fs/copy +target-path+ {:replace-existing true})
          (fs/file)
          (utils/chmod-file {:owner #{:r :w :x}
                             :group #{:r :x}
                             :public #{:r :x}}))
      (timbre/info "wrote to file" +target-path+)
      (spit +version-file-path+ {:current new-version}))))

