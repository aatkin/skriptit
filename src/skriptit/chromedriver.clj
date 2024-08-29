(ns skriptit.chromedriver
  (:require [skriptit.utils :as utils]
            [babashka.curl :as curl]
            [babashka.fs :as fs]))

(def stable-channel
  (delay
    (-> (utils/slurp-json "https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions-with-downloads.json")
        (get-in [:channels :Stable]))))

(def urls
  (delay
    (into {} (for [driver (get-in @stable-channel [:downloads :chromedriver])
                   :let [platform (:platform driver)
                         url (:url driver)]]
               [platform url]))))

(def +version-file-path+ (str (fs/path (utils/get-project-root-path)
                                       "resources"
                                       "chromedriver_version.edn")))

(def +target-path+ (str (fs/path "/usr" "local" "bin" "chromedriver")))

(defn get-file-name [platform]
  (let [dir (case platform
              "mac-x64" "chromedriver-mac-x64"
              "mac-arm64" "chromedriver-mac-arm64"
              "linux64" "chromedriver-linux64"
              "win32" "chromedriver-win32"
              "win64" "chromedriver-win64")]
    (str dir "/chromedriver")))

(defn- download-and-unzip [{:keys [platform] :as _opts}]
  (let [response (curl/get (get @urls platform) {:as :bytes})
        files (utils/unzip-bytes (:body response))
        f (fs/file (:dir files) (get-file-name platform))]
    (assert (fs/exists? f) (str "expected unzipped contents to contain chromedriver"))
    f))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-f" "--force" "Force update"]
                  ["-y" "--yes" "Run script without having it ask any questions"]])

(defn get-current-and-new-versions []
  (let [version-file (when (.exists (fs/file +version-file-path+))
                       (utils/slurp-edn +version-file-path+))
        current-version (:current version-file)
        version (:version @stable-channel)]
    {:current current-version
     :new version}))

(defn- check-version [opts]
  (let [versions (get-current-and-new-versions)
        current-version (:current versions)
        new-version (:new versions)]
    (cond
      (:force opts)
      new-version

      (= current-version new-version)
      (println "already on latest version:" current-version)

      (:yes opts)
      (do
        (println "latest chromedriver version:" new-version (str "(current: " current-version ")"))
        (println "NB: this will overwrite current file in" +target-path+)
        (println "option :yes set, proceeding automatically")
        new-version)

      :else
      (do
        (println "latest chromedriver version:" new-version (str "(current: " current-version ")"))
        (println "NB: this will overwrite current file in" +target-path+)
        (println "do you want to proceed? (y/n)")
        (when (some #{"y" "yes"} (list (read-line)))
          new-version)))))

(defn update-chromedriver! [opts]
  (when-some [new-version (check-version opts)]
    (-> (download-and-unzip {:platform "mac-x64"}) ; XXX: support other OS downloads
        (fs/copy +target-path+ {:replace-existing true})
        (fs/file)
        (utils/chmod-file {:owner #{:r :w :x}
                           :group #{:r :x}
                           :public #{:r :x}}))
    (println "wrote to file" +target-path+)
    (spit +version-file-path+ {:current new-version})))

(defn cli [cli-args]
  (let [cmd (first cli-args)
        opts (rest cli-args)]
    (case cmd
      "update" (update-chromedriver! (utils/parse-cli opts cli-options))
      (println (get-current-and-new-versions)))))
