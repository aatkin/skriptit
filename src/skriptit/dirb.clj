(ns skriptit.dirb
  (:require [babashka.fs :as fs]
            [skriptit.utils :as utils]))

(def +db-file-path+ (str (fs/path (utils/resources-dir!) "dirb.edn")))

(when-not (fs/exists? +db-file-path+)
  (fs/create-file +db-file-path+)
  (-> (str (fs/absolutize +db-file-path+))
      (spit {})))

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
  (into (sorted-map) (utils/slurp-edn +db-file-path+)))

(defn cli [cli-args]
  (let [cmd (first cli-args)
        opts (rest cli-args)]
    (cond
      (and cmd (seq opts)) (case cmd
                             "save" (let [k (first opts)
                                          v (System/getenv "PWD")
                                          db (read-db!)]
                                      (write-db! db k v))
                             "delete" (let [k (first opts)
                                            db (dissoc (read-db!) k)]
                                        (write-db! db)))
      cmd (case cmd
            "autocomplete" (doseq [entry (read-db!)]
                             (println (key entry)))
            (some-> (read-db!)
                    (get (first cli-args))
                    (println)))

      :else (doseq [entry (read-db!)]
              (println (key entry) "->" (val entry))))))
