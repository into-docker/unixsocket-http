(ns ^:no-doc unixsocket-http.impl.FixedPathUnixSocket
  "Wrapper around AFUNIXSocket that is bound to a fixed address and will
   ignore the argument passed to 'connect'. This is necessary since an HTTP
   client using this socket will pass its own socket address."
  (:gen-class
    :name            unixsocket_http.impl.FixedPathUnixSocket
    :extends         java.net.Socket
    :init            init
    :state           state
    :constructors    {[String] []}
    :methods         [[connect [] void]])
  (:require [unixsocket-http.impl.delegate :refer [delegate]]
            [clojure.java.io :as io])
  (:import [org.newsclub.net.unix AFUNIXSocketAddress AFUNIXSocket]))

;; ## State

(defn- get-socket
  ^AFUNIXSocket [^unixsocket_http.impl.FixedPathUnixSocket this]
  (-> this (.-state) (deref) (:socket)))

(defn- get-address
  ^AFUNIXSocketAddress [^unixsocket_http.impl.FixedPathUnixSocket this]
  (-> this (.-state) (deref) (:address)))

;; ## Constructor

(defn -init
  [^String path]
  [[] (atom {:socket  (AFUNIXSocket/newInstance)
             :address (AFUNIXSocketAddress. (io/file path))})])

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
