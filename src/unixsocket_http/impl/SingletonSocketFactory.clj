(ns ^:no-doc unixsocket-http.impl.SingletonSocketFactory
  "Wrapper around a `SocketFactory` that will cache the created socket."
  (:gen-class
    :name            unixsocket_http.impl.SingletonSocketFactory
    :extends         javax.net.SocketFactory
    :init            init
    :state           socket
    :constructors    {[javax.net.SocketFactory] []}
    :methods         [[getSocket [] java.net.Socket]])
  (:import [java.net InetAddress InetSocketAddress Socket]
           [javax.net SocketFactory]))

;; ## Constructor

(defn -init
  [^SocketFactory factory]
  [[] (delay (.createSocket factory))])

;; ## Methods

(defn- get-socket
  ^Socket [^unixsocket_http.impl.SingletonSocketFactory this]
  @(.-socket this))

(defn -createSocket
  ([this]
   (get-socket this))
  ([this ^InetAddress _ ^Integer _]
   (doto (get-socket this)
     (.connect (InetSocketAddress. 0))))
  ([this ^InetAddress _ ^Integer _ ^InetAddress _ ^Integer _]
   (doto (get-socket this)
     (.connect (InetSocketAddress. 0)))))

(defn -getSocket
  [this]
  (get-socket this))

(defn -toString
  [^unixsocket_http.impl.SingletonSocketFactory this]
  (str (.-socket this)))
