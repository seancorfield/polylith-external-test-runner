(ns org.corfield.util.interface.str
  "Copied from Polylith for the pluralization in messages.

  https://github.com/polyfy/polylith/blob/master/components/util/src/polylith/clj/core/util/interface/str.clj")

(defn count-things [thing cnt]
  (if (<= cnt 1)
    (str cnt " " thing)
    (str cnt " " thing "s")))
