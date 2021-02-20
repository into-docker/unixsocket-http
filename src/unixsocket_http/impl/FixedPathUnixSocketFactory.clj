(ns ^:no-doc unixsocket-http.impl.FixedPathUnixSocketFactory
  "SocketFactory that produces `FixedPathUnixSocket` objects bound to a
   given path."
  (:gen-class
    :name            unixsocket_http.impl.FixedPathUnixSocketFactory
    :extends         javax.net.SocketFactory
    :init            init
    :state           state
    :constructors    {[String] []})
  (:import [unixsocket_http.impl FixedPathUnixSocket]
           [java.net InetAddress]))

;; ## Constructor

(defn -init
  [^String path]
  [[] path])

;; ## Methods

(defn- create-socket!
  ^FixedPathUnixSocket [^unixsocket_http.impl.FixedPathUnixSocketFactory this]
  (FixedPathUnixSocket. ^String (.-state this)))

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

(defn -toString
  [^unixsocket_http.impl.FixedPathUnixSocketFactory this]
  (str (.-state this)))
