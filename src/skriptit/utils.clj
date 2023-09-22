(ns skriptit.utils
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

(defn get-project-root-path []
  (let [dir (System/getenv "BB_SCRIPTS")]
    (assert (not (str/blank? dir))
            "please set environment variable $BB_SCRIPTS first (e.g. to git repo root)")
    dir))

(defn print-help [option-specs]
  (println "Available options:")
  (doseq [opt option-specs]
    (apply println "\t" opt)))

(defn parse-cli [args option-specs]
  (let [parsed-args (parse-opts args option-specs)
        errors (:errors parsed-args)
        unknown-args (:arguments parsed-args)]
    (cond
      (seq errors) (do
                     (apply println "errors parsing options:" errors)
                     (print-help option-specs))
      (seq unknown-args) (do
                           (apply println "unrecognized arguments:" unknown-args)
                           (print-help option-specs))
      :else (:options parsed-args))))

(defn slurp-edn [x]
  (-> (slurp x)
      (edn/read-string)))

(defn slurp-json [x]
  (-> (slurp x)
      (json/parse-string true)))

(defn write-to-file
  "Create temp dir and write data to filename in temp dir. Returns File."
  [data filename]
  (let [temp-dir (fs/create-temp-dir)
        path (fs/path temp-dir filename)]
    (fs/write-bytes path data)
    (println #'write-to-file "wrote bytes to" (str path))
    (fs/file path)))

(defn- to-permission [x]
  (cond
    (seq x)
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
      (println #'unzip-bytes "unzipped files" file-list)
      {:dir temp-dir
       :file-list file-list})))

(defn parse-int [x]
  (some-> x str (Integer/parseInt)))

(defn plus-keywords [& args]
  (->> args
       (into [] (mapcat (fn [s]
                          [s (str ":" s)])))))

(defn extract-vargs
  "If first element of `args` coll is sequential, return that element instead.
   Useful for working with variable argument functions"
  [args]
  (if (sequential? (first args))
    (first args)
    args))
