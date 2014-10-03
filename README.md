# async-sockets

A Clojure library for opening and working with sockets asynchronously.

## Usage

This library uses the [Component](https://github.com/stuartsierra/component) framework for managing lifecycle. To
start a server, create it using `(socket-server <port>)`, then start it using `(component/start <server>)`.

Each server yields a record with a channel called `:connections`, which yields one socket (`AsyncSocket`) per incoming
connection. Each socket in turn exposes `:in`, a channel for receiving messages, `:out`, a channel for sending messages,
and `:socket`, the raw `java.net.Socket` object, should you need it for something.

```clojure
(ns user
  (:require [com.stuartsierra.component :as component]
            [async-sockets.core :as socket]
            [clojure.core.async :as async]))
   
(defn echo-everything [{:keys [in out] :as socket}]
  (async/go-loop []
    (when-let [line (async/<! in)]
      (async/>! out (str "ECHO: " line))
      (recur))))
   
(defn echo-server [port]
  (let [server (component/start (socket/socket-server 12345))]
    (async/go-loop []
      (when-let [connection (async/<! (:connections server))] 
        (echo-everything connection)
        (recur)))))
```

To connect to a remote server:

```clojure
(ns user
  (:require [com.stuartsierra.component :as component]
            [async-sockets.core :as socket]
            [clojure.core.async :as async])
  (:import  [java.net InetAddress]))

(let [address (.getByName InetAddress "http://remote.host")
      socket (component/start (socket/socket-client address 12345))]
  (async/>! socket "Hello, World"))
```

When the underlying socket closes, each channel will close automatically, both for outgoing (client) and incoming
(server) sockets.

## License

Copyright © 2014 Brian Guthrie (btguthrie@gmail.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
