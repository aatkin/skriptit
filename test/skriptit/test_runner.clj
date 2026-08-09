(ns skriptit.test-runner
  (:require [clojure.test :as test]
            [skriptit.bookmarks-test]
            [skriptit.cli-test]
            [skriptit.config-test]
            [skriptit.db-test]))

(defn run-tests! []
  (let [{:keys [fail error]}
        (test/run-tests 'skriptit.bookmarks-test
                        'skriptit.cli-test
                        'skriptit.config-test
                        'skriptit.db-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
