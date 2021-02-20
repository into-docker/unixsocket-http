(ns ^:no-doc unixsocket-http.impl.FixedPathTcpSocketFactory
  "SocketFactory that produces `FixedPathTcpSocket` objects bound to a fixed path."
  (:gen-class
    :name            unixsocket_http.impl.FixedPathTcpSocketFactory
    :extends         javax.net.SocketFactory
    :init            init
    :state           state
    :constructors    {[String Integer] []})
  (:import [unixsocket_http.impl FixedPathTcpSocket]
           [java.net InetAddress]
           [java.net InetSocketAddress SocketAddress]))

;; ## Constructor

(defn -init
  [^String host ^Integer port]
  [[] (InetSocketAddress. host port)])

;; ## Methods

(defn- create-socket!
  ^FixedPathTcpSocket [^unixsocket_http.impl.FixedPathTcpSocketFactory this]
  (FixedPathTcpSocket. ^SocketAddress (.-state this)))

(defn -createSocket
  ([this]
   (create-socket! this))
  ([this ^InetAddress _ ^Integer _]
   (doto (create-socket! this)
     (.connect)))
  ([this ^InetAddress _ ^Integer _ ^InetAddress _ ^Integer _]
   (doto (create-socket! this)
     (.connect))))

(defn -toString
  [^unixsocket_http.impl.FixedPathTcpSocketFactory this]
  (str (.-state this)))
