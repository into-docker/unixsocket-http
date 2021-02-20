(ns ^:no-doc unixsocket-http.impl.FixedPathTcpSocket
  "Wrapper around AFUNIXSocket that is bound to a fixed address and will
   ignore the argument passed to 'connect'. This is necessary since an HTTP
   client using this socket will pass its own socket address."
  (:gen-class
    :name            unixsocket_http.impl.FixedPathTcpSocket
    :extends         java.net.Socket
    :init            init
    :state           state
    :constructors    {[java.net.SocketAddress] []}
    :methods         [[connect [] void]])
  (:require [unixsocket-http.impl.delegate :refer [delegate]])
  (:import [java.net Socket SocketAddress]))

;; ## State

(defn- get-socket
  ^Socket [^unixsocket_http.impl.FixedPathTcpSocket this]
  (-> this (.-state) (deref) (:socket)))

(defn- get-address
  ^SocketAddress [^unixsocket_http.impl.FixedPathTcpSocket this]
  (-> this (.-state) (deref) (:address)))

;; ## Constructor

(defn -init
  [^SocketAddress addr]
  [[] (atom {:socket  (Socket.)
             :address addr})])

;; ## Methods

(delegate
  {:class  java.net.Socket
   :via    get-socket
   :except [connect]})

(defn -connect
  ([this]
   (.connect (get-socket this) (get-address this)))
  ([this _]
   (.connect (get-socket this) (get-address this)))
  ([this _ ^Integer timeout]
   (.connect (get-socket this) (get-address this) timeout)))
