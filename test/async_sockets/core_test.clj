(ns async-sockets.core-test
  (:import  [java.net Socket InetAddress])
  (:require [clojure.test :refer :all]
            [async-sockets.core :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async]))

(def port   (int 55555))
(def server (atom (socket-server port)))
(def client (atom nil))

(use-fixtures :each
  (fn [f]
    (reset! server (component/start @server))
    (try (f)
      (finally
        (reset! server (component/stop @server)))))

  (fn [f]
    (reset! client (-> (InetAddress/getLocalHost) (Socket. port) (wrap-socket)))
    (try (f)
      (finally
        (socket-close! @client)
        (reset! client nil))))
  )

(deftest test-server-in-out
  (let [conn-chan (:connections @server)
        {:keys [in out]} (async/<!! conn-chan)]

    (socket-write @client "foo")
    (is (= "foo" (async/<!! in)))

    (async/>!! out "bar")
    (is (= "bar" (socket-read @client)))
    ))
