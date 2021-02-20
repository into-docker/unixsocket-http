(ns ^:no-doc unixsocket-http.impl.ResponseSocket
  "Wrapper around an existing socket that will make sure that the originating
   OkHttp Response is closed when the socket is closed. Without this, we
   run into connection leaks."
  (:gen-class
    :name            unixsocket_http.impl.ResponseSocket
    :extends         java.net.Socket
    :init            init
    :state           state
    :constructors    {[java.net.Socket okhttp3.Response] []})
  (:require [unixsocket-http.impl.delegate :refer [delegate]])
  (:import [okhttp3 Response]
           [java.net Socket]))

;; ## State

(defn- get-socket
  ^Socket [^unixsocket_http.impl.ResponseSocket this]
  (-> this (.-state) (:socket)))

(defn- get-response
  ^Response [^unixsocket_http.impl.ResponseSocket this]
  (-> this (.-state)  (:response)))

;; ## Constructor

(defn -init
  [^Socket socket ^Response response]
  [[] {:socket   socket
       :response response}])

;; ## Methods

(delegate
  {:class  java.net.Socket
   :via    get-socket
   :except [connect close]})

(defn -connect
  ([this addr]
   (.connect (get-socket this) addr))
  ([this addr ^Integer timeout]
   (.connect (get-socket this) addr timeout)))

(defn -close
  [this]
  (.close (get-response this))
  (.close (get-socket this)))
