(ns async-sockets.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import  [java.net Socket ServerSocket SocketException InetAddress InetSocketAddress]
            [java.io BufferedReader BufferedWriter]))

(set! *warn-on-reflection* true)

(def system-newline ;; This is in clojure.core but marked private.
  (System/getProperty "line.separator"))

(defrecord AsyncSocket [^Socket socket ^InetSocketAddress address]
  component/Lifecycle

  (start [this]
    (when (or (.isClosed socket) (not (.isBound socket)))
      (.connect socket address))

    (let [^BufferedReader in (io/reader socket)
          ^BufferedWriter out (io/writer socket)
          in-ch (async/chan)
          out-ch (async/chan)
          this (assoc this :in in-ch :out out-ch)]

      (async/go-loop []
        (let [line (and (not (.isClosed socket)) (.readLine in))]
          (if-not line
            (component/stop this)
            (do
              (async/>! in-ch line)
              (recur)))))

      (async/go-loop []
        (let [line (and (not (.isClosed socket)) (async/<! out-ch))]
          (if-not line
            (component/stop this)
            (do
              (.write out (str line system-newline))
              (.flush out)
              (recur)))))

      this
      ))

  (stop [{:keys [in out] :as this}]
    (when-not (.isInputShutdown socket)  (.shutdownInput socket))
    (when-not (.isOutputShutdown socket) (.shutdownOutput socket))
    (when-not (.isClosed socket)         (.close socket))
    (async/close! in)
    (async/close! out)
    (dissoc this :socket :in :out)))

(defn- async-socket-server-chan [^ServerSocket server]
  "Given a java.net.ServerSocket, returns a channel which yields a pair (in, out) of async channels for each new socket
   connection on the given port."
  (let [ch (async/chan)]
    (async/go
      (while (and (not (.isClosed server)) (.isBound server))
        (try
          (async/>! ch (-> server
                           (.accept)
                           (->AsyncSocket (.getLocalSocketAddress server))
                           (component/start)))
          (log/info "New connection opened on port" (.getLocalPort server))
          (catch SocketException e
            (log/debug "Received socket exception" e)
            (async/close! ch))))
      (async/close! ch))
    ch))

(defn server-running? [{:keys [^ServerSocket server]}]
  (and server (not (.isClosed server))))

(defrecord AsyncSocketServer [port]
  component/Lifecycle
  (start [this]
    (when-not (server-running? this)
      (let [server (ServerSocket. port)]
        (log/info "Starting async socket server on port" port)
        (assoc this :server server :connections (async-socket-server-chan server)))))

  (stop [{:keys [^ServerSocket server connections] :as this}]
    (when (server-running? this)
      (log/info "Stopping async socket server on port" port)
      (async/close! connections)
      (.close server)
      (dissoc this :server :connections))))

(defn socket-server [port] (->AsyncSocketServer port))

(defn socket-client
  ([port]
   (socket-client port (InetAddress/getLocalHost)))
  ([^Integer port ^InetAddress address]
   (->AsyncSocket (Socket.) (InetSocketAddress. address port))))
