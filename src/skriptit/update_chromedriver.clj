(ns skriptit.update-chromedriver
  (:require [skriptit.utils :as utils]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [taoensso.timbre :as timbre]))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]
                  ["-f" "--force" "Force update"]
                  ["-y" "--yes" "Run script without having it ask any questions"]
                  ["-h" "--help" "Show this help"]])

(def +chromedriver-google-uri+ "https://chromedriver.storage.googleapis.com")
(def +version-file-path+ (str (fs/path (utils/get-project-root-path)
                                       "resources"
                                       "chromedriver_version.edn")))
(def +target-path+ (str (fs/path "/usr/local/bin" "chromedriver")))

(defn get-version-file []
  (when (.exists (fs/file +version-file-path+))
    (utils/slurp-edn +version-file-path+)))

(defn get-version []
  (let [current-version (:current (get-version-file))
        version (:body (curl/get (str +chromedriver-google-uri+ "/LATEST_RELEASE")))]
    (timbre/debug #'get-version :version version :current-version current-version)
    {:current current-version
     :new version}))

(def os-filenames
  {:mac/intel "chromedriver_mac64.zip"})

(defn get-chromedriver [version & [opts]]
  (let [uri (str +chromedriver-google-uri+ "/" version "/" (get os-filenames (:os opts)))
        response (curl/get uri {:as :bytes})
        _ (timbre/debug #'get-chromedriver :uri uri :response response)
        files (utils/unzip-bytes {:data (:body response)})]
    (assert (some #{"chromedriver"} (:file-list files))
            "unzipped contents must contain chromedriver")
    (fs/file (:dir files) "chromedriver")))

(defn ask-user-confirm! [version]
  (when (:new version)
    (println "latest chromedriver version:" (:new version) (str "(current: " (:current version) ")"))
    (println "NB: this will overwrite current file in" +target-path+)
    (println "do you want to proceed? (y/n)")
    (some #{"y" "yes"} (list (read-line)))))

(defn update-chromedriver! [version]
  (-> (:new version)
      (get-chromedriver {:os :mac/intel}) ; XXX: support other OS downloads
      (fs/copy +target-path+ {:replace-existing true})
      (fs/file)
      (utils/chmod-file {:owner #{:r :w :x}
                         :group #{:r :x}
                         :public #{:r :x}}))
  (timbre/info "wrote to" +target-path+)
  (spit +version-file-path+ {:current (:new version)})
  (timbre/debug "wrote to" (str (fs/absolutize +version-file-path+))))

(defn cli [{:keys [force] :as _opts}]
  (let [version (get-version)
        update? (not= (:current version) (:new version))]
    (cond
      (or update? force) (when (ask-user-confirm! version)
                           (update-chromedriver! version))
      :else (timbre/info "already on latest version:" (:current version)))))

(defn -main [cli-args]
  (let [args-m (utils/parse-cli cli-args cli-options)
        unknown-args (:arguments args-m)
        errors (:errors args-m)
        opts (:options args-m)]
    (cond
      (seq errors) (do
                     (apply println "errors parsing options:" errors)
                     (utils/print-help cli-options))
      (seq unknown-args) (do
                           (apply println "unrecognized arguments:" unknown-args)
                           (utils/print-help cli-options))
      (:help opts) (utils/print-help cli-options)
      :else (utils/with-logging opts :info
              (cli opts)))))

