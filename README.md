# async-sockets

A Clojure library for opening and working with sockets asynchronously.

## Releases

`async-sockets` is published to [clojars.org](https://clojars.org). The latest stable release is `0.0.1-SNAPSHOT`.

[Leiningen](http://leiningen.org) dependency information:

```
[com.gearswithingears/async-sockets "0.0.1-SNAPSHOT"]
```

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

To connect and send a message to a remote server `example.com`:

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [clojure.core.async :as async]))

(let [socket (socket-client "example.com" 12345)]
  (async/>!! (:out socket) "Hello, World")
  (close-socket-client socket))
```

To start an asynchronous socket server, which in this case echoes every input received:

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [clojure.core.async :as async]))
   
(defn echo-everything [socket]
  (async/go-loop []
    (when-let [line (async/<! (:in socket))]
      (async/>! (:out socket) (str "ECHO: " line))
      (recur))))
   
(let [server (socket-server 12345)]
  (async/go-loop []
    (when-let [connection (async/<! (:connections server))] 
      (echo-everything connection)
      (recur)))))
```

When the underlying socket closes, each channel will close automatically, both for outgoing (client) and incoming
(server) sockets.

## License

Copyright © 2014 Brian Guthrie (btguthrie@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.