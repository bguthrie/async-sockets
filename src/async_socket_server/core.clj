(ns async-socket-server.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import  [java.net Socket ServerSocket SocketException]
            [java.io BufferedReader BufferedWriter]))

(set! *warn-on-reflection* true)

(defprotocol IClojureSocket
  (read-ln [this])
  (write-ln [this line])
  (open? [this])
  (close! [this]))

(defn make-socket [^Socket socket]
  (let [^BufferedReader in (io/reader socket) ^BufferedWriter out (io/writer socket)]
    (reify IClojureSocket
      (read-ln [_]
        (.readLine in))
      (write-ln [_ line]
        (.write out (str line "\r\n")) (.flush out))
      (open? [_]
        (not (.isClosed socket)))
      (close! [this]
        (when (open? this) (doto socket (.shutdownInput) (.shutdownOutput) (.close)))))))

(defn- close-all [sockable in-ch out-ch]
  (close! sockable) (async/close! in-ch) (async/close! out-ch))

(defn async-socket-chan-pair [socket]
  "Accepts a socket and returns a pair (in, out) of async channels that read from the socket's input and write
   to its output respectively. Closes the socket and its in-chan when either the socket or the out-chan receives nil."
  (let [in-ch (async/chan) out-ch (async/chan)]
    (async/go-loop []
      (if-let [line (and (open? socket) (read-ln socket))]
        (async/>! in-ch line)
        (close-all socket in-ch out-ch)))
    (async/go-loop []
      (if-let [line (and (open? socket) (async/<! out-ch))]
        (write-ln socket line)
        (close-all socket in-ch out-ch)))
    [in-ch out-ch]))

(defn async-socket-server-chan [^ServerSocket server]
  "Given a java.net.ServerSocket, returns a channel which yields a pair (in, out) of async channels for each new socket
   connection on the given port."
  (let [ch (async/chan)]
    (async/go
      (while (and (not (.isClosed server)) (.isBound server))
        (try
          (async/>! ch (-> server
                           (.accept)
                           (make-socket)
                           (async-socket-chan-pair)))
          (log/info "New connection opened on port" (.getLocalPort server))
          (catch SocketException e
            (log/debug "Received socket exception" e)
            (async/close! ch))))
      (async/close! ch))
    ch))

(defn running? [{:keys [^ServerSocket server]}]
  (and server (not (.isClosed server))))

(defrecord AsyncSocketServer [port]
  component/Lifecycle
  (start [{:keys [^ServerSocket server] :as this}]
    (when-not (running? this)
      (let [server (ServerSocket. port)]
        (log/info "Starting async socket server on port" port)
        (assoc this :server server :connections (async-socket-server-chan server)))))

  (stop [{:keys [^ServerSocket server connections] :as this}]
    (when (running? this)
      (log/info "Stopping async socket server on port" port)
      (async/close! connections)
      (.close server)
      (dissoc this :server :connections))))

(defn socket-server [port] (->AsyncSocketServer port))