(ns skriptit.rems
  (:require [babashka.process :refer [shell]]
            [clojure.string :as str]
            [skriptit.cli :refer [lein-dep print-cmd wrap-vec]]
            [skriptit.utils :refer [parse-int]]))

;; code generated by ChatGPT v3.5. incredible times
(defn rems-tag [cli-args]
  (let [changelog (slurp "CHANGELOG.md")
        release-version (second (re-find #"## (v[\d.]+)" changelog))
        release-name (second (re-find #"## v[\d.]+ \"([^\"]+)\"" changelog))
        tag-command (str "git tag -a " release-version
                         " -m \"Release " release-version
                         ", \\\"" release-name "\\\"\"")]
    (println tag-command)))

(defn release-branch [cli-args]
  (let [version (-> (shell {:out :string}
                           "git describe"
                           "--tags"
                           "--abbrev=0")
                    :out
                    str/split-lines
                    first)
        major (->> version (re-find #"v[\d].+") second parse-int)
        minor (->> version (re-find #"v\d\.(.+)") second parse-int inc)
        branch (str "release-" major "." minor)]
    (println (pr-str {:current version
                      :next (str "v" major "." minor)}))
    (println "create branch:" branch)))

;; start postgres:13 container with existing volume for rems-dev local database
(defn rems-db [cli-args]
  (let [volume (or (first cli-args)
                   (System/getenv "REMS_DB_DOCKER_VOLUME"))]
    (assert (not (str/blank? volume)) "volume cannot be empty")
    (shell {:pre-start-fn print-cmd}
           "docker run"
           "--rm"
           "--name" "rems_test"
           "-v" (str volume ":/var/lib/postgresql/data")
           "-p" "127.0.0.1:5432:5432"
           "-d"
           "-e" "POSTGRES_HOST_AUTH_METHOD=trust"
           "postgres:13")))

;; :doc "start REMS in development mode with headless nREPL (mostly same startup code as Calva uses)"
;; XXX: this could be generic leiningen script to add nREPL dependencies?
;; XXX: something wrong with shadow-cljs and leiningen, does not work correctly
;; deps-shadow ["update-in" ":dependencies" "conj" (lein-dep "thheller/shadow-cljs" "2.19.0")]
;; repl-shadow ["update-in" "[:repl-options :nrepl-middleware]" "conj" (lein-dep "shadow.cljs.devtools.server.nrepl/middleware")]
(defn rems-dev [cli-args]
  (let [deps-nrepl ["update-in" :dependencies
                    "conj" (lein-dep "nrepl" "1.0.0")]
        plugins ["update-in" :plugins
                 "conj" (lein-dep "cider/cider-nrepl" "0.28.5")]
        repl-cider ["update-in" (wrap-vec :repl-options :nrepl-middleware)
                    "conj" (lein-dep "cider.nrepl/cider-middleware")]
        profiles "+dev,+portal,+snitch"
        cmd ["trampoline" "with-profile" profiles "repl" :headless]]
    (-> shell
        (apply {:pre-start-fn print-cmd}
               "lein" (->> (list deps-nrepl plugins repl-cider cmd)
                           (interpose ["--"])
                           (flatten))))))

(defn rems-shadow [cli-args]
  (let [repl-options ["-d" "cider/cider-nrepl:0.28.5"]
        cmd ["npx" "shadow-cljs" repl-options "watch" ":app"]]
    (-> shell
        (apply {:pre-start-fn print-cmd}
               (flatten cmd)))))

(defn rems-test [cli-args]
  (let [target (first cli-args)
        test-suite? (some #{"integration" "browser" "unit"} target)
        focus-meta? (str/starts-with? target ":feat/")
        cmd ["trampoline" "kaocha"]
        opts ["--watch"
              "--fail-fast"
              (cond
                test-suite? ["--reporter" "kaocha.report/documentation" target]
                focus-meta? ["--focus-meta" target]
                :else ["--focus" target])]]
    (-> shell
        (apply {:pre-start-fn print-cmd}
               "lein" (->> (list cmd opts)
                           (flatten))))))

(defn cli [cli-args]
  (let [cmd (first cli-args)
        opts (rest cli-args)]
    (case cmd
      "db" (rems-db opts)
      "dev" (rems-dev opts)
      "release" (release-branch opts)
      "shadow" (rems-shadow opts)
      "test" (rems-test opts)
      "tag" (rems-tag opts)
      (println "available commands: db dev release shadow test tag"))))

