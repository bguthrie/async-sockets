(ns com.gearswithingears.async-sockets-test
  (:import  [java.net Socket InetAddress])
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [com.gearswithingears.async-sockets :refer :all]
            [com.stuartsierra.component :as component]))

(def port (int 55555))

(deftest test-server-in-out
  (let [server      (component/start (socket-server port))
        client-sock (component/start (socket-client port))
        server-sock (async/<!! (:connections server))]

    (try
      (async/>!! (:out client-sock) "Ping")
      (is (= "Ping" (async/<!! (:in server-sock))))

      (async/>!! (:out server-sock) "Pong")
      (is (= "Pong" (async/<!! (:in client-sock))))

      (finally
        (component/stop server)
        (component/stop client-sock)))
    ))

(deftest test-server-repeated-messages
  (let [server      (component/start (socket-server port))
        client-sock (component/start (socket-client port))
        server-sock (async/<!! (:connections server))]

    (try
      (async/go
        (async/>! (:out client-sock) "Ping 1")
        (async/>! (:out client-sock) "Ping 2")
        (async/>! (:out client-sock) "Ping 3"))

      (is (= "Ping 1") (async/<!! (:in server-sock)))
      (is (= "Ping 2") (async/<!! (:in server-sock)))
      (is (= "Ping 3") (async/<!! (:in server-sock)))

      (async/go
        (async/>! (:out server-sock) "Pong 1")
        (async/>! (:out server-sock) "Pong 2")
        (async/>! (:out server-sock) "Pong 3"))

      (is (= "Pong 1") (async/<!! (:in client-sock)))
      (is (= "Pong 2") (async/<!! (:in client-sock)))
      (is (= "Pong 3") (async/<!! (:in client-sock)))

      (finally
        (component/stop server)
        (component/stop client-sock)))
    ))

(deftest test-echo-server
  (let [server      (component/start (socket-server port))
        client-sock (component/start (socket-client port))
        server-sock (async/<!! (:connections server))]

    (try
      (async/go-loop []
        (when-let [input (async/<! (:in server-sock))]
          (async/>! (:out server-sock) (str "ECHO: " input))
          (recur)))

      (async/>!! (:out client-sock) "Hello, I'm Guybrush Threepwood")
      (is (= "ECHO: Hello, I'm Guybrush Threepwood") (async/<!! (:in client-sock)))

      (finally
        (component/stop server)
        (component/stop client-sock)))
    ))