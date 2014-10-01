(ns async-sockets.core-test
  (:import  [java.net Socket InetAddress])
  (:require [clojure.test :refer :all]
            [async-sockets.core :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async]))

(def port   (int 55555))
(def server (atom (socket-server port)))
(def client (atom (socket-client port)))

(use-fixtures :each
  (fn [f]
    (reset! server (component/start @server))
    (try (f)
      (finally
        (reset! server (component/stop @server)))))

  (fn [f]
    (reset! client (component/start @client))
    (try (f)
      (finally
        (reset! client (component/stop @client)))))
  )

(deftest test-server-in-out
  (let [server-conn (async/<!! (:connections @server))
        client-conn @client]

    (async/>!! (:out client-conn) "foo")
    (is (= "foo" (async/<!! (:in server-conn))))

    (async/>!! (:out server-conn) "bar")
    (is (= "bar" (async/<!! (:in client-conn))))
    ))

(deftest test-echo-server-in-out
  (let [server-conn (async/<!! (:connections @server))
        client-conn @client]

    (async/go-loop []
      (when-let [input (async/<! (:in server-conn))]
        (async/>! (:out server-conn) (str "ECHO: " input))
        (recur)))

    (async/>!! (:out client-conn) "Hello, I'm Guybrush Threepwood")
    (is (= "ECHO: Hello, I'm Guybrush Threepwood") (async/<!! (:in client-conn)))
    ))
