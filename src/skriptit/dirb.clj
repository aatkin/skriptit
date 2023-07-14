(ns skriptit.dirb
  (:require [babashka.fs :as fs]
            [skriptit.utils :as utils]))

(def +db-file-path+ (str (fs/path (utils/get-project-root-path)
                                  "resources"
                                  "dirb.edn")))

(defn write-db!
  ([m]
   (println :dirb/save (vec (sort (keys m))))
   (spit +db-file-path+ m)
   m)
  ([m k v]
   (or (cond
         (nil? k) (println :dirb/no-changes)
         (nil? v) (println :dirb/no-changes)
         :else (let [updated (assoc m k v)]
                 (println :dirb/save-entry (pr-str {k v}))
                 (spit +db-file-path+ updated)
                 updated))
       m)))

(defn read-db! []
  (when-not (.exists (fs/file +db-file-path+))
    (write-db! {}))
  (into (sorted-map) (utils/slurp-edn +db-file-path+)))

(defn cli [cli-args]
  (if (seq cli-args)
    (case (first cli-args)
      "autocomplete" (doseq [entry (read-db!)]
                       (println (key entry)))
      "save" (-> (read-db!)
                 (write-db! (second cli-args) (System/getenv "PWD")))
      "delete" (-> (read-db!)
                   (dissoc (second cli-args))
                   (write-db!))
      (some-> (read-db!)
              (get (first cli-args))
              (println)))
    (doseq [entry (read-db!)]
      (println (key entry) "->" (val entry)))))
