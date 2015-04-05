(defproject ceres-geschichte "0.1.0-SNAPSHOT"

  :description "Collecting twitter data with geschichte"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [gezwitscher "0.1.1-SNAPSHOT"]

                 [net.polyc0l0r/konserve "0.2.3"]
                 [net.polyc0l0r/geschichte "0.1.0-SNAPSHOT"]

                 [clj-time "0.9.0"]
                 [com.novemberain/monger "2.1.0"]

                 [aprint "0.1.3"]
                 [com.taoensso/nippy "2.8.0"]
                 [com.taoensso/timbre "3.4.0" :exclusions [com.taoensso/encore]]]

  :main ceres-geschichte.core

  :min-lein-version "2.0.0"

  :uberjar-name "ceres-geschichte.jar")
