(ns skriptit.utils
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(def +project-root-path+ (-> *file* ; root/src/skriptit/utils.clj
                             (fs/parent) ; root/src/skriptit
                             (fs/parent) ; root/src
                             (fs/parent))) ; root

(defn slurp-edn [x]
  (-> (slurp x)
      (edn/read-string)))

(defn write-to-file
  "Create temp dir and write data to filename in temp dir. Returns File."
  [data filename]
  (let [temp-dir (fs/create-temp-dir)
        path (fs/path temp-dir filename)]
    (fs/write-bytes path data)
    (timbre/debug #'write-to-file "wrote bytes to" (str path))
    (fs/file path)))

(defn- to-permission [x]
  (cond
    (set? x)
    (->> (map x #{:r :w :x})
         (map (comp name (fnil identity "-")))
         (str/join))

    (int? x)
    (case x
      1 "--x"
      2 "-w-"
      3 "-wx"
      4 "r--"
      5 "r-x"
      6 "rw-"
      7 "rwx")))

(defn chmod-file
  "Set posix file permissions on File f."
  [f permissions]
  (when f
    (->> (cond
           (string? permissions) permissions
           (map? permissions) (->> [permissions]
                                   (apply (juxt :owner :group :public))
                                   (mapv to-permission)
                                   (str/join))
           (vector? permissions) (->> permissions
                                      (mapv to-permission)
                                      (str/join)))
         (fs/set-posix-file-permissions f))))

(defn get-file-list [dir]
  (let [dir-path (str dir "/")
        to-filename #(str/replace-first % dir-path "")]
    (->> (fs/list-dir dir)
         (into #{} (map (comp to-filename str))))))

(defn unzip-bytes [{:keys [data]}]
  (let [temp-dir (fs/create-temp-dir)]
    (-> data
        (write-to-file (str "unzip_" (System/currentTimeMillis)))
        (fs/unzip temp-dir))
    (let [file-list (get-file-list temp-dir)]
      (timbre/debug #'unzip-bytes "unzipped files" file-list)
      {:dir temp-dir
       :file-list file-list})))

