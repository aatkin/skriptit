(ns skriptit.dirb
  "Directory saving/changing utility, inspired by similarly named bash utility.
   Works smoother with some shell helpers: (bash example)

   s() {
       bb --config $BB_SCRIPTS/bb.edn $@
   }

   alias ss='s dirb save'

   sg() {
       if [[ ! -z \"$1\" ]]; then
           cd $(s dirb \"$1\")
       fi
   }
   ss skriptit # save current pwd
   cd /other/dir
   sg skriptit # go back to saved directory
  "
  (:require [babashka.fs :as fs]
            [skriptit.utils :as utils]))

(def +db-file-path+ (str (fs/path (utils/get-project-root-path)
                                  "resources"
                                  "dirb.edn")))

(defn write-db!
  ([m]
   (spit +db-file-path+ m))
  ([m k v]
   (println :dirb/new-entry {k v})
   (write-db! (assoc m k v))))

(defn read-db! []
  (when-not (.exists (fs/file +db-file-path+))
    (write-db! {}))
  (utils/slurp-edn +db-file-path+))

(defn cli [cli-args]
  (if (seq cli-args)
    (case (first cli-args)
      "save" (-> (read-db!)
                 (write-db! (second cli-args) (System/getenv "PWD")))
      (some-> (read-db!)
              (get (first cli-args))
              (println)))
    (println :dirb/current-entries (pr-str (read-db!)))))
