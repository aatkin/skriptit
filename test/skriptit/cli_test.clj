(ns skriptit.cli-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [skriptit.cli :as cli]))

(def ^:private extension-manifest
  (str (fs/path (System/getProperty "user.dir")
                "test" "fixtures" "extensions.edn")))

;; Every call passes an explicit manifest so that a SKRIPTIT_EXTENSIONS value in
;; the developer's own environment cannot change what these tests see.

(deftest public-command-groups
  (is (= [["dirb" 'skriptit.dirb]
          ["fileb" 'skriptit.fileb]]
         (cli/command-groups nil))))

(deftest help-and-errors
  (testing "help describes only the public groups"
    (let [output (with-out-str (is (zero? (cli/dispatch! ["help"] nil))))]
      (is (str/includes? output "dirb"))
      (is (str/includes? output "fileb"))))

  (testing "an unknown group returns a usage error"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["private"] nil))]
      (is (= 2 status))
      (is (str/includes? (str error) "Unknown command group")))))

(deftest argument-counts-are-checked
  (testing "a missing argument is reported before the command runs"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["dirb" "save"] nil))]
      (is (= 2 status))
      (is (str/includes? (str error) "`save` takes 1 argument, but got 0"))))

  (testing "a command without parameters rejects arguments"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["dirb" "list" "--verbose"] nil))]
      (is (= 2 status))
      (is (str/includes? (str error) "`list` takes no arguments, but got 1"))))

  (testing "an optional argument is accepted at either count"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["dirb" "autocomplete" "entries" "extra"] nil))]
      (is (= 2 status))
      (is (str/includes? (str error) "`autocomplete` takes 0 to 1 argument")))))

(deftest extension-commands
  (testing "a manifest adds its namespace through a relative source path"
    (is (= ["dirb" "fileb" "hello"]
           (mapv first (cli/command-groups extension-manifest)))))

  (testing "extension commands participate in autocomplete and dispatch"
    (let [completion-output
          (with-out-str
            (is (zero? (cli/dispatch! ["autocomplete" "hello"]
                                      extension-manifest))))
          command-output
          (with-out-str
            (is (zero? (cli/dispatch! ["hello" "greet" "Colleague"]
                                      extension-manifest))))]
      (is (= "greet\n" completion-output))
      (is (= "Hello, Colleague\n" command-output)))))
