(ns skriptit.cli-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [skriptit.cli :as cli]))

(deftest help-and-errors
  (testing "help describes the default groups"
    (let [output (with-out-str (is (zero? (cli/dispatch! ["help"]))))]
      (is (str/includes? output "dirb"))
      (is (str/includes? output "fileb"))))

  (testing "an unknown group returns a usage error"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["private"]))]
      (is (= 2 status))
      (is (str/includes? (str error) "Unknown command group")))))

(defn- dispatch-error
  "Dispatch args and return [status stderr]."
  [args]
  (let [error (java.io.StringWriter.)
        status (binding [*err* error]
                 (cli/dispatch! args))]
    [status (str error)]))

(deftest typos-get-a-suggestion
  (testing "a misspelled group suggests the closest one"
    (let [[status error] (dispatch-error ["dirv"])]
      (is (= 2 status))
      (is (str/includes? error "Did you mean `dirb`?"))))

  (testing "a group name run together with its command suggests the group"
    (is (str/includes? (second (dispatch-error ["fileb-list"]))
                       "Did you mean `fileb`?")))

  (testing "a misspelled command suggests the closest command in its group"
    (is (str/includes? (second (dispatch-error ["dirb" "lsit"]))
                       "Did you mean `list`?")))

  (testing "nothing is suggested for a word unlike any name"
    (is (not (str/includes? (second (dispatch-error ["private"]))
                            "Did you mean")))))

(deftest argument-counts-are-checked
  (testing "a missing argument is reported before the command runs"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["dirb" "save"]))]
      (is (= 2 status))
      (is (str/includes? (str error) "`save` takes 1 argument, but got 0"))))

  (testing "a command without parameters rejects arguments"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["dirb" "list" "--verbose"]))]
      (is (= 2 status))
      (is (str/includes? (str error) "`list` takes no arguments, but got 1"))))

  (testing "an optional argument is accepted at either count"
    (let [error (java.io.StringWriter.)
          status (binding [*err* error]
                   (cli/dispatch! ["dirb" "autocomplete" "entries" "extra"]))]
      (is (= 2 status))
      (is (str/includes? (str error) "`autocomplete` takes 0 to 1 argument")))))

(deftest custom-command-groups
  (let [groups (conj cli/default-groups
                     ["hello" 'skriptit.fixtures.hello-commands])]
    (testing "an extra group joins autocomplete and dispatch"
      (is (= "greet\n"
             (with-out-str
               (is (zero? (cli/dispatch! ["autocomplete" "hello"] groups))))))
      (is (= "Hello, Colleague\n"
             (with-out-str
               (is (zero? (cli/dispatch! ["hello" "greet" "Colleague"]
                                         groups)))))))

    (testing "a prefix the dispatcher answers itself is rejected"
      (let [error (java.io.StringWriter.)
            status (binding [*err* error]
                     (cli/dispatch! ["help"]
                                    (conj cli/default-groups
                                          ["help" 'skriptit.fixtures.hello-commands])))]
        (is (= 1 status))
        (is (str/includes? (str error) "reserved"))))

    (testing "a duplicate prefix is rejected instead of shadowing"
      (let [error (java.io.StringWriter.)
            status (binding [*err* error]
                     (cli/dispatch! ["help"] (conj groups ["dirb" 'other.ns])))]
        (is (= 1 status))
        (is (str/includes? (str error) "unique"))))))
