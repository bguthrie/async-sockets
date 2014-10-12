(ns com.gearswithingears.async-sockets-test
  (:import  [java.net Socket InetAddress])
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [com.gearswithingears.async-sockets :refer :all]
            [clj-time.core :as time]))

(def port (int 55555))

(deftest test-server-in-out
  (let [server      (socket-server port)
        client-sock (socket-client port)
        server-sock (async/<!! (:connections server))]

    (try
      (async/>!! (:out client-sock) "Ping")
      (is (= "Ping" (async/<!! (:in server-sock))))

      (async/>!! (:out server-sock) "Pong")
      (is (= "Pong" (async/<!! (:in client-sock))))

      (finally
        (stop-socket-server server)
        (close-socket-client client-sock)))
    ))

(deftest test-server-repeated-messages
  (let [server      (socket-server port)
        client-sock (socket-client port)
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
        (stop-socket-server server)
        (close-socket-client client-sock)))
    ))

(deftest test-echo-server
  (let [server      (socket-server port)
        client-sock (socket-client port)
        server-sock (async/<!! (:connections server))]

    (try
      (async/go-loop []
        (when-let [input (async/<! (:in server-sock))]
          (async/>! (:out server-sock) (str "ECHO: " input))
          (recur)))

      (async/>!! (:out client-sock) "Hello, I'm Guybrush Threepwood")
      (is (= "ECHO: Hello, I'm Guybrush Threepwood") (async/<!! (:in client-sock)))

      (finally
        (stop-socket-server server)
        (close-socket-client client-sock)))
    ))

(defn receive-until-secs-elapsed [limit out-chan socket]
  (let [start-time (time/now)]
    (async/go-loop [n 0]
      (let [secs-elapsed (time/in-seconds (time/interval start-time (time/now)))
            msg (async/<! (:in socket))]
        (if (or (nil? msg) (= secs-elapsed limit))
          (do (close-socket-client socket) (async/>! out-chan n))
          (recur (inc n)))))))

(defn send-indefinitely [socket id]
  (async/go-loop [n 0]
    (when (= 0 (mod n 100000)) (println "Sent" n "messages on socket" id))
    (async/>! (:out socket) (str "message " n))
    (recur (inc n))))

(defn perftest-sockets [socket-count secs-limit]
  (let [server        (socket-server port)
        client-socks  (map socket-client (range socket-count))
        server-socks  (map (fn [_] (async/<!! (:connections server))) (range socket-count))
        limit-minutes (/ 60 secs-limit)
        out-chan      (async/chan socket-count)]

    (try

      (doall
        (map-indexed
          (fn [idx sock] (send-indefinitely sock idx))
          client-socks))

      (doall
        (map (partial receive-until-secs-elapsed secs-limit out-chan) server-socks))

      (loop [n 0]
        (when (< n socket-count)
          (let [msgs-received (async/<!! out-chan)
                msgs-per-minute (* msgs-received limit-minutes)]
            (println (format "Socket %d received %d msgs in %d seconds (%f msgs/minute)" n msgs-received secs-limit (float msgs-per-minute)))
            (recur (inc n)))))

      (finally
        (stop-socket-server server)
        (doall (map close-socket-client client-socks))))

    ))

;(perftest-sockets 4 10)