(ns bb-utils
  (:require [babashka.fs :as fs]
            [clojure.string]))

(def ^:dynamic *debug*)

(defn debug-println [& xs]
  (when (and (bound? #'*debug*) *debug*)
    (-> (partial println "DEBUG:")
        (apply xs))))

(defn unzip-bytes [{:keys [data]}]
  (let [temp-dir (fs/create-temp-dir)
        filename (str "unzip_" (System/currentTimeMillis))
        path (fs/path temp-dir filename)
        _ (fs/write-bytes path data)
        _ (debug-println "wrote bytes to" (str path))
        _ (fs/unzip (fs/file path) temp-dir)
        files (->> (fs/list-dir temp-dir)
                   ; trim leading temp dir paths
                   (map #(clojure.string/replace-first (str %) (str temp-dir "/") ""))
                   (remove #{filename}))
        _ (debug-println "unzipped files" files)]
    {:dir temp-dir
     :file-list files}))
