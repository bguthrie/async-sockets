(ns async-sockets.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import  [java.net Socket ServerSocket SocketException InetAddress]
            [java.io BufferedReader BufferedWriter]))

(set! *warn-on-reflection* true)

(def system-newline
  (System/getProperty "line.separator"))

(defprotocol IClojureSocket
  (socket-read   [this])
  (socket-write  [this line])
  (socket-open?  [this])
  (socket-close! [this]))

(defn wrap-socket [^Socket socket]
  "Given a java.net.Socket, returns a reified IClojureSocket which handles buffered reading and writing. By default,
   treats all input and output as distinct lines, and flushes on each write."
  (let [^BufferedReader in (io/reader socket) ^BufferedWriter out (io/writer socket)]
    (reify IClojureSocket
      (socket-read [_]
        (.readLine in))
      (socket-write [_ line]
        (.write out (str line system-newline)) (.flush out))
      (socket-open? [_]
        (not (.isClosed socket)))
      (socket-close! [this]
        (when (socket-open? this) (doto socket (.shutdownInput) (.shutdownOutput) (.close)))))))

(defn- close-all [sockable in-ch out-ch]
  (socket-close! sockable)
  (async/close! in-ch)
  (async/close! out-ch))

(defn- async-socket-channels [socket]
  "Accepts a ClojureSocket and returns a pair (in, out) of async channels that read from the socket's input and write
   to its output respectively. If nil is received from the socket, nil is received from the in-chan, or the socket
   itself closes, closes both channels and the socket as necessary."
  (let [in-ch (async/chan) out-ch (async/chan)]
    (async/go-loop []
      (if-let [line (and (socket-open? socket) (socket-read socket))]
        (async/>! in-ch line)
        (close-all socket in-ch out-ch)))
    (async/go-loop []
      (if-let [line (and (socket-open? socket) (async/<! out-ch))]
        (socket-write socket line)
        (close-all socket in-ch out-ch)))
    {:in in-ch :out out-ch :socket socket}))

(defn- async-socket-server-chan [^ServerSocket server]
  "Given a java.net.ServerSocket, returns a channel which yields a pair (in, out) of async channels for each new socket
   connection on the given port."
  (let [ch (async/chan)]
    (async/go
      (while (and (not (.isClosed server)) (.isBound server))
        (try
          (async/>! ch (-> server
                           (.accept)
                           (wrap-socket)
                           (async-socket-channels)))
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

(defrecord AsyncSocketClient [^Integer port ^InetAddress address]
  component/Lifecycle
  (start [this]
    (let [raw-socket (Socket. address port)
          socket (wrap-socket raw-socket)
          [in out] (async-socket-channels socket)]
      (log/info "Opened socket connection opened on port" port)
      (assoc this :socket socket :in in :out out)))

  (stop [{:keys [socket in out] :as this}]
    (when (socket-open? socket)
      (close-all socket in out)
      (log/info "Closing socket connection opened on port" port)
      (dissoc this :socket :in :out))))

(defn socket-client
  ([port]
   (socket-client (InetAddress/getLocalHost) port))
  ([port address]
   (map->AsyncSocketClient {:port port :address address})))
