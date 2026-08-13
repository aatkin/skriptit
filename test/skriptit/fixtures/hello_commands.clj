(ns skriptit.fixtures.hello-commands
  "Stand-in for a command namespace supplied by a private build.")

(defn greet!
  "Print a greeting."
  {:skriptit/cmd "greet"
   :skriptit/args "<name>"}
  [name]
  (println "Hello," name))
