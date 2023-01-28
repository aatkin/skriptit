(ns bb-utils
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def ^:dynamic *debug*)

(defn relative-file
  "Returns file relative to currently executed file."
  [s]
  (fs/file (fs/parent *file*) s))

(defn debug-println [& xs]
  (when (and (bound? #'*debug*) *debug*)
    (apply println "DEBUG:" xs)))

(defn write-to-file
  "Create temp dir and write data to filename in temp dir. Returns File."
  [data filename]
  (let [temp-dir (fs/create-temp-dir)
        path (fs/path temp-dir filename)]
    (fs/write-bytes path data)
    (debug-println #'write-to-file "wrote bytes to" (str path))
    (fs/file path)))

(defn get-file-list [dir]
  (let [+dir-path+ (str dir "/")
        to-file-path #(-> % str (str/replace-first +dir-path+ ""))]
    (->> (fs/list-dir dir)
         (into #{} (map to-file-path)))))

(defn unzip-bytes [{:keys [data]}]
  (let [temp-dir (fs/create-temp-dir)]
    (-> data
        (write-to-file (str "unzip_" (System/currentTimeMillis)))
        (fs/unzip temp-dir))
    (let [file-list (get-file-list temp-dir)]
      (debug-println #'unzip-bytes "unzipped files" file-list)
      {:dir temp-dir
       :file-list file-list})))

(defn slurp-if-exists [filename]
  (let [maybe-file (relative-file filename)]
    (when (.exists maybe-file)
      (slurp maybe-file))))

