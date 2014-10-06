(ns com.gearswithingears.async-sockets
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import  [java.net Socket ServerSocket SocketException InetAddress InetSocketAddress]
            [java.io BufferedReader BufferedWriter]))

(def system-newline ;; This is in clojure.core but marked private.
  (System/getProperty "line.separator"))

(defn- socket-open? [^Socket socket]
  (not (or (.isClosed socket) (.isInputShutdown socket) (.isOutputShutdown socket))))

(defn- socket-read-line-or-nil [^Socket socket ^BufferedReader in]
  (when (socket-open? socket)
    (try (.readLine in)
      (catch SocketException e nil))))

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
        (let [line (socket-read-line-or-nil socket in)]
          (if-not line
            (component/stop this)
            (do
              (async/>! in-ch line)
              (recur)))))

      (async/go-loop []
        (let [line (and (socket-open? socket) (async/<! out-ch))]
          (if-not line
            (component/stop this)
            (do
              (.write out (str line system-newline))
              (.flush out)
              (recur)))))

      (log/info "New async socket opened on address" address)
      this
      ))

  (stop [{:keys [in out] :as this}]
    (log/info "Closing async socket on address" address)
    (when-not (.isInputShutdown socket)  (.shutdownInput socket))
    (when-not (.isOutputShutdown socket) (.shutdownOutput socket))
    (when-not (.isClosed socket)         (.close socket))
    (async/close! in)
    (async/close! out)
    (assoc this :socket nil :in nil :out nil)))

(defn- async-socket-server-chan [^ServerSocket server]
  "Given a java.net.ServerSocket, returns a channel which yields a pair (in, out) of async channels for each new socket
   connection on the given port."
  (let [ch (async/chan)]
    (async/go
      (while (and (not (.isClosed server)) (.isBound server))
        (try
          (async/>! ch (-> (.accept server)
                           (->AsyncSocket (.getLocalSocketAddress server))
                           (component/start)))
          (catch SocketException e
            (log/debug "Received socket exception" e)
            (async/close! ch))))
      (async/close! ch))
    ch))

(defn server-running? [{:keys [^ServerSocket server]}]
  (and server (not (.isClosed server))))

(defrecord AsyncSocketServer [^Integer port ^Integer backlog ^InetAddress bind-addr]
  component/Lifecycle
  (start [this]
    (when-not (server-running? this)
      (let [server (ServerSocket. port backlog bind-addr)]
        (log/info "Starting async socket server on port" port)
        (assoc this :server server :connections (async-socket-server-chan server)))))

  (stop [{:keys [^ServerSocket server connections] :as this}]
    (when (server-running? this)
      (log/info "Stopping async socket server on port" port)
      (async/close! connections)
      (.close server)
      (assoc this :server nil :connections nil))))

(defn- ^InetAddress localhost []
  (InetAddress/getLocalHost))

(defn- host-name [^InetAddress address]
  (.getHostName address))

(defn- ^InetAddress inet-address [host]
  (if (instance? InetAddress host) host (InetAddress/getByName host)))

(def ^Integer default-server-backlog 50) ;; derived from SocketServer.java

(defn socket-server
  "Given a port and optional backlog (the maximum queue length of incoming connection indications, 50 by default)
   and an optional bind address (localhost by default), returns an AsyncSocketServer which must be explicitly
   started and stopped by the consumer."
  ([port]
   (socket-server port default-server-backlog nil))
  ([port backlog]
   (socket-server port backlog nil))
  ([port backlog bind-addr]
   (->AsyncSocketServer (int port) (int backlog) (when bind-addr (inet-address bind-addr)))))

(defn socket-client
  "Given a port and an optional address (localhost by default), returns an AsyncSocket which must be explicitly
   started and stopped by the consumer."
  ([port]
    (socket-client (int port) (host-name (localhost))))
  ([^Integer port ^String address]
   (->AsyncSocket (Socket.) (InetSocketAddress. address port))))
