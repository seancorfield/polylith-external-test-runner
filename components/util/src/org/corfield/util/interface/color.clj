(ns org.corfield.util.interface.color
  "Copied from Polylith so the subprocess can have basic colorization.

  https://github.com/polyfy/polylith/blob/master/components/util/src/polylith/clj/core/util/interface/color.clj"
  (:require [org.corfield.util.colorizer :as colorizer]))

(def none "none")

(defn clean-colors [message]
  (colorizer/clean-colors message))

(defn blue [color-mode & messages]
  (colorizer/blue color-mode messages))

(defn cyan [color-mode & messages]
  (colorizer/cyan color-mode messages))

(defn green [color-mode & messages]
  (colorizer/green color-mode messages))

(defn grey [color-mode & messages]
  (colorizer/grey color-mode messages))

(defn purple [color-mode & messages]
  (colorizer/purple color-mode messages))

(defn red [color-mode & messages]
  (colorizer/red color-mode messages))

(defn yellow [color-mode & messages]
  (colorizer/yellow color-mode messages))

(defn ok [color-mode & messages]
  (colorizer/ok color-mode messages))

(defn warning [color-mode & messages]
  (colorizer/warning color-mode messages))

(defn error [color-mode & messages]
  (colorizer/error color-mode messages))

(defn entity [type name color-mode]
  (colorizer/entity type name color-mode))

(defn brick [type name color-mode]
  (colorizer/brick type name color-mode))

(defn interface [name color-mode]
  (colorizer/interface name color-mode))

(defn component [name color-mode]
  (colorizer/component name color-mode))

(defn base [name color-mode]
  (colorizer/base name color-mode))

(defn project [name color-mode]
  (colorizer/project name color-mode))

(defn path [path color-mode]
  (colorizer/path path color-mode))

(defn profile [name color-mode]
  (colorizer/profile name color-mode))

(defn library [name color-mode]
  (colorizer/library name color-mode))

(defn namespc
  ([name color-mode]
   (colorizer/namespc name color-mode))
  ([interface namespace color-mode]
   (colorizer/namespc interface namespace color-mode)))
