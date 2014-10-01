(ns async-sockets.core-test
  (:import  [java.net Socket InetAddress])
  (:require [clojure.test :refer :all]
            [async-sockets.core :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async]))


(def port   (int 9999))
(def server (atom (socket-server port)))
(def client (atom nil))

(use-fixtures :each
  (fn [f]
    (reset! server (component/start @server))
    (try (f)
      (finally
        (reset! server (component/stop @server)))))

  (fn [f]
    (reset! client (-> (InetAddress/getLocalHost) (Socket. port) (make-socket)))
    (try (f)
      (finally
        (close! @client)
        (reset! client nil))))
  )

(deftest a-test
  (let [conn-chan (:connections @server)
        [in out] (async/<!! conn-chan)]
    (write-ln @client "foo")
    (is (= "foo" (async/<!! in)))
    ))
