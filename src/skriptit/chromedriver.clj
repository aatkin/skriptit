(ns skriptit.chromedriver
  (:require [skriptit.utils :as utils]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [clojure.string]))

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

(def +platforms+
  {"mac-x64" "chromedriver-mac-x64"
   "mac-arm64" "chromedriver-mac-arm64"
   "linux64" "chromedriver-linux64"
   "win32" "chromedriver-win32"
   "win64" "chromedriver-win64"})

(defn get-file-name [platform]
  (let [dir (get +platforms+ platform)]
    (assert dir (format "Platform %s not found in +platforms+" platform))
    (str dir "/chromedriver")))

(defn- download-and-unzip [{:keys [platform]}]
  (println 'download-and-unzip {:platform platform
                                :url (get @urls platform)})
  (let [response (curl/get (get @urls platform) {:as :bytes})
        files (utils/unzip-bytes (:body response))
        f (fs/file (:dir files) (get-file-name platform))]
    (assert (fs/exists? f) (str "expected unzipped contents to contain chromedriver"))
    f))

(def cli-options [["-f" "--force Force update"]
                  ["-y" "--yes Run script without having it ask any questions"]
                  ["-p" "--platform Platform to download"]])

(defn get-current-and-new-versions []
  (let [version-file (when (.exists (fs/file +version-file-path+))
                       (utils/slurp-edn +version-file-path+))
        current-version (:current version-file)
        version (:version @stable-channel)]
    {:current current-version
     :new version}))

(defn- check-version []
  (let [versions (get-current-and-new-versions)
        curr-version (:current versions)
        next-version (:new versions)]
    (if (= curr-version next-version)
      [:skip curr-version next-version]
      [:update curr-version next-version])))

(defn update-chromedriver! [new-version platform]
  (-> (download-and-unzip {:platform platform})
      (fs/copy +target-path+ {:replace-existing true})
      (fs/file)
      (utils/chmod-file {:owner #{:r :w :x}
                         :group #{:r :x}
                         :public #{:r :x}}))
  (println "wrote to file" +target-path+)
  (spit +version-file-path+ {:current new-version}))

(defn check-version-and-update! [cmd opts]
  (let [[action curr-version next-version] (check-version)
        force-update? (true? (:force opts))
        update? (= :update action)
        auto-update? (and update?
                          (true? (:yes opts)))
        platform (:platform opts)]
    (println (format "latest chromedriver version: %s (current: %s)"
                     next-version curr-version))
    (cond
      (not= "update" cmd) nil

      force-update?
      (do
        (println "NB: this will overwrite current file in" +target-path+)
        (println "--force flag set, proceeding automatically")
        (update-chromedriver! next-version platform))

      (= :skip action)
      (println "already on latest version!")

      auto-update?
      (do
        (println "NB: this will overwrite current file in" +target-path+)
        (println "option :yes set, proceeding automatically")
        (update-chromedriver! next-version platform))

      update?
      (do
        (println "NB: this will overwrite current file in" +target-path+)
        (println "do you want to proceed? (y/n)")
        (when (some #{"y" "yes"} (list (read-line)))
          (update-chromedriver! next-version platform))))))

(defn cli [cli-args]
  (let [cmd (first cli-args)
        parsed-opts (utils/parse-cli (rest cli-args) cli-options)]

    (utils/exit-on-falsy #(contains? +platforms+ (:platform parsed-opts))
                         {:platform {:valid-values (keys +platforms+)}})

    (check-version-and-update! cmd parsed-opts)))
