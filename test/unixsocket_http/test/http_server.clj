(ns unixsocket-http.test.http-server
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [org.newsclub.net.unix
            AFUNIXServerSocket
            AFUNIXSocketAddress]
           [okhttp3.mockwebserver.internal.duplex
            DuplexResponseBody]
           [okhttp3.mockwebserver
            Dispatcher
            MockResponse
            MockWebServer
            RecordedRequest]
           [okhttp3.tls
            HandshakeCertificates$Builder
            HeldCertificate$Builder
            HandshakeCertificates
            HeldCertificate]
           [java.io File]
           [javax.net
            ServerSocketFactory]))

;; ## Mock Server

(defn- mock-response
  [status ^String body]
  (.. (MockResponse.)
      (setResponseCode status)
      (setHeader "Content-Type" "text/plain")
      (setBody body)))

(defn- create-mock-server!
  ^MockWebServer [& [builder-fn]]
  ;; Don't log warnings that, for some reason, occur when using
  ;; the UNIX socket.
  (doto (java.util.logging.LogManager/getLogManager)
    (.reset))

  ;; Mock Server
  (doto (MockWebServer.)
    (cond-> builder-fn builder-fn)
    (.setDispatcher
      (proxy [Dispatcher] []
        (dispatch [^RecordedRequest request]
          (with-open [body (.getBody request)]
            (case (string/replace (.getPath request) #"\?.*$" "")
              "/ok"   (mock-response 200 "OK")
              "/head" (mock-response 200 "")
              "/echo" (mock-response 200 (.readUtf8 body))
              "/fail" (mock-response 500 "FAIL")
              (mock-response 404 "NOT_FOUND"))))))
    (.start)))

(defn- as-url
  [^MockWebServer server]
  (-> (str (.url server ""))
      (string/replace #"/+$" "")))

;; ## Servers

(defn create-unix-socket-server
  []
  (let [socket-file (doto (File/createTempFile "http" ".sock")
                      (.delete))
        address (AFUNIXSocketAddress. socket-file)
        factory (proxy [ServerSocketFactory] []
                  (createServerSocket []
                    (AFUNIXServerSocket/forceBindOn address)))
        server (create-mock-server!
                 (fn [^MockWebServer server]
                   (.setServerSocketFactory server factory)))]
    {:url (str "unix://" (.getCanonicalPath socket-file))
     :stop #(.shutdown server)}))

;; ### TCP

(defn create-tcp-socket-server
  []
  (let [server (create-mock-server!)]
    {:url  (-> (as-url server)
               (string/replace #"^http:" "tcp:"))
     :stop #(.shutdown server)}))

;; ### HTTP

(defn create-http-socket-server
  []
  (let [server (create-mock-server!)]
    {:url  (as-url server)
     :stop #(.shutdown server)}))

;; ### HTTPS

(defn- create-certificate
  ^HeldCertificate [^String hostname]
  (-> (HeldCertificate$Builder.)
      (.addSubjectAlternativeName hostname)
      (.build)))

(defn- create-server-builder-fn
  [^HeldCertificate certificate]
  (fn [^MockWebServer server]
    (let [socket-factory (.. (HandshakeCertificates$Builder.)
                             (heldCertificate
                               certificate
                               (into-array java.security.cert.X509Certificate []))
                             (build)
                             (sslSocketFactory))]
      (.useHttps server socket-factory false))))

(defn- create-client-builder-fn
  [^HeldCertificate certificate]
  (let [certs (-> (HandshakeCertificates$Builder.)
                  (.addTrustedCertificate (.certificate certificate))
                  (.build))]
    (fn [^okhttp3.OkHttpClient$Builder builder]
      (.sslSocketFactory builder
                         (.sslSocketFactory certs)
                         (.trustManager certs)))))

(defn create-https-socket-server
  []
  (let [certificate (create-certificate "localhost")
        builder-fn  (create-server-builder-fn certificate)
        server      (create-mock-server! builder-fn)]
    {:url  (as-url server)
     :opts {:builder-fn (create-client-builder-fn certificate)}
     :stop #(.shutdown server)}))
