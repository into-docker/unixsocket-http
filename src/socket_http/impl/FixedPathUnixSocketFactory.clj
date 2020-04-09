(ns socket-http.impl.FixedPathUnixSocketFactory
  "SocketFactory that produces `FixedPathUnixSocket` objects bound to a
   given path."
  (:gen-class
    :name            socket_http.impl.FixedPathUnixSocketFactory
    :extends         javax.net.SocketFactory
    :init            init
    :state           socketFn
    :constructors    {[String] []})
  (:require [socket-http.impl.delegate :refer [delegate]]
            [clojure.java.io :as io])
  (:import [socket_http.impl FixedPathUnixSocket]
           [java.net InetAddress]
           [javax.net SocketFactory]))

;; ## Constructor

(defn -init
  [^String path]
  [[] #(FixedPathUnixSocket. path)])

;; ## Methods

(defn- create-socket!
  ^FixedPathUnixSocket [^socket_http.impl.FixedPathUnixSocketFactory this]
  ((.-socketFn this)))

(defn -createSocket
  ([this]
   (doto (create-socket! this)
     (.connect)))
  ([this ^InetAddress _ ^Integer _]
   (doto (create-socket! this)
     (.connect)))
  ([this ^InetAddress _ ^Integer _ ^InetAddress _ ^Integer _]
   (doto (create-socket! this)
     (.connect))))
