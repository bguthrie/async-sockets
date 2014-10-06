(defproject com.gearswithingears/async-sockets "0.0.1-SNAPSHOT"
  :description "A Clojure library for working with sockets using core.async channels."
  :url "https://github.com/bguthrie/async-sockets"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.stuartsierra/component "0.2.2"]
                 [org.clojure/tools.logging "0.3.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}})