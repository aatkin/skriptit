(ns skriptit.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [skriptit.config :as config]))

(deftest environment-interpolation
  (testing "a set variable is expanded in place"
    (is (= (str (System/getenv "HOME") "/notes.md")
           (config/interpolate-env "$HOME/notes.md"))))

  (testing "a path without variables is returned unchanged"
    (is (= "/tmp/notes.md" (config/interpolate-env "/tmp/notes.md"))))

  (testing "an unset variable fails loudly instead of expanding to nothing"
    (is (thrown-with-msg? Exception #"not set"
                          (config/interpolate-env "$SKRIPTIT_UNSET_IN_TESTS/x"))))

  (testing "a non-string value is rejected"
    ;; Passing the wrong type is the point of this assertion.
    #_{:clj-kondo/ignore [:type-mismatch]}
    (is (thrown-with-msg? Exception #"path string"
                          (config/interpolate-env ["/tmp"])))))
