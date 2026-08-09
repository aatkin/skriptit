(ns skriptit.config
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn data-dir
  "Return the directory for machine-local skriptit state.

  SKRIPTIT_DATA_DIR wins, then XDG_DATA_HOME/skriptit, then
  ~/.local/share/skriptit."
  []
  (let [explicit (System/getenv "SKRIPTIT_DATA_DIR")
        xdg-home (System/getenv "XDG_DATA_HOME")
        user-home (System/getenv "HOME")]
    (cond
      (not (str/blank? explicit)) (fs/expand-home explicit)
      (not (str/blank? xdg-home)) (fs/path xdg-home "skriptit")
      (not (str/blank? user-home)) (fs/path user-home ".local" "share" "skriptit")
      :else (throw (ex-info "Cannot locate skriptit data directory"
                            {:expected-env ["SKRIPTIT_DATA_DIR"
                                            "XDG_DATA_HOME"
                                            "HOME"]})))))

(defn database-path [name]
  (fs/path (data-dir) (str name ".edn")))

(defn interpolate-env
  "Replace $VAR tokens in s. Throw when a referenced variable is unset."
  [s]
  (when-not (string? s)
    (throw (ex-info "Expected a path string" {:value s})))
  (str/replace s #"\$([A-Za-z_][A-Za-z0-9_]*)"
               (fn [[token variable]]
                 (or (System/getenv variable)
                     (throw (ex-info (str "Environment variable is not set: " token)
                                     {:variable variable}))))))
