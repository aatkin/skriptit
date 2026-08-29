(ns skriptit.util
  (:require [clojure.string :as str]))

(defn blank?
  "True when x is not a string, or is a string that is empty or all whitespace."
  [x]
  (or (not (string? x))
      (str/blank? x)))

(defn not-blank
  "Return x unless it is blank, in which case nil."
  [x]
  (when-not (blank? x)
    x))
