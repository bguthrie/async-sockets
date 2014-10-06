# async-sockets

A Clojure library for opening and working with sockets asynchronously.

## Usage

This library permits the creation of socket servers and remote socket clients in idiomatic Clojure. It uses
`clojure.core.async`  channels for receiving and sending socket data but otherwise leans heavily on `java.net.Socket`
and `java.net.ServerSocket` for most of the heavy lifting. At the moment, it uses a line-based idiom for all socket
interactions; remote data is received and sent a line at a time, and flushed on write. Pull requests gratefully
accepted.

Servers are created with `(socket-server <port> [<backlog>] [<bind-addr>])`. The latter two arguments are both optional
and behave as specified by `java.net.SocketServer`. The function returns a record with a channel named `:connections`, 
which yields one `AsyncSocket` per incoming client connection.

Clients are created with `(socket-client <port> [<address>])`; `address` is `localhost` by default. Each socket, either
created explicitly using this function or yielded by the `:connections` fields of an `AsyncSocketServer`, exposes two
channels, an `:in` channel for receiving messages and an `:out` channel for sending messages. The raw `java.net.Socket` 
object is also available as `:socket`, should you need it.

This library uses the [Component](https://github.com/stuartsierra/component) framework for managing lifecycle. Both
socket servers and socket clients must be explicitly started using `(component/start <server-or-client>)`.

To connect and send a message to a remote server `example.com`:

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async])
  (:import  [java.net InetAddress]))

(let [socket (component/start (socket-client "example.com" 12345))]
  (async/>! (:out socket) "Hello, World"))
```

To start an asynchronous socket server, which in this case echoes every input received:

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async]))
   
(defn echo-everything [socket]
  (async/go-loop []
    (when-let [line (async/<! (:in socket))]
      (async/>! (:out socket) (str "ECHO: " line))
      (recur))))
   
(let [server (component/start (socket-server 12345))]
  (async/go-loop []
    (when-let [connection (async/<! (:connections server))] 
      (echo-everything connection)
      (recur)))))
```

When the underlying socket closes, each channel will close automatically, both for outgoing (client) and incoming
(server) sockets.

## License

Copyright © 2014 Brian Guthrie (btguthrie@gmail.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
