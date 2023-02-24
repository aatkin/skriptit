(ns skriptit.update-chromedriver
  (:require [clojure.tools.cli :refer [parse-opts]]
            [skriptit.utils :as utils :refer [+project-root-path+]]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [taoensso.timbre :as timbre]))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]
                  ["-f" "--force" "Force update"]
                  ["-y" "--yes" "Run script without having it ask any questions"]
                  ["-h" "--help" "Show this help"]])
(defn parse-cli-options [args]
  (-> args
      (parse-opts cli-options)
      :options))

(def +version-file-path+ (str (-> +project-root-path+
                                  (fs/path "resources" "chromedriver_version.txt"))))
(def +bin-path+ "/usr/local/bin")

(defn check-version [{:keys [force]}]
  (timbre/debug :version-file/path (str +version-file-path+))
  (let [current-version (when (.exists (fs/file +version-file-path+))
                          (:current (utils/slurp-edn +version-file-path+)))
        version (:body (curl/get "https://chromedriver.storage.googleapis.com/LATEST_RELEASE"))
        update? (or force
                    (not= current-version version))]
    (timbre/debug {:version version
                   :current-version current-version
                   :update? update?})
    (cond-> {:current current-version}
      update? (assoc :new version))))

(defn update-version-file [version]
  (spit +version-file-path+ {:current (:new version)})
  (timbre/info "wrote to" (str (fs/absolutize +version-file-path+))))

(defn get-chromedriver [version & [{:keys [os]}]]
  (let [os-suffix (-> {:mac/intel "/chromedriver_mac64.zip"}
                      (get os))
        uri (str "https://chromedriver.storage.googleapis.com/" version os-suffix)]
    (timbre/debug #'get-chromedriver "get uri" uri)
    (let [response (curl/get uri {:as :bytes})]
      (timbre/debug #'get-chromedriver "downloaded file:" response)
      (let [files (utils/unzip-bytes {:data (:body response)})]
        (assert (some #{"chromedriver"} (:file-list files))
                "unzipped contents must contain chromedriver")
        (fs/file (:dir files) "chromedriver")))))

(defn ask-user-confirm! [version]
  (when (:new version)
    (println "latest chromedriver version:" (:new version) (str "(current: " (:current version) ")"))
    (println (str "NB: this will overwrite your current file in " +bin-path+ "/chromedriver"))
    (println "do you want to proceed? (y/n)")
    (some #{"y" "yes"} (list (read-line)))))

(defn get-logging-level [opts]
  (cond
    (:debug opts) :debug
    :else :info))

(defn -main [& args]
  (let [opts (parse-cli-options args)]
    (timbre/with-level (get-logging-level opts)
      (timbre/debug :main/start opts)
      (cond
        (:help opts) (do
                       (println "Available options:")
                       (doseq [option-spec cli-options]
                         (apply println option-spec)))

        :else (let [version (check-version opts)]
                (if (ask-user-confirm! version)
                  (do (-> (:new version)
                          (get-chromedriver {:os :mac/intel})
                          (fs/copy +bin-path+ {:replace-existing true})
                          (fs/file)
                          (utils/chmod-file {:owner #{:r :w :x}
                                             :group #{:r :x}
                                             :public #{:r :x}}))
                      (timbre/info "wrote to" (str (fs/path +bin-path+ "chromedriver")))
                      (update-version-file version))
                  (println "already on latest version:" (:current version)))))
      (timbre/debug :main/end))))

