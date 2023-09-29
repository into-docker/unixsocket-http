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
  (:import [org.newsclub.net.unix
            AFUNIXSelectorProvider
            AFUNIXSocketAddress
            AFUNIXSocket]))

;; ## State

#_:clj-kondo/ignore
(defn- get-socket
  ^AFUNIXSocket [^unixsocket_http.impl.FixedPathUnixSocket this]
  (-> this (.-state) (deref) (:socket)))

(defn- get-address
  ^AFUNIXSocketAddress [^unixsocket_http.impl.FixedPathUnixSocket this]
  (-> this (.-state) (deref) (:address)))

(defn- set-socket!
  [^unixsocket_http.impl.FixedPathUnixSocket this socket]
  (swap! (.-state this) assoc :socket socket))

;; ## Constructor

(defn -init
  [^String path]
  [[] (atom {:socket  (AFUNIXSocket/newInstance)
             :address (AFUNIXSocketAddress/of (io/file path))})])

;; ## Methods

(delegate
  {:class  java.net.Socket
   :via    get-socket
   :except [connect]})

(defn- connect-socket!
  ^AFUNIXSocket
  [^AFUNIXSocketAddress address]
  (let [provider (AFUNIXSelectorProvider/provider)
        channel (.openSocketChannel provider)]
    (.connect channel address)
    (.socket channel)))

(defn- connect-and-store-socket!
  [^unixsocket_http.impl.FixedPathUnixSocket this]
  (->> (get-address this)
       (connect-socket!)
       (set-socket! this)))

(defn -connect
  ([this]
   (connect-and-store-socket! this))
  ([this _]
   (connect-and-store-socket! this))
  ([this _ _]
   (connect-and-store-socket! this)))
