(ns unixsocket-http.nanohttpd
  (:require [clojure.java.io :as io])
  (:import [org.newsclub.net.unix
            AFUNIXServerSocket
            AFUNIXSocketAddress]
           [fi.iki.elonen
            NanoHTTPD
            NanoHTTPD$IHTTPSession
            NanoHTTPD$ServerSocketFactory
            NanoHTTPD$Response$Status]
           [javax.net SocketFactory]
           [java.io File]))

(defn- read-body
  [^NanoHTTPD$IHTTPSession session]
  (when-let [len (.. session
                     (getHeaders)
                     (get "content-length"))]
    (let [in (.getInputStream session)
          buf (byte-array (Integer. ^String len))]
      (.read in buf)
      (String. buf))))

(defn- create-nanohttpd
  ^NanoHTTPD []
  (proxy [NanoHTTPD] [0]
    (serve [^NanoHTTPD$IHTTPSession session]
      (let [body (read-body session)
            method (str (.getMethod session))]
        (case (.getUri session)
          "/ok"
          (NanoHTTPD/newFixedLengthResponse "OK")

          "/head"
          (NanoHTTPD/newFixedLengthResponse "")

          "/echo"
          (NanoHTTPD/newFixedLengthResponse
            NanoHTTPD$Response$Status/OK
            "text/plain"
            body)

          "/fail"
          (NanoHTTPD/newFixedLengthResponse
            NanoHTTPD$Response$Status/INTERNAL_ERROR
            "text/plain"
            "FAIL")

          (NanoHTTPD/newFixedLengthResponse
            NanoHTTPD$Response$Status/NOT_FOUND
            "text/plain"
            "NOT_FOUND"))))))

;; ## Servers

(defn create-unix-socket-server
  []
  (let [socket-file (doto (File/createTempFile "http" ".sock")
                      (.delete))
        address (AFUNIXSocketAddress. socket-file)
        server (doto (create-nanohttpd)
                 (.setServerSocketFactory
                   (proxy [NanoHTTPD$ServerSocketFactory] []
                     (create []
                       (AFUNIXServerSocket/forceBindOn address))))
                 (.start NanoHTTPD/SOCKET_READ_TIMEOUT false))]
    {:url (str "unix://" (.getCanonicalPath socket-file))
     :stop #(.stop server)}))

(defn create-tcp-socket-server
  ^NanoHTTPD []
  (let [server (doto (create-nanohttpd)
                 (.start NanoHTTPD/SOCKET_READ_TIMEOUT false))]
    {:url (str "tcp://127.0.0.1:" (.getListeningPort server))
     :stop #(.stop server)}))
