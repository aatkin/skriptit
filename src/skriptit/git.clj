(ns skriptit.git
  (:require [taoensso.timbre :as timbre]
            [skriptit.utils :as utils :refer [+project-root-path+]]))

;; https://book.babashka.org/#_parsing_command_line_arguments
(def cli-options [["-d" "--debug" "Debug mode"]
                  ["-h" "--help" "Show this help"]])

(defn cli [opts]
  (utils/print-help cli-options))

(defn -main [& args]
  (let [opts (utils/parse-cli args cli-options)]
    (utils/with-logging opts :info
      (cli opts))))
