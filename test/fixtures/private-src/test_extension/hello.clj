(ns test-extension.hello)

(defn greet!
  "Print a greeting from a private extension namespace."
  {:skriptit/cmd "greet"
   :skriptit/args "<name>"}
  [name]
  (println "Hello," name))
