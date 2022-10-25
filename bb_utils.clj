(ns bb-utils
  (:require [babashka.fs :as fs]
            [clojure.string]))

(def ^:dynamic *debug*)

(defn debug-println [& xs]
  (when (and (bound? #'*debug*) *debug*)
    (-> (partial println "DEBUG:")
        (apply xs))))

(defn unzip-bytes [{:keys [data filename]}]
  (let [temp-dir (fs/create-temp-dir)
        path (fs/path temp-dir filename)]
    (fs/write-bytes path data)
    (debug-println "wrote bytes to" (str path))
    (fs/unzip (fs/file path) temp-dir)
    (let [files (->> (fs/list-dir temp-dir)
                     ; trim leading temp dir paths
                     (map #(clojure.string/replace-first (str %) (str temp-dir "/") ""))
                     (remove #{filename}))]
      (debug-println "unzipped files" files)
      {:dir temp-dir
       :file-list files})))
